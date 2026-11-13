package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 未读提示：给首页弹窗用（用户侧=客服回复了我，客服侧=有用户消息待回复）
 */
@Data
public class SupportUnreadVO {

    private long count;
    private Long conversationId;
    /** 最新一条消息摘要 */
    private String latestMessage;
    /** 最新一条消息的发送者昵称 */
    private String latestSenderName;
    /** USER / ADMIN / STAFF */
    private String latestSenderRole;
    private LocalDateTime latestAt;

    public static SupportUnreadVO none() {
        SupportUnreadVO vo = new SupportUnreadVO();
        vo.count = 0;
        return vo;
    }
}
