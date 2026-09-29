// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
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
