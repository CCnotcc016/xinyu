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
    /** 是否被贴主置顶（置顶的评论排在列表最前） */
    private Boolean pinned;
    /** 当前查看者是不是贴主 —— 是才有权置顶/取消置顶（可以置顶别人的评论） */
    private Boolean canPin;
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
        vo.pinned = comment.getPinnedAt() != null;
        vo.canPin = false;
        vo.createdAt = comment.getCreatedAt();
        return vo;
    }
}
