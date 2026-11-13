package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分布统计项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistItem {

    private String name;
    private Long value;
}
