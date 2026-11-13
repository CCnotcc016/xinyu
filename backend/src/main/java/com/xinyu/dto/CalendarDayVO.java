package com.xinyu.dto;

import lombok.Data;

/** 情绪热力图里的一格 */
@Data
public class CalendarDayVO {

    private String date;
    /** 当天日记条数，0 表示这天没写 */
    private Integer count;
    /** 当天平均情绪分，没写日记时为 null */
    private Double avgScore;
    private String label;
}
