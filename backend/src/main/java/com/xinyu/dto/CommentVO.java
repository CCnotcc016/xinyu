// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import com.xinyu.entity.PostComment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {

    private Long id;
    private Long postId;
    private Long userId;
    /** 被回复的评论 id，NULL 表示直接评论帖子 */
    private Long parentId;
    /** 被回复人的昵称，前端渲染成「回复 @昵称：」 */
    private String replyToNickname;
    private String content;
    private String nickname;
    private Boolean mine;
    private LocalDateTime createdAt;

    public static CommentVO from(PostComment comment, String nickname, boolean mine, String replyToNickname) {
        CommentVO vo = new CommentVO();
        vo.id = comment.getId();
        vo.postId = comment.getPostId();
        vo.userId = comment.getUserId();
        vo.parentId = comment.getParentId();
        vo.replyToNickname = replyToNickname;
        vo.content = comment.getContent();
        vo.nickname = nickname;
        vo.mine = mine;
        vo.createdAt = comment.getCreatedAt();
        return vo;
    }
}
