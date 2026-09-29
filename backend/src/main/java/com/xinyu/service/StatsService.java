// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.dto.DistItem;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.entity.Diary;
import com.xinyu.entity.EmotionDailyStat;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.EmotionDailyStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 情绪统计服务
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DiaryMapper diaryMapper;
    private final EmotionDailyStatMapper emotionDailyStatMapper;

    /** 情绪趋势：range = day / week / month */
    public List<TrendPoint> trend(Long userId, String range) {
        int days = switch (range == null ? "week" : range) {
            case "month" -> 30;
            case "day" -> 1;
            default -> 7;
        };
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, start.atStartOfDay())
                .isNotNull(Diary::getEmotionScore)
                .orderByAsc(Diary::getCreatedAt));

        Map<LocalDate, List<Diary>> byDate = diaries.stream()
                .collect(Collectors.groupingBy(d -> d.getCreatedAt().toLocalDate()));

        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            List<Diary> list = byDate.getOrDefault(d, List.of());
            TrendPoint p = new TrendPoint();
            p.setDate(d.toString());
            p.setDiaryCount(list.size());
            p.setAvgScore(list.isEmpty() ? null : avg(list));
            p.setDominantLabel(list.isEmpty() ? null : dominant(list));
            points.add(p);
        }
        return points;
    }

    /** 情绪分布：标签分布 + 分值分布 */
    public DistributionVO distribution(Long userId) {
        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .isNotNull(Diary::getEmotionScore));

        Map<String, Long> labelCount = diaries.stream()
                .filter(d -> d.getEmotionLabel() != null && !d.getEmotionLabel().isBlank())
                .collect(Collectors.groupingBy(Diary::getEmotionLabel,
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> scoreCount = diaries.stream()
                .collect(Collectors.groupingBy(d -> String.valueOf(d.getEmotionScore()),
                        LinkedHashMap::new, Collectors.counting()));

        DistributionVO vo = new DistributionVO();
        vo.setLabels(labelCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new DistItem(e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
        vo.setScores(scoreCount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) ->
                        Integer.compare(Integer.parseInt(a), Integer.parseInt(b))))
                .map(e -> new DistItem(e.getKey() + "分", e.getValue()))
                .collect(Collectors.toList()));
        return vo;
    }

    /** 刷新某用户某日的情绪统计（AI 分析完成后调用） */
    public void refreshDailyStat(Long userId, LocalDate date) {
        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, date.atStartOfDay())
                .lt(Diary::getCreatedAt, date.plusDays(1).atStartOfDay())
                .isNotNull(Diary::getEmotionScore));

        EmotionDailyStat stat = emotionDailyStatMapper.selectOne(new LambdaQueryWrapper<EmotionDailyStat>()
                .eq(EmotionDailyStat::getUserId, userId)
                .eq(EmotionDailyStat::getStatDate, date));

        if (diaries.isEmpty()) {
            if (stat != null) {
                emotionDailyStatMapper.deleteById(stat.getId());
            }
            return;
        }

        if (stat == null) {
            stat = new EmotionDailyStat();
            stat.setUserId(userId);
            stat.setStatDate(date);
        }
        stat.setAvgScore(BigDecimal.valueOf(avg(diaries)).setScale(2, RoundingMode.HALF_UP));
        stat.setDiaryCount(diaries.size());
        stat.setDominantLabel(dominant(diaries));

        if (stat.getId() == null) {
            emotionDailyStatMapper.insert(stat);
        } else {
            emotionDailyStatMapper.updateById(stat);
        }
    }

    private double avg(List<Diary> list) {
        return list.stream().mapToInt(Diary::getEmotionScore).average().orElse(0);
    }

    private String dominant(List<Diary> list) {
        return list.stream()
                .map(Diary::getEmotionLabel)
                .filter(l -> l != null && !l.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("平静");
    }
}
