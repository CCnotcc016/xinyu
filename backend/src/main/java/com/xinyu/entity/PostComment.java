package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_comment")
public class PostComment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    /** 被回复的评论 id，NULL 表示直接评论帖子 */
    private Long parentId;
    private String content;
    private Integer status;
    /** 置顶时间，NULL = 未置顶；贴主每个帖子最多置顶一条评论 */
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
}
