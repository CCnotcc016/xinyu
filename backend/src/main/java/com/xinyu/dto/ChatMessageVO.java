package com.xinyu.dto;

import com.xinyu.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;

/** 一条私聊消息（mine 由后端算好，前端只负责左右分栏） */
@Data
public class ChatMessageVO {

    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    /** 是不是我发的 */
    private Boolean mine;
    /** 对方是否已读（只对我发出的消息有意义） */
    private Boolean read;
    /** 这条消息命中危机关键词，前端给发送方弹一段关怀提示（不通知管理员） */
    private Boolean crisis;
    private LocalDateTime createdAt;

    public static ChatMessageVO from(ChatMessage m, Long me) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.id = m.getId();
        vo.fromUserId = m.getFromUserId();
        vo.toUserId = m.getToUserId();
        vo.content = m.getContent();
        vo.mine = me != null && me.equals(m.getFromUserId());
        vo.read = m.getReadAt() != null;
        vo.createdAt = m.getCreatedAt();
        return vo;
    }
}
