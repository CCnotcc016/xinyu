package com.xinyu.dto;

import lombok.Data;

/** 私聊未读统计的中间结果：某个好友发给我几条还没读 */
@Data
public class PeerUnread {
    private Long peerId;
    private Integer unread;
}
