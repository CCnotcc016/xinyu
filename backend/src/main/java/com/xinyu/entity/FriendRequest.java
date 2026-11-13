package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请。被拒后还可以再申请，所以同一对用户可能有多条记录，
 * 「是否已申请」的判断只看 status = PENDING 的那条。
 */
@Data
@TableName("friend_request")
public class FriendRequest {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String message;
    private String status;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
