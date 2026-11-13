package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("emotion_daily_stat")
public class EmotionDailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate statDate;
    private BigDecimal avgScore;
    private Integer diaryCount;
    private String dominantLabel;
}
