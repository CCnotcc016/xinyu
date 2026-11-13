package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 情绪周报缓存：同一个人同一周只调用一次大模型，
 * 之后直接读这张表，省 token 也省等待。
 */
@Data
@TableName("emotion_weekly_report")
public class EmotionWeeklyReport {

    /** 真实大模型生成 */
    public static final String SOURCE_AI = "AI";
    /** 未配置 API Key 时后端自己拼的兜底文案 */
    public static final String SOURCE_MOCK = "MOCK";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 该周周一 */
    private LocalDate weekStart;
    private Integer diaryCount;
    private BigDecimal avgScore;
    private String dominantLabel;
    private String keywords;
    private String content;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
