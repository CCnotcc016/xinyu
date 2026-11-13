package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情绪趋势数据点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint {

    private String date;
    private Double avgScore;
    private Integer diaryCount;
    private String dominantLabel;
}
