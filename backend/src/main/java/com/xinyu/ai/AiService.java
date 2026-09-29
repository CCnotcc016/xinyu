// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.config.AiProperties;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.service.CrisisAlertService;
import com.xinyu.service.CrisisSupportService;
import com.xinyu.service.StatsService;
import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 服务：调用大模型（DeepSeek / 通义 / OpenAI 兼容接口）做情绪分析，
 * 并通过 WebClient 流式读取返回，转成 SSE 推送给前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String SYSTEM_PROMPT = """
            你是一个温暖、克制的情绪陪伴者，不是心理医生。你的任务是理解用户、给予陪伴。
            不要做任何医学诊断，不要评判用户，不要给极端建议。
            
            请严格按以下格式输出，除规定内容外不要输出任何多余文字：
            第一行输出一个单行 JSON 对象，包含字段：
              emotion_score（1-10 的整数，数字越大情绪越积极）、
              emotion_label（一个中文情绪标签，例如：开心、平静、焦虑、低落、愤怒、疲惫、孤独）、
              suggestion（一句具体、可执行的小建议）。
            从第二行开始输出共情回复正文：先共情用户的感受，再给予温和的陪伴与支持，2-4 句话，温暖自然。
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final DiaryMapper diaryMapper;
    private final StatsService statsService;
    private final CrisisSupportService crisisSupportService;
    private final CrisisAlertService crisisAlertService;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        if (isKeyMissing()) {
            log.warn("未配置 AI_API_KEY，AI 情绪分析将使用离线 Mock 陪伴回复（不会调用外部大模型）");
        } else {
            log.info("AI 已就绪：provider={} model={}（API Key 已加载，长度 {}）",
                    aiProperties.getProvider(), aiProperties.getModel(),
                    aiProperties.getApiKey().trim().length());
        }
        // xinyu.ai.timeout-seconds 同时作用于建连和两次数据读取之间：
        // 大模型卡住时能及时中断，避免 SSE 连接与 reactor 线程长时间挂起。
        Duration timeout = Duration.ofSeconds(aiProperties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        this.webClient = WebClient.builder()
                .baseUrl(aiProperties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.getApiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    /**
     * 对日记进行 AI 分析，并以 SSE 流式返回共情回复。
     * 该方法只负责建立订阅，实际推送在 reactor 线程中异步完成。
     */
    public void streamAnalyze(Diary diary, SseEmitter emitter) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        // 客户端断开或超时后立刻终止本轮流式推送：否则会继续消耗大模型 token，并逐段刷错误日志
        emitter.onCompletion(() -> stopStream(stopped, subscription));
        emitter.onTimeout(() -> stopStream(stopped, subscription));
        emitter.onError(e -> stopStream(stopped, subscription));

        // 危机关键词直接干预，不调用 AI
        if (crisisSupportService.containsCrisis(diary.getContent())) {
            diary.setAiStatus("CRISIS");
            diary.setAiReply(CrisisSupportService.CRISIS_REPLY);
            diaryMapper.updateById(diary);
            // 记一条预警，管理员端能看到并尝试联系本人
            crisisAlertService.record(diary.getUserId(), CrisisAlert.TARGET_DIARY,
                    diary.getId(), diary.getContent());
            send(emitter, "start", Map.of());
            send(emitter, "delta", Map.of("text", CrisisSupportService.CRISIS_REPLY));
            send(emitter, "crisis", Map.of("message", CrisisSupportService.CRISIS_REPLY));
            emitter.complete();
            return;
        }

        // 未配置真实 API Key 时，使用离线 Mock 陪伴回复，保证演示可用
        if (isKeyMissing()) {
            mockAnalyze(diary, emitter, stopped);
            return;
        }

        send(emitter, "start", Map.of());

        Map<String, Object> body = Map.of(
                "model", aiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", diary.getContent())),
                "stream", true,
                "temperature", aiProperties.getTemperature(),
                "max_tokens", aiProperties.getMaxTokens());

        StringBuilder buffer = new StringBuilder();
        StringBuilder replyText = new StringBuilder();
        AtomicBoolean metaDone = new AtomicBoolean(false);
        AtomicReference<JsonNode> metaRef = new AtomicReference<>();

        Flux<ServerSentEvent<String>> flux = webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });

        subscription.set(flux.subscribe(
                sse -> {
                    String data = sse.data();
                    if (data == null || "[DONE]".equals(data.trim())) {
                        return;
                    }
                    String delta = extractDelta(data);
                    if (delta == null) {
                        return;
                    }
                    buffer.append(delta);
                    if (!metaDone.get()) {
                        int idx = buffer.indexOf("\n");
                        if (idx >= 0) {
                            String metaJson = buffer.substring(0, idx);
                            String rest = buffer.substring(idx + 1);
                            metaRef.set(parseMeta(metaJson));
                            metaDone.set(true);
                            if (!rest.isEmpty()) {
                                replyText.append(rest);
                                send(emitter, "delta", Map.of("text", rest), stopped);
                            }
                        }
                    } else {
                        replyText.append(delta);
                        send(emitter, "delta", Map.of("text", delta), stopped);
                    }
                },
                error -> {
                    log.error("AI 调用失败", error);
                    diary.setAiStatus("FAILED");
                    diaryMapper.updateById(diary);
                    send(emitter, "error", Map.of("message", "AI 服务暂时不可用，请稍后再试"), stopped);
                    emitter.complete();
                },
                () -> finish(diary, emitter, buffer.toString(), replyText.toString(), metaDone.get(), metaRef.get(),
                        stopped)));
    }

    private static void stopStream(AtomicBoolean stopped, AtomicReference<Disposable> subscription) {
        stopped.set(true);
        Disposable disposable = subscription.get();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void finish(Diary diary, SseEmitter emitter, String fullText, String streamedReply,
                        boolean metaDone, JsonNode meta, AtomicBoolean stopped) {
        String reply = streamedReply;
        if (!metaDone) {
            // 整个流都没有换行：把全文当作回复，并尝试从中提取元数据
            int idx = fullText.indexOf("\n");
            if (idx >= 0) {
                meta = parseMeta(fullText.substring(0, idx));
                reply = fullText.substring(idx + 1);
            } else {
                meta = extractMetaFromText(fullText);
                reply = fullText;
            }
        }

        int score = clampScore(meta == null ? 5 : meta.path("emotion_score").asInt(5));
        String label = meta == null || meta.path("emotion_label").isMissingNode()
                ? "平静" : meta.path("emotion_label").asText("平静");
        String suggestion = meta == null ? "" : meta.path("suggestion").asText("");

        String finalReply = (reply == null || reply.isBlank()) ? "谢谢你的分享，我会一直在这里陪着你。" : reply.trim();
        diary.setEmotionScore(score);
        diary.setEmotionLabel(label);
        diary.setAiStatus("DONE");
        diary.setAiReply(suggestion.isBlank()
                ? finalReply
                : finalReply + "\n\n💡 小建议：" + suggestion);
        diaryMapper.updateById(diary);

        statsService.refreshDailyStat(diary.getUserId(), LocalDate.now());

        send(emitter, "meta", Map.of(
                "emotionScore", score,
                "emotionLabel", label,
                "suggestion", suggestion), stopped);
        send(emitter, "done", Map.of("aiStatus", "DONE"), stopped);
        emitter.complete();
    }

    private boolean isKeyMissing() {
        String key = aiProperties.getApiKey();
        return key == null || key.isBlank() || key.contains("your-api-key");
    }

    /**
     * 离线 Mock 陪伴回复：未配置 AI_API_KEY 时使用，体验与真实 AI 一致（SSE 流式 + 情绪打分）。
     */
    private void mockAnalyze(Diary diary, SseEmitter emitter, AtomicBoolean stopped) {
        final String reply = buildMockReply(diary.getContent());
        final int score = deriveScore(diary.getContent());
        final String label = deriveLabel(score);
        final String suggestion = "试着把注意力放回当下：喝杯温水，做几次深呼吸，再完成一件很小的事。";

        diary.setEmotionScore(score);
        diary.setEmotionLabel(label);
        diary.setAiStatus("DONE");
        diary.setAiReply(reply + "\n\n💡 小建议：" + suggestion);
        diaryMapper.updateById(diary);
        statsService.refreshDailyStat(diary.getUserId(), LocalDate.now());

        send(emitter, "start", Map.of());
        Thread t = new Thread(() -> {
            try {
                int step = 4;
                for (int i = 0; i < reply.length() && !stopped.get(); i += step) {
                    send(emitter, "delta", Map.of("text",
                            reply.substring(i, Math.min(i + step, reply.length()))), stopped);
                    Thread.sleep(25);
                }
                send(emitter, "meta", Map.of(
                        "emotionScore", score,
                        "emotionLabel", label,
                        "suggestion", suggestion), stopped);
                send(emitter, "done", Map.of("aiStatus", "DONE"), stopped);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                emitter.complete();
            }
        });
        t.setDaemon(true);
        t.setName("mock-ai-stream");
        t.start();
    }

    private static final List<Map.Entry<String, Integer>> EMOTION_HINTS = List.of(
            Map.entry("开心", 9), Map.entry("高兴", 9), Map.entry("快乐", 9), Map.entry("幸福", 9),
            Map.entry("满足", 8), Map.entry("平静", 6), Map.entry("放松", 7),
            Map.entry("焦虑", 3), Map.entry("紧张", 3), Map.entry("担心", 3), Map.entry("压力", 3),
            Map.entry("难过", 2), Map.entry("伤心", 2), Map.entry("低落", 2), Map.entry("沮丧", 2),
            Map.entry("哭", 2), Map.entry("愤怒", 2), Map.entry("生气", 2), Map.entry("烦躁", 3),
            Map.entry("疲惫", 3), Map.entry("累", 3), Map.entry("孤独", 3), Map.entry("寂寞", 3));

    private int deriveScore(String content) {
        if (content == null || content.isEmpty()) {
            return 6;
        }
        int sum = 0;
        int count = 0;
        for (Map.Entry<String, Integer> hint : EMOTION_HINTS) {
            if (content.contains(hint.getKey())) {
                sum += hint.getValue();
                count++;
            }
        }
        return count == 0 ? 6 : clampScore(sum / count);
    }

    private String deriveLabel(int score) {
        if (score >= 8) {
            return "开心";
        }
        if (score >= 6) {
            return "平静";
        }
        if (score >= 4) {
            return "疲惫";
        }
        if (score >= 3) {
            return "焦虑";
        }
        return "低落";
    }

    private String buildMockReply(String content) {
        int score = deriveScore(content);
        if (score >= 7) {
            return "读到你的分享，能感受到这份" + deriveLabel(score) + "，真替你高兴。这些美好的瞬间值得被好好记住，也谢谢你愿意把它写下来。";
        }
        if (score >= 5) {
            return "谢谢你把此刻的感受写下来。生活总有起伏，你的平静与觉察本身就是一种力量，我会一直在这里听你说。";
        }
        return "我能感受到你此刻的不容易，辛苦你了。情绪来了不一定是坏事，它只是在提醒你该照顾一下自己。慢慢来，我会一直陪着你。";
    }

    private String extractDelta(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("delta").path("content");
                if (!content.isMissingNode() && !content.isNull()) {
                    return content.asText();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode parseMeta(String jsonLine) {
        try {
            return objectMapper.readTree(jsonLine.trim());
        } catch (Exception e) {
            return extractMetaFromText(jsonLine);
        }
    }

    /** 从任意文本中提取第一个 JSON 对象 */
    private JsonNode extractMetaFromText(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(text.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private int clampScore(int score) {
        return Math.max(1, Math.min(10, score));
    }

    private void send(SseEmitter emitter, String name, Object data) {
        send(emitter, name, data, null);
    }

    /**
     * stopped 非空代表这是一轮流式推送：一旦推送失败就置位，后续推送静默跳过，
     * 避免客户端断开后还在逐段刷日志。
     */
    private void send(SseEmitter emitter, String name, Object data, AtomicBoolean stopped) {
        if (stopped != null && stopped.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            if (stopped == null) {
                log.warn("SSE 推送失败（客户端可能已断开）: {}", e.getMessage());
            } else if (stopped.compareAndSet(false, true)) {
                log.warn("SSE 推送失败（客户端可能已断开），已终止本轮流式推送: {}", e.getMessage());
            }
        }
    }
}
