package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 好友申请（收到的 / 我发出的，用 direction 区分） */
@Data
public class FriendRequestVO {

    private Long id;
    /** 对方的用户 ID：收到的是申请人，发出的是被申请人 */
    private Long userId;
    private String nickname;
    private String avatar;
    private String message;
    private String status;
    /** RECEIVED = 别人发给我，SENT = 我发出去的 */
    private String direction;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
