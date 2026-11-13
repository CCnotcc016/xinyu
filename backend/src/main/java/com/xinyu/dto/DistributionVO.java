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
