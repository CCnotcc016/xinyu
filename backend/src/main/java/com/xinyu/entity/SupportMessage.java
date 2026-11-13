package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话里的一条消息。
 *
 * <p>发送时把 {@code sender_role} 一起落库，前端就能直接显示「客服 · 小语 回复」
 * 这类标识，不必回头再查发送者当时的角色（角色后续可能变动）。</p>
 */
@Data
@TableName("support_message")
public class SupportMessage {

    /** 用户发给客服 */
    public static final String ROLE_USER = "USER";
    /** 管理员回复 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 客服回复 */
    public static final String ROLE_STAFF = "STAFF";
    /** 系统提示（客服结单时留给用户的那条说明），不算任何一方发的消息 */
    public static final String ROLE_SYSTEM = "SYSTEM";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderRole;
    private String content;
    private LocalDateTime createdAt;
}
