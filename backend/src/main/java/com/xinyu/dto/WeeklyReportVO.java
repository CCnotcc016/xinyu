package com.xinyu.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 情绪周报：本周的统计 + 一段 AI 总结。
 */
@Data
public class WeeklyReportVO {

    private String weekStart;
    private String weekEnd;
    private Integer diaryCount;
    /** 本周平均情绪分，没写日记时为 null */
    private Double avgScore;
    /** 上周平均情绪分，用于对比 */
    private Double prevAvgScore;
    /** 本周平均分 - 上周平均分，负数表示情绪回落 */
    private Double delta;
    private String dominantLabel;
    /** 本周高频标签（最多 5 个） */
    private List<DistItem> labels;
    /** AI 生成的周报正文 */
    private String content;
    /** AI = 大模型生成，MOCK = 未配置 Key 时的兜底文案，EMPTY = 本周还没写日记 */
    private String source;
    /** 是否直接读的缓存（刷新时为 false） */
    private Boolean cached;
}
