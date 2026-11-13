package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ai.AiService;
import com.xinyu.dto.DistItem;
import com.xinyu.dto.WeeklyReportVO;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.EmotionWeeklyReport;
import com.xinyu.entity.Tag;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.EmotionWeeklyReportMapper;
import com.xinyu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 情绪周报：把一周的日记汇总成几个数字，再让大模型写成一段话。
 *
 * <p>两个取舍：</p>
 * <ul>
 *   <li>只把「统计结果 + 情绪标签」发给大模型，不把日记正文发出去。周报是回顾，
 *       不需要把用户这一周写的原话再上传一次。</li>
 *   <li>结果按周缓存（emotion_weekly_report），同一周重复打开不会反复消耗 token。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日");

    /** 关键词最多取几个 */
    private static final int MAX_KEYWORDS = 5;

    private final EmotionWeeklyReportMapper reportMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final StatsService statsService;
    private final AiService aiService;

    /**
     * @param weekStart 该周周一；为空时取本周一
     * @param refresh   true 表示忽略缓存重新生成
     */
    public WeeklyReportVO weekly(Long userId, LocalDate weekStart, boolean refresh) {
        LocalDate start = weekStart == null ? LocalDate.now().with(DayOfWeek.MONDAY) : weekStart;
        LocalDate end = start.plusDays(7);

        List<Diary> diaries = statsService.diariesBetween(userId, start, end);
        List<Diary> prev = statsService.diariesBetween(userId, start.minusDays(7), start);

        WeeklyReportVO vo = new WeeklyReportVO();
        vo.setWeekStart(start.toString());
        vo.setWeekEnd(start.plusDays(6).toString());
        vo.setDiaryCount(diaries.size());
        vo.setAvgScore(statsService.avgOf(diaries));
        vo.setPrevAvgScore(statsService.avgOf(prev));
        if (vo.getAvgScore() != null && vo.getPrevAvgScore() != null) {
            vo.setDelta(BigDecimal.valueOf(vo.getAvgScore() - vo.getPrevAvgScore())
                    .setScale(1, RoundingMode.HALF_UP).doubleValue());
        }
        vo.setDominantLabel(statsService.dominantOf(diaries));
        vo.setLabels(labelDistribution(diaries));
        vo.setCached(false);

        if (diaries.isEmpty()) {
            vo.setSource("EMPTY");
            vo.setContent("这一周还没有日记。不用有压力，从今天写一句「今天怎么样」就可以开始。");
            return vo;
        }

        EmotionWeeklyReport cached = reportMapper.selectOne(new LambdaQueryWrapper<EmotionWeeklyReport>()
                .eq(EmotionWeeklyReport::getUserId, userId)
                .eq(EmotionWeeklyReport::getWeekStart, start));
        if (cached != null && !refresh) {
            vo.setContent(cached.getContent());
            vo.setSource(cached.getSource());
            vo.setCached(true);
            return vo;
        }

        List<String> keywords = keywords(diaries);
        String content = aiService.weeklyReport(buildPrompt(vo, keywords));
        String source = EmotionWeeklyReport.SOURCE_AI;
        if (content == null || content.isBlank()) {
            content = mockReport(vo, keywords);
            source = EmotionWeeklyReport.SOURCE_MOCK;
        }
        vo.setContent(content);
        vo.setSource(source);
        cache(userId, start, vo, keywords, content);
        return vo;
    }

    /** 缓存落库（同一用户同一周只有一行） */
    private void cache(Long userId, LocalDate weekStart, WeeklyReportVO vo, List<String> keywords, String content) {
        try {
            EmotionWeeklyReport row = reportMapper.selectOne(new LambdaQueryWrapper<EmotionWeeklyReport>()
                    .eq(EmotionWeeklyReport::getUserId, userId)
                    .eq(EmotionWeeklyReport::getWeekStart, weekStart));
            if (row == null) {
                row = new EmotionWeeklyReport();
                row.setUserId(userId);
                row.setWeekStart(weekStart);
            }
            row.setDiaryCount(vo.getDiaryCount());
            row.setAvgScore(vo.getAvgScore() == null ? null
                    : BigDecimal.valueOf(vo.getAvgScore()).setScale(2, RoundingMode.HALF_UP));
            row.setDominantLabel(vo.getDominantLabel());
            row.setKeywords(String.join(",", keywords));
            row.setContent(content);
            row.setSource(vo.getSource());
            if (row.getId() == null) {
                reportMapper.insert(row);
            } else {
                reportMapper.updateById(row);
            }
        } catch (Exception e) {
            // 缓存失败不该让用户看不到周报
            log.warn("周报缓存写入失败: {}", e.getMessage());
        }
    }

    private String buildPrompt(WeeklyReportVO vo, List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("统计区间：").append(d(vo.getWeekStart())).append(" 至 ").append(d(vo.getWeekEnd())).append("\n");
        sb.append("记录次数：").append(vo.getDiaryCount()).append(" 次\n");
        sb.append("平均情绪分：").append(vo.getAvgScore()).append(" / 10（分数越高越积极）\n");
        if (vo.getPrevAvgScore() != null) {
            sb.append("上周平均情绪分：").append(vo.getPrevAvgScore()).append("\n");
            sb.append("与上周相比：").append(vo.getDelta() == null ? "无变化"
                    : (vo.getDelta() > 0 ? "上升 " : vo.getDelta() < 0 ? "下降 " : "持平 ")
                    + Math.abs(vo.getDelta())).append("\n");
        } else {
            sb.append("上周没有记录，无法对比\n");
        }
        sb.append("最常出现的情绪：").append(vo.getDominantLabel() == null ? "未标注" : vo.getDominantLabel()).append("\n");
        if (!keywords.isEmpty()) {
            sb.append("高频标签：").append(String.join("、", keywords)).append("\n");
        }
        sb.append("\n请按上面的数据写这段周报。");
        return sb.toString();
    }

    private String d(String isoDate) {
        return LocalDate.parse(isoDate).format(MD);
    }

    /** 未配置 API Key 或调用失败时的兜底文案，保证演示时一定有东西看 */
    private String mockReport(WeeklyReportVO vo, List<String> keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("这一周你记录了 ").append(vo.getDiaryCount()).append(" 次心情，平均 ")
                .append(vo.getAvgScore()).append(" 分");
        if (vo.getDelta() != null && vo.getDelta() != 0) {
            sb.append(vo.getDelta() > 0 ? "，比上周好一些" : "，比上周稍微低落了一点");
        }
        sb.append("。");
        if (vo.getDominantLabel() != null) {
            sb.append("出现最多的情绪是「").append(vo.getDominantLabel()).append("」，");
        }
        if (!keywords.isEmpty()) {
            sb.append("常常和「").append(String.join("、", keywords)).append("」有关，");
        }
        sb.append("愿意把这些写下来，本身就是一种照顾自己的方式。下周可以试着在同一时间写三句话，"
                + "让记录变成一件不那么费力的小事。");
        return sb.toString();
    }

    /** 这一周用得最多的情绪标签（取日记上打过的 tag） */
    private List<String> keywords(List<Diary> diaries) {
        List<Long> diaryIds = diaries.stream().map(Diary::getId).toList();
        if (diaryIds.isEmpty()) {
            return List.of();
        }
        List<DiaryTag> links = diaryTagMapper.selectList(new LambdaQueryWrapper<DiaryTag>()
                .in(DiaryTag::getDiaryId, diaryIds));
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> tagCount = links.stream()
                .collect(Collectors.groupingBy(DiaryTag::getTagId, Collectors.counting()));
        List<Long> tagIds = new ArrayList<>(tagCount.keySet());
        Map<Long, String> names = new HashMap<>();
        tagMapper.selectBatchIds(tagIds).forEach(t -> names.put(t.getId(), t.getName()));
        return tagCount.entrySet().stream()
                .filter(e -> names.containsKey(e.getKey()))
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(MAX_KEYWORDS)
                .map(e -> names.get(e.getKey()))
                .toList();
    }

    /** 本周情绪标签分布（饼图/标签云用） */
    private List<DistItem> labelDistribution(List<Diary> diaries) {
        Map<String, Long> count = diaries.stream()
                .filter(d -> d.getEmotionLabel() != null && !d.getEmotionLabel().isBlank())
                .collect(Collectors.groupingBy(Diary::getEmotionLabel,
                        LinkedHashMap::new, Collectors.counting()));
        return count.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new DistItem(e.getKey(), e.getValue()))
                .toList();
    }
}
