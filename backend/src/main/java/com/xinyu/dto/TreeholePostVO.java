// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
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
