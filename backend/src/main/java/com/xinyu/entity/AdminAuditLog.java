package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作审计：记录「谁、何时、对哪条数据、做了什么、为什么」。
 *
 * <p>目前只记录敏感操作（查看用户日记原文、删除日记 / 帖子）。
 * 目的是让「管理员看用户私密内容」这件事有据可查，不可悄悄发生。</p>
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    /** 查看日记原文 */
    public static final String ACTION_REVEAL_DIARY = "REVEAL_DIARY";
    /** 删除日记 */
    public static final String ACTION_DELETE_DIARY = "DELETE_DIARY";
    /** 删除帖子 */
    public static final String ACTION_DELETE_POST = "DELETE_POST";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminId;
    /** 动作，见上方常量 */
    private String action;
    private String targetType;
    private Long targetId;
    /** 当事用户（被查看 / 被删除内容的作者） */
    private Long targetUserId;
    /** 管理员填写的理由 */
    private String reason;
    private LocalDateTime createdAt;
}
