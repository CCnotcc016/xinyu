package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 危机预警记录：树洞帖子或日记里出现自残 / 自杀等关键词时落库，供管理员跟进。
 *
 * <p>正文只留摘要（500 字以内），不做全文快照，够管理员判断是否需要介入即可。</p>
 */
@Data
@TableName("crisis_alert")
public class CrisisAlert {

    /** 目标类型：帖子 / 日记 / 心理自评 */
    public static final String TARGET_POST = "POST";
    public static final String TARGET_DIARY = "DIARY";
    public static final String TARGET_ASSESSMENT = "ASSESSMENT";

    /** 待跟进 */
    public static final String STATUS_PENDING = "PENDING";
    /** 已跟进 */
    public static final String STATUS_HANDLED = "HANDLED";
    /** 已忽略（误报） */
    public static final String STATUS_DISMISSED = "DISMISSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String keyword;
    private String content;
    private String status;
    private String remark;
    private Long handledBy;
    private LocalDateTime handledAt;
    /** 回访：处理完之后隔几天再看一眼，确认当事人状态（v3.2） */
    private LocalDateTime followUpAt;
    private String followUpNote;
    private Long followUpBy;
    private LocalDateTime createdAt;
}
