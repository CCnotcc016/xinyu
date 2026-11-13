package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.dto.CalendarDayVO;
import com.xinyu.dto.CalendarVO;
import com.xinyu.dto.DistItem;
import com.xinyu.dto.DistributionVO;
import com.xinyu.dto.EmotionInsightVO;
import com.xinyu.dto.RecapVO;
import com.xinyu.dto.TrendPoint;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.EmotionDailyStat;
import com.xinyu.entity.Tag;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.EmotionDailyStatMapper;
import com.xinyu.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;

    /** 情绪趋势：range = day（3 天）/ week（7 天）/ month（30 天）；end 为窗口右端日期，缺省为今天 */
    public List<TrendPoint> trend(Long userId, String range, LocalDate end) {
        int days = switch (range == null ? "week" : range) {
            case "month" -> 30;
            case "day" -> 3;
            default -> 7;
        };
        LocalDate today = LocalDate.now();
        // 「日」视图支持往前翻：end 是用户选的那一天，窗口以它为最后一天
        LocalDate anchor = end == null || end.isAfter(today) ? today : end;
        LocalDate start = anchor.minusDays(days - 1L);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, start.atStartOfDay())
                // 窗口右端含当天全天，否则翻回今天时会少掉今天的数据
                .lt(Diary::getCreatedAt, anchor.plusDays(1).atStartOfDay())
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

    /**
     * 情绪洞察：天气 / 场景 / 标签 / 星期四个维度下的平均分。
     *
     * <p>只看已经有 AI 情绪分的日记，且各维度的空值直接跳过 —— 「没写天气」不代表「晴天」。</p>
     */
    public EmotionInsightVO insights(Long userId) {
        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .isNotNull(Diary::getEmotionScore));

        EmotionInsightVO vo = new EmotionInsightVO();
        vo.setDiaryCount(diaries.size());
        vo.setWeather(groups(diaries, Diary::getWeather, null));
        vo.setScene(groups(diaries, Diary::getScene, null));
        vo.setWeekday(groups(diaries, d -> weekdayName(d.getCreatedAt().getDayOfWeek()), null));
        vo.setTags(tagGroups(diaries));
        vo.setHighlights(highlights(vo));
        return vo;
    }

    /**
     * 按某个维度分组算平均分，结果按平均分从高到低。
     *
     * @param order 非空时按这个顺序输出（星期用得到），空值分组不参与
     */
    private List<EmotionInsightVO.Group> groups(List<Diary> diaries,
                                                Function<Diary, String> keyOf,
                                                List<String> order) {
        Map<String, List<Diary>> byKey = diaries.stream()
                .filter(d -> keyOf.apply(d) != null && !keyOf.apply(d).isBlank())
                .collect(Collectors.groupingBy(keyOf, LinkedHashMap::new, Collectors.toList()));
        List<EmotionInsightVO.Group> list = byKey.entrySet().stream()
                .map(e -> new EmotionInsightVO.Group(e.getKey(), round1(avg(e.getValue())), e.getValue().size()))
                .sorted((a, b) -> Double.compare(b.getAvgScore(), a.getAvgScore()))
                .collect(Collectors.toList());
        if (order != null) {
            list.sort((a, b) -> Integer.compare(order.indexOf(a.getKey()), order.indexOf(b.getKey())));
        }
        return list;
    }

    /** 标签维度：一篇日记可能挂多个标签，会分别计入各自的统计 */
    private List<EmotionInsightVO.Group> tagGroups(List<Diary> diaries) {
        if (diaries.isEmpty()) {
            return List.of();
        }
        Map<Long, Diary> byId = diaries.stream()
                .collect(Collectors.toMap(Diary::getId, d -> d, (a, b) -> a));
        List<DiaryTag> links = diaryTagMapper.selectList(new LambdaQueryWrapper<DiaryTag>()
                .in(DiaryTag::getDiaryId, byId.keySet()));
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Tag> tags = tagMapper.selectBatchIds(links.stream()
                        .map(DiaryTag::getTagId).distinct().toList()).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<String, List<Diary>> byTag = new LinkedHashMap<>();
        for (DiaryTag link : links) {
            Tag tag = tags.get(link.getTagId());
            Diary diary = byId.get(link.getDiaryId());
            if (tag == null || diary == null) {
                continue;
            }
            byTag.computeIfAbsent(tag.getName(), k -> new ArrayList<>()).add(diary);
        }
        return byTag.entrySet().stream()
                .map(e -> new EmotionInsightVO.Group(e.getKey(), round1(avg(e.getValue())), e.getValue().size()))
                .sorted((a, b) -> Double.compare(b.getAvgScore(), a.getAvgScore()))
                .collect(Collectors.toList());
    }

    /**
     * 把统计结果翻成一两句人话。
     *
     * <p>样本少于 2 篇的维度不下结论 —— 一篇日记的「平均分」什么也说明不了，
     * 说出来反而像 AI 在编。</p>
     */
    private List<String> highlights(EmotionInsightVO vo) {
        List<String> lines = new ArrayList<>();
        if (vo.getDiaryCount() == null || vo.getDiaryCount() < 2) {
            return lines;
        }
        List<EmotionInsightVO.Group> weather = enough(vo.getWeather());
        if (weather.size() >= 2) {
            EmotionInsightVO.Group top = weather.get(0);
            EmotionInsightVO.Group low = weather.get(weather.size() - 1);
            double diff = round1(top.getAvgScore() - low.getAvgScore());
            if (diff >= 0.5) {
                lines.add(String.format("%s平均 %.1f 分，比%s高 %.1f 分。",
                        top.getKey(), top.getAvgScore(), low.getKey(), diff));
            }
        }
        List<EmotionInsightVO.Group> scene = enough(vo.getScene());
        if (!scene.isEmpty()) {
            EmotionInsightVO.Group top = scene.get(0);
            lines.add(String.format("在「%s」时你的状态最好，平均 %.1f 分（%d 篇）。",
                    top.getKey(), top.getAvgScore(), top.getCount()));
        }
        List<EmotionInsightVO.Group> tags = enough(vo.getTags());
        if (!tags.isEmpty()) {
            EmotionInsightVO.Group low = tags.get(tags.size() - 1);
            if (low.getAvgScore() < 5) {
                lines.add(String.format("带「%s」的日记平均只有 %.1f 分，值得多留意一下。",
                        low.getKey(), low.getAvgScore()));
            }
        }
        List<EmotionInsightVO.Group> weekday = enough(vo.getWeekday());
        if (weekday.size() >= 3) {
            EmotionInsightVO.Group low = weekday.get(weekday.size() - 1);
            lines.add(String.format("%s是一周里最低的一天（平均 %.1f 分）。",
                    low.getKey(), low.getAvgScore()));
        }
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    /** 至少 2 篇才拿来说事 */
    private List<EmotionInsightVO.Group> enough(List<EmotionInsightVO.Group> groups) {
        if (groups == null) {
            return List.of();
        }
        return groups.stream().filter(g -> g.getCount() >= 2).collect(Collectors.toList());
    }

    private String weekdayName(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
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

    /**
     * 情绪日历：最近 days 天，每天一条日记就行（一天写多篇按平均分算）。
     *
     * <p>streak / maxStreak 是「连续记录天数」：今天没写但昨天写了，连续天数仍然保留，
     * 这样白天还没写日记的时候徽章不会突然断掉。</p>
     */
    public CalendarVO calendar(Long userId, int days) {
        int span = Math.min(Math.max(days, 7), 366);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(span - 1L);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, start.atStartOfDay())
                .lt(Diary::getCreatedAt, today.plusDays(1).atStartOfDay())
                .isNotNull(Diary::getEmotionScore)
                .orderByAsc(Diary::getCreatedAt));

        Map<LocalDate, List<Diary>> byDate = diaries.stream()
                .collect(Collectors.groupingBy(d -> d.getCreatedAt().toLocalDate()));

        List<CalendarDayVO> list = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate d = start.plusDays(i);
            List<Diary> day = byDate.getOrDefault(d, List.of());
            CalendarDayVO vo = new CalendarDayVO();
            vo.setDate(d.toString());
            vo.setCount(day.size());
            vo.setAvgScore(day.isEmpty() ? null
                    : BigDecimal.valueOf(avg(day)).setScale(1, RoundingMode.HALF_UP).doubleValue());
            vo.setLabel(day.isEmpty() ? null : dominant(day));
            list.add(vo);
        }

        CalendarVO result = new CalendarVO();
        result.setDays(list);
        result.setActiveDays(byDate.size());
        result.setTotalCount(diaries.size());
        result.setStreak(currentStreak(userId, today));
        result.setMaxStreak(maxStreak(userId));
        return result;
    }

    /**
     * 当前连续记录天数：从今天（或昨天）往前数，遇到第一个没写日记的日子就停。
     */
    private int currentStreak(Long userId, LocalDate today) {
        Set<LocalDate> written = writtenDates(userId, today.minusDays(366));
        LocalDate cursor = written.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (written.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** 历史最长连续天数 */
    private int maxStreak(Long userId) {
        List<LocalDate> dates = new ArrayList<>(writtenDates(userId, null));
        Collections.sort(dates);
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    /** 写过日记的所有日期（有情绪分的才算） */
    private Set<LocalDate> writtenDates(Long userId, LocalDate since) {
        LambdaQueryWrapper<Diary> w = new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .isNotNull(Diary::getEmotionScore);
        if (since != null) {
            w.ge(Diary::getCreatedAt, since.atStartOfDay());
        }
        return diaryMapper.selectList(w).stream()
                .map(d -> d.getCreatedAt().toLocalDate())
                .collect(Collectors.toSet());
    }

    /** 指定区间内的日记，周报和趋势都按这个口径取数 */
    public List<Diary> diariesBetween(Long userId, LocalDate start, LocalDate endExclusive) {
        return diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, start.atStartOfDay())
                .lt(Diary::getCreatedAt, endExclusive.atStartOfDay())
                .isNotNull(Diary::getEmotionScore)
                .orderByAsc(Diary::getCreatedAt));
    }

    /** 一组日记的平均分（保留一位小数），空集合返回 null */
    public Double avgOf(List<Diary> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(avg(list)).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** 一组日记里出现最多的情绪标签 */
    public String dominantOf(List<Diary> list) {
        return list == null || list.isEmpty() ? null : dominant(list);
    }

    /**
     * 月度 / 年度回顾。
     *
     * <p>口径说明：还走完的周期只统计到今天，否则趋势柱状图后半段全是 0；
     * 与上一周期的对比用的是「已结束的完整周期」，所以月中看回顾时，
     * 「比上月高」这句是有水分的 —— 文案里不写绝对结论，只给数字。</p>
     */
    public RecapVO recap(Long userId, String period, LocalDate anchor) {
        boolean year = "year".equalsIgnoreCase(period);
        LocalDate today = LocalDate.now();
        LocalDate anchorDate = anchor == null || anchor.isAfter(today) ? today : anchor;
        LocalDate start = year ? LocalDate.of(anchorDate.getYear(), 1, 1) : anchorDate.withDayOfMonth(1);
        LocalDate endExclusive = year ? start.plusYears(1) : start.plusMonths(1);
        LocalDate statEnd = endExclusive.isAfter(today.plusDays(1)) ? today.plusDays(1) : endExclusive;

        List<Diary> all = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, start.atStartOfDay())
                .lt(Diary::getCreatedAt, statEnd.atStartOfDay())
                .orderByAsc(Diary::getCreatedAt));
        List<Diary> scored = all.stream().filter(d -> d.getEmotionScore() != null).toList();

        LocalDate prevStart = year ? start.minusYears(1) : start.minusMonths(1);
        List<Diary> prevAll = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId)
                .ge(Diary::getCreatedAt, prevStart.atStartOfDay())
                .lt(Diary::getCreatedAt, start.atStartOfDay()));
        List<Diary> prevScored = prevAll.stream().filter(d -> d.getEmotionScore() != null).toList();

        RecapVO vo = new RecapVO();
        vo.setPeriod(year ? "YEAR" : "MONTH");
        vo.setLabel(year ? start.getYear() + " 年" : start.getYear() + " 年 " + start.getMonthValue() + " 月");
        vo.setEmpty(all.isEmpty());
        vo.setDiaryCount(all.size());
        vo.setActiveDays((int) all.stream().map(d -> d.getCreatedAt().toLocalDate()).distinct().count());
        vo.setAvgScore(avgOf(scored));
        vo.setDominantLabel(dominantOf(scored));
        vo.setPrevAvgScore(avgOf(prevScored));
        vo.setDiaryCountDelta((double) (all.size() - prevAll.size()));
        if (vo.getAvgScore() != null && vo.getPrevAvgScore() != null) {
            vo.setAvgScoreDelta(round1(vo.getAvgScore() - vo.getPrevAvgScore()));
        }

        Map<String, Long> tagCounts = tagCounts(scored);
        tagCounts.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> {
            vo.setTopTag(e.getKey());
            vo.setTopTagCount(e.getValue().intValue());
        });
        vo.setTopScene(mode(all.stream().map(Diary::getScene).toList()));
        vo.setTopWeather(mode(all.stream().map(Diary::getWeather).toList()));

        List<LocalDate> dates = all.stream().map(d -> d.getCreatedAt().toLocalDate())
                .distinct().sorted().toList();
        vo.setMaxStreak(streakOf(dates));

        Map<LocalDate, List<Diary>> byDate = scored.stream()
                .collect(Collectors.groupingBy(d -> d.getCreatedAt().toLocalDate()));
        vo.setBestDay(extremeDay(byDate, true));
        vo.setHardestDay(extremeDay(byDate, false));

        List<RecapVO.RecapBucket> buckets = new ArrayList<>();
        if (year) {
            for (int m = 1; m <= 12; m++) {
                LocalDate from = LocalDate.of(start.getYear(), m, 1);
                if (!from.isBefore(endExclusive)) {
                    break;
                }
                buckets.add(bucket(m + "月", inRange(all, from, from.plusMonths(1))));
            }
        } else {
            for (int week = 0; week < 5; week++) {
                LocalDate from = start.plusDays(week * 7L);
                if (!from.isBefore(endExclusive)) {
                    break;
                }
                LocalDate to = from.plusDays(7).isAfter(endExclusive) ? endExclusive : from.plusDays(7);
                // 最后一段的 to 是「下个月 1 号」，直接取 dayOfMonth 会减出 0 号，所以用 to - 1 天
                LocalDate last = to.minusDays(1);
                String label = last.getMonthValue() == from.getMonthValue()
                        ? from.getDayOfMonth() + "-" + last.getDayOfMonth() + "日"
                        : from.getDayOfMonth() + "日-" + last.getMonthValue() + "月" + last.getDayOfMonth() + "日";
                buckets.add(bucket(label, inRange(all, from, to)));
            }
        }
        vo.setBuckets(buckets);
        vo.setHighlights(recapHighlights(vo, year));
        return vo;
    }

    private RecapVO.RecapBucket bucket(String label, List<Diary> list) {
        List<Diary> scored = list.stream().filter(d -> d.getEmotionScore() != null).toList();
        return new RecapVO.RecapBucket(label, list.size(), avgOf(scored));
    }

    private List<Diary> inRange(List<Diary> list, LocalDate from, LocalDate toExclusive) {
        return list.stream()
                .filter(d -> !d.getCreatedAt().toLocalDate().isBefore(from)
                        && d.getCreatedAt().toLocalDate().isBefore(toExclusive))
                .toList();
    }

    /** 平均分最高 / 最低的那一天；多天同分取天数多的那天 */
    private RecapVO.RecapDay extremeDay(Map<LocalDate, List<Diary>> byDate, boolean best) {
        return byDate.entrySet().stream()
                .map(e -> new RecapVO.RecapDay(
                        e.getKey().getMonthValue() + "月" + e.getKey().getDayOfMonth() + "日",
                        round1(avg(e.getValue())), e.getValue().size()))
                .reduce((a, b) -> {
                    int cmp = Double.compare(a.getAvgScore(), b.getAvgScore());
                    boolean aWins = best ? cmp > 0 : cmp < 0;
                    if (cmp == 0) {
                        return a.getCount() >= b.getCount() ? a : b;
                    }
                    return aWins ? a : b;
                })
                .orElse(null);
    }

    private int streakOf(List<LocalDate> sortedDates) {
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : sortedDates) {
            run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    /** 标签出现次数（一篇日记挂多个标签时各计一次） */
    private Map<String, Long> tagCounts(List<Diary> diaries) {
        if (diaries.isEmpty()) {
            return Map.of();
        }
        List<DiaryTag> links = diaryTagMapper.selectList(new LambdaQueryWrapper<DiaryTag>()
                .in(DiaryTag::getDiaryId, diaries.stream().map(Diary::getId).toList()));
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = tagMapper.selectBatchIds(links.stream()
                        .map(DiaryTag::getTagId).distinct().toList()).stream()
                .filter(t -> "EMOTION".equals(t.getType()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return links.stream()
                .map(l -> names.get(l.getTagId()))
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private String mode(List<String> values) {
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<String> recapHighlights(RecapVO vo, boolean year) {
        List<String> lines = new ArrayList<>();
        String span = year ? "这一年" : "这个月";
        if (vo.getDiaryCount() == 0) {
            lines.add(span + "还没有记录，从今天写一篇也不晚。");
            return lines;
        }
        if (vo.getAvgScore() != null) {
            StringBuilder sb = new StringBuilder("写下 ").append(vo.getDiaryCount())
                    .append(" 篇日记，平均 ").append(String.format("%.1f", vo.getAvgScore())).append(" 分");
            if (vo.getAvgScoreDelta() != null) {
                double delta = vo.getAvgScoreDelta();
                sb.append(delta == 0 ? "，和上一周期持平"
                        : String.format("，比上一周期%s了 %.1f 分", delta > 0 ? "高" : "低", Math.abs(delta)));
            }
            lines.add(sb.append("。").toString());
        } else {
            lines.add("写下 " + vo.getDiaryCount() + " 篇日记，还没有 AI 情绪分。");
        }
        if (vo.getTopTag() != null) {
            lines.add(String.format("最常出现的情绪是「%s」，出现了 %d 次。",
                    vo.getTopTag(), vo.getTopTagCount()));
        }
        if (vo.getBestDay() != null && vo.getHardestDay() != null
                && !vo.getBestDay().getLabel().equals(vo.getHardestDay().getLabel())) {
            lines.add(String.format("心情最好的是 %s（%.1f 分），最难的是 %s（%.1f 分）。",
                    vo.getBestDay().getLabel(), vo.getBestDay().getAvgScore(),
                    vo.getHardestDay().getLabel(), vo.getHardestDay().getAvgScore()));
        }
        if (vo.getMaxStreak() >= 2) {
            lines.add("最长连续记录了 " + vo.getMaxStreak() + " 天。");
        }
        if (vo.getTopScene() != null && !"其他".equals(vo.getTopScene())) {
            lines.add("最常在「" + vo.getTopScene() + "」的时候写下心事。");
        }
        return lines;
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
