package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次会话的完整内容（用户侧和客服侧共用）。
 *
 * <p>用户侧只需要 {@code assigneeName} + {@code messages}；
 * 客服侧额外关心 {@code userId}/{@code username}/{@code nickname}/{@code assigneeId}。</p>
 */
@Data
public class SupportThreadVO {

    private Long conversationId;
    /** 会话尚未建立（用户还没发过消息）时为 null */
    private Long userId;
    /** 用户账号（手机号已打码） */
    private String username;
    private String nickname;
    /** 接手人 id，NULL = 待接手 */
    private Long assigneeId;
    /** 接手人昵称，用户侧显示「客服 · 小语」 */
    private String assigneeName;
    /** 请求方视角的未读数 */
    private Integer unread;
    /** OPEN=进行中，CLOSED=客服已结单（用户再来消息会自动开启新一轮） */
    private String status;
    private LocalDateTime closedAt;
    /** 结单人昵称，用户侧据此显示「客服 · 小语 已结束本次会话」 */
    private String closedByName;
    /** 当前登录的客服/管理员是否可以结单（用户侧恒为 false） */
    private Boolean canClose;
    private List<SupportMessageVO> messages;

    public static SupportThreadVO empty() {
        SupportThreadVO vo = new SupportThreadVO();
        vo.unread = 0;
        vo.messages = List.of();
        return vo;
    }
}
