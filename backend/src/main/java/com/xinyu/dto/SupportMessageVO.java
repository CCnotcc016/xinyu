package com.xinyu.dto;

import com.xinyu.entity.SupportMessage;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话中的一条消息（发送者昵称由服务层批量填充，避免 N+1 查询）
 */
@Data
public class SupportMessageVO {

    private Long id;
    private Long senderId;
    /** USER / ADMIN / STAFF / SYSTEM */
    private String senderRole;
    /** 发送者昵称，前端据此显示「客服 · 小语 回复」 */
    private String senderName;
    private String content;
    private LocalDateTime createdAt;

    public static SupportMessageVO from(SupportMessage message, String senderName) {
        SupportMessageVO vo = new SupportMessageVO();
        vo.id = message.getId();
        vo.senderId = message.getSenderId();
        vo.senderRole = message.getSenderRole();
        vo.senderName = senderName;
        vo.content = message.getContent();
        vo.createdAt = message.getCreatedAt();
        return vo;
    }
}
