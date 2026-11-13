package com.xinyu.dto;

import lombok.Data;

import java.util.List;

/**
 * 情绪日历：热力图数据 + 连续记录天数。
 */
@Data
public class CalendarVO {

    private List<CalendarDayVO> days;
    /** 当前连续记录天数（今天没写但昨天写了，仍算连续，直到断掉为止） */
    private Integer streak;
    /** 历史最长连续天数 */
    private Integer maxStreak;
    /** 统计区间内写日记的天数 */
    private Integer activeDays;
    /** 统计区间内日记总条数 */
    private Integer totalCount;
}
