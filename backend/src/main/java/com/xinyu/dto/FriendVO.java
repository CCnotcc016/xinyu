package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友 / 会话列表里的一项。
 * 好友列表和聊天列表用的是同一个结构：没聊过天的好友 lastMessage 为 null，
 * 前端就把「最后一条消息」换成「打个招呼吧」。
 */
@Data
public class FriendVO {

    private Long userId;
    private String nickname;
    private String avatar;
    /** 最后一条消息摘要，没聊过为 null */
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    /** 最后一条消息是不是我发的，前端显示「我：…」 */
    private Boolean lastMessageMine;
    /** 我未读的条数 */
    private Integer unread;
    private LocalDateTime friendsSince;
}
