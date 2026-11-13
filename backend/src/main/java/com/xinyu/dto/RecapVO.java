package com.xinyu.dto;

import lombok.Data;

import java.util.List;

/**
 * 月度 / 年度回顾。
 *
 * <p>只做「回顾」不做「报告」：数字都是用户自己写下的日记统计出来的，
 * 文案克制，不下判断，也不给建议。</p>
 */
@Data
public class RecapVO {

    /** MONTH / YEAR */
    private String period;
    /** 展示用标题，例如「2026 年 10 月」「2026 年」 */
    private String label;
    /** 该回顾内是否有日记；false 时前端显示空状态，不渲染图表 */
    private boolean empty;

    private int diaryCount;
    private int activeDays;
    private Double avgScore;
    /** 上一个月 / 上一年的平均分，没有数据时为 null */
    private Double prevAvgScore;
    /** 与上一周期的差（本期 - 上期），保留一位小数；任一为空时为 null */
    private Double avgScoreDelta;
    private Double diaryCountDelta;
    private String dominantLabel;
    private String topTag;
    private int topTagCount;
    private String topScene;
    private String topWeather;
    /** 这段时间内最长连续记录天数 */
    private int maxStreak;

    private RecapDay bestDay;
    private RecapDay hardestDay;

    /** 趋势柱子：月回顾按周（最多 5 段），年回顾按月（12 段） */
    private List<RecapBucket> buckets;
    private List<String> highlights;

    @Data
    public static class RecapDay {
        /** 已是可直接展示的文案，例如「10月9日」 */
        private String label;
        private double avgScore;
        private int count;

        public RecapDay(String label, double avgScore, int count) {
            this.label = label;
            this.avgScore = avgScore;
            this.count = count;
        }
    }

    @Data
    public static class RecapBucket {
        /** 例如「1-7日」「1月」 */
        private String label;
        private int count;
        private Double avgScore;

        public RecapBucket(String label, int count, Double avgScore) {
            this.label = label;
            this.count = count;
            this.avgScore = avgScore;
        }
    }
}
