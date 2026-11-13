package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ai.AiService;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.AssessmentSubmitRequest;
import com.xinyu.dto.AssessmentVO;
import com.xinyu.dto.ScaleVO;
import com.xinyu.entity.Assessment;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.mapper.AssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 心理自评：答题 → 计分 → （可选）AI 解读 → 存档。
 *
 * <p>三条边界：</p>
 * <ol>
 *   <li><b>不做诊断。</b>严重程度只说「症状提示」，全部页面都带
 *       {@link AssessmentScaleRegistry#DISCLAIMER}；</li>
 *   <li><b>只把分数发给模型。</b>量表里没有自由文本，发给大模型的只有量表名、得分、档位和
 *       最近两周的日记统计（不含正文）；</li>
 *   <li><b>不拦人。</b>分数再高也照常出结果，风险阳性时额外给求助热线并落一条管理员预警，
 *       不做任何限制用户使用的事。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    /** AI 解读的日配额，和日记的 AI 分析分开计 */
    private static final int AI_DAILY_LIMIT = 10;

    private final AssessmentMapper assessmentMapper;
    private final AiService aiService;
    private final RateLimitService rateLimitService;
    private final CrisisAlertService crisisAlertService;
    private final StatsService statsService;

    public List<ScaleVO> scales() {
        return AssessmentScaleRegistry.all().stream().map(ScaleVO::from).toList();
    }

    /**
     * 提交一份自评。
     *
     * <p>计分同步完成并立刻落库，AI 解读随后写回同一条记录：
     * 模型挂了或者超配额，用户照样拿得到分数和固定建议，不会白答一遍。</p>
     */
    public AssessmentVO submit(Long userId, AssessmentSubmitRequest req) {
        AssessmentScaleRegistry.Scale scale = AssessmentScaleRegistry.find(req.getScaleKey());
        if (scale == null) {
            throw new BusinessException(ErrorCode.SCALE_NOT_FOUND);
        }
        List<Integer> answers = normalize(scale, req.getAnswers());
        AssessmentScaleRegistry.Score score = AssessmentScaleRegistry.score(scale, answers);

        Assessment a = new Assessment();
        a.setUserId(userId);
        a.setScaleKey(scale.key());
        a.setScaleName(scale.name());
        a.setAnswers(join(answers));
        a.setTotalScore(score.total());
        a.setMaxScore(score.maxScore());
        a.setSeverity(score.band().severity().name());
        a.setRiskFlag(score.risk());
        assessmentMapper.insert(a);

        // AI 解读：配额内才调，失败不影响主流程
        boolean quotaOk = rateLimitService.tryAcquire("assess-ai:" + userId, AI_DAILY_LIMIT, 86400L);
        String ai = quotaOk
                ? aiService.assessmentInterpretation(buildPrompt(userId, scale, answers, score))
                : null;
        a.setAiInterpretation(ai != null ? ai : fallbackText(scale, score));
        a.setAiSource(ai != null ? Assessment.SOURCE_AI : Assessment.SOURCE_MOCK);
        if (ai == null) {
            log.info("自评解读走本地兜底：user={} scale={} total={}", userId, scale.key(), score.total());
        }
        assessmentMapper.updateById(a);

        if (score.risk()) {
            crisisAlertService.recordRisk(userId, CrisisAlert.TARGET_ASSESSMENT, a.getId(),
                    "PHQ-9 第9题阳性（自伤/自杀念头）",
                    buildExcerpt(scale, score));
        }
        return AssessmentVO.from(a);
    }

    /** 历史记录，最新的在前 */
    public List<AssessmentVO> history(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<Assessment> list = assessmentMapper.selectList(new LambdaQueryWrapper<Assessment>()
                .eq(Assessment::getUserId, userId)
                .orderByDesc(Assessment::getCreatedAt)
                .orderByDesc(Assessment::getId)
                .last("LIMIT " + size));
        return list.stream().map(AssessmentVO::brief).toList();
    }

    public AssessmentVO detail(Long userId, Long id) {
        return AssessmentVO.from(owned(userId, id));
    }

    /** 重新生成解读：模型当时不可用时，用户可以在结果页点一下重试 */
    public AssessmentVO interpret(Long userId, Long id) {
        Assessment a = owned(userId, id);
        AssessmentScaleRegistry.Scale scale = AssessmentScaleRegistry.find(a.getScaleKey());
        if (scale == null) {
            throw new BusinessException(ErrorCode.SCALE_NOT_FOUND);
        }
        if (!rateLimitService.tryAcquire("assess-ai:" + userId, AI_DAILY_LIMIT, 86400L)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        List<Integer> answers = AssessmentVO.parseAnswers(a.getAnswers());
        AssessmentScaleRegistry.Score score = AssessmentScaleRegistry.score(scale, answers);
        String ai = aiService.assessmentInterpretation(buildPrompt(userId, scale, answers, score));
        if (ai == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
        a.setAiInterpretation(ai);
        a.setAiSource(Assessment.SOURCE_AI);
        assessmentMapper.updateById(a);
        return AssessmentVO.from(a);
    }

    public void delete(Long userId, Long id) {
        Assessment a = owned(userId, id);
        assessmentMapper.deleteById(a.getId());
    }

    /* ------------------------------------------------------------------ */

    private Assessment owned(Long userId, Long id) {
        Assessment a = assessmentMapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_NOT_FOUND);
        }
        return a;
    }

    /** 答案必须和题目数量一致、每项 0-3，否则直接拒掉，不拿脏数据去算分 */
    private List<Integer> normalize(AssessmentScaleRegistry.Scale scale, List<Integer> answers) {
        int n = scale.items().size();
        if (answers == null || answers.size() != n) {
            throw new BusinessException(ErrorCode.SCALE_ANSWERS_INVALID);
        }
        List<Integer> out = new ArrayList<>(n);
        for (Integer v : answers) {
            if (v == null || v < 0 || v > 3) {
                throw new BusinessException(ErrorCode.SCALE_ANSWERS_INVALID);
            }
            out.add(v);
        }
        return out;
    }

    private String join(List<Integer> answers) {
        StringJoiner joiner = new StringJoiner(",");
        answers.forEach(v -> joiner.add(String.valueOf(v)));
        return joiner.toString();
    }

    /**
     * 拼给模型的用户消息：量表结果 + 仅命中的题目 + 最近两周的日记统计。
     *
     * <p>只带统计、不带日记正文：解读量表不需要把用户写过的原话再发出去一次。</p>
     */
    private String buildPrompt(Long userId, AssessmentScaleRegistry.Scale scale,
                               List<Integer> answers, AssessmentScaleRegistry.Score score) {
        StringBuilder sb = new StringBuilder();
        sb.append("量表：").append(scale.name()).append("（").append(scale.source()).append("）\n");
        sb.append("总分：").append(score.total()).append(" / ").append(score.maxScore()).append('\n');
        sb.append("筛查档位：").append(score.band().label()).append('\n');
        if (score.risk()) {
            sb.append("注意：第 9 题（伤害自己的念头）为阳性，用户近期出现过这类念头。\n");
        }

        if (!score.dimensions().isEmpty()) {
            sb.append("子维度：");
            score.dimensions().forEach((k, v) -> sb.append(k).append(' ')
                    .append(v).append('/').append(AssessmentScaleRegistry.dimensionMax(scale)).append(' '));
            sb.append('\n');
        }

        sb.append("有明显困扰的题目：\n");
        boolean any = false;
        for (int i = 0; i < scale.items().size() && i < answers.size(); i++) {
            if (answers.get(i) > 0) {
                sb.append("- ").append(scale.items().get(i))
                        .append("（").append(labelOf(scale, answers.get(i))).append("）\n");
                any = true;
            }
        }
        if (!any) {
            sb.append("- 无，所有题目都选了「完全不会」\n");
        }

        LocalDate today = LocalDate.now();
        List<Diary> recent = statsService.diariesBetween(userId, today.minusDays(13), today.plusDays(1));
        sb.append("最近两周日记情况：");
        if (recent.isEmpty()) {
            sb.append("这段时间没有记录日记。\n");
        } else {
            sb.append("写了 ").append(recent.size()).append(" 篇");
            Double avg = statsService.avgOf(recent);
            if (avg != null) {
                sb.append("，平均情绪分 ").append(avg).append("/10");
            }
            String dominant = statsService.dominantOf(recent);
            if (dominant != null) {
                sb.append("，出现最多的情绪是「").append(dominant).append("」");
            }
            sb.append("。\n");
        }
        sb.append("请据此写一段温暖的解读。");
        return sb.toString();
    }

    /** 模型不可用时的兜底解读：档位建议 + 免责提醒，信息量够，措辞不越界 */
    private String fallbackText(AssessmentScaleRegistry.Scale scale, AssessmentScaleRegistry.Score score) {
        String tail = score.risk()
                ? AssessmentScaleRegistry.CRISIS_HINT
                : AssessmentScaleRegistry.SHORT_DISCLAIMER + "。";
        return "这份" + scale.shortName() + "得分 " + score.total() + " 分（满分 " + score.maxScore()
                + "），落在「" + score.band().label() + "」这一档。" + score.band().advice() + tail;
    }

    /** 预警摘要：管理员能一眼看出是谁、哪份量表、多少分 */
    private String buildExcerpt(AssessmentScaleRegistry.Scale scale, AssessmentScaleRegistry.Score score) {
        return scale.name() + " 总分 " + score.total() + "/" + score.maxScore()
                + "（" + score.band().label() + "），第 9 题阳性：出现过伤害自己的念头。";
    }

    private String labelOf(AssessmentScaleRegistry.Scale scale, Integer score) {
        for (AssessmentScaleRegistry.Option o : scale.options()) {
            if (o.value() == score) {
                return o.label();
            }
        }
        return String.valueOf(score);
    }
}
