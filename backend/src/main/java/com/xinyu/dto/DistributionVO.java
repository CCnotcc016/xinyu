// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import lombok.Data;

import java.util.List;

/**
 * 情绪分布统计
 */
@Data
public class DistributionVO {

    /** 情绪标签分布 */
    private List<DistItem> labels;
    /** 情绪分值分布 */
    private List<DistItem> scores;
}
