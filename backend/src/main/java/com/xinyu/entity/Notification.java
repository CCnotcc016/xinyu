package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    public static final String LIKE = "LIKE";
    public static final String COMMENT = "COMMENT";
    public static final String COMMENT_REPLY = "COMMENT_REPLY";
    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String CHAT = "CHAT";
    public static final String SUPPORT = "SUPPORT";
    /** 举报处理结果回执：举报人收到「你的举报已处理 / 未通过」 */
    public static final String REPORT = "REPORT";

    public static final String TARGET_POST = "POST";
    public static final String TARGET_USER = "USER";
    public static final String TARGET_CONVERSATION = "CONVERSATION";
    /** 举报回执：点开跳到「我的举报」 */
    public static final String TARGET_REPORT = "REPORT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String targetType;
    private Long targetId;
    private Integer isRead;
    private LocalDateTime createdAt;
}
