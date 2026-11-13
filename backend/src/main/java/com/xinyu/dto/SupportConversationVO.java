package com.xinyu.dto;

import com.xinyu.entity.SupportConversation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服工作台的会话列表项
 */
@Data
public class SupportConversationVO {

    private Long id;
    private Long userId;
    /** 用户昵称 */
    private String nickname;
    /** 用户账号（手机号打码），同名用户靠它区分 */
    private String username;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    /** 客服侧未读数 */
    private Integer unread;
    /** 接手人昵称，NULL = 待接手 */
    private String assigneeName;
    /** 是否由当前登录的客服/管理员接手 */
    private Boolean mine;
    /** OPEN=进行中，CLOSED=已结单 */
    private String status;
    private LocalDateTime closedAt;

    public static SupportConversationVO from(SupportConversation conversation, String nickname,
                                             String username, String assigneeName, boolean mine) {
        SupportConversationVO vo = new SupportConversationVO();
        vo.id = conversation.getId();
        vo.userId = conversation.getUserId();
        vo.nickname = nickname;
        vo.username = username;
        vo.lastMessage = conversation.getLastMessage();
        vo.lastMessageAt = conversation.getLastMessageAt();
        vo.unread = conversation.getStaffUnread() == null ? 0 : conversation.getStaffUnread();
        vo.assigneeName = assigneeName;
        vo.mine = mine;
        vo.status = conversation.getStatus();
        vo.closedAt = conversation.getClosedAt();
        return vo;
    }
}
