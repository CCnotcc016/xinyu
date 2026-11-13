package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系。A 与 B 成为好友时存两行（A→B、B→A），
 * 查「我的好友」只需 user_id = 我，不用再判方向。
 */
@Data
@TableName("friend")
public class Friend {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long friendId;
    private LocalDateTime createdAt;
}
