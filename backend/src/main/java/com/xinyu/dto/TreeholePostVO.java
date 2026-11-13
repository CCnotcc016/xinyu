package com.xinyu.dto;

import com.xinyu.entity.TreeholePost;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TreeholePostVO {

    private Long id;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean anonymous;
    private String nickname;
    /**
     * 非匿名帖子的作者 ID，前端据此把昵称做成可点的「主页」入口。
     * 匿名帖一律为 null —— 一旦返回 ID，匿名就形同虚设了。
     */
    private Long userId;
    private Boolean liked;
    private Boolean mine;
    private Boolean crisisSupport;
    private LocalDateTime createdAt;

    public static TreeholePostVO from(TreeholePost post, String nickname,
                                      boolean liked, boolean mine) {
        TreeholePostVO vo = new TreeholePostVO();
        vo.id = post.getId();
        vo.content = post.getContent();
        vo.images = splitImages(post.getImages());
        vo.likeCount = post.getLikeCount();
        vo.commentCount = post.getCommentCount();
        vo.anonymous = post.getIsAnonymous() != null && post.getIsAnonymous() == 1;
        vo.nickname = vo.anonymous ? "匿名" : nickname;
        vo.userId = vo.anonymous ? null : post.getUserId();
        vo.liked = liked;
        vo.mine = mine;
        vo.crisisSupport = false;
        vo.createdAt = post.getCreatedAt();
        return vo;
    }

    public static List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
