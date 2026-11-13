package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    private Integer emotionScore;
    private String emotionLabel;
    private String suggestion;
    private String empathyReply;
    /** 是否触发危机干预 */
    private boolean crisis;
}
