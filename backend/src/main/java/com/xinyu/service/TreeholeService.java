// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.CommentRequest;
import com.xinyu.dto.CommentVO;
import com.xinyu.dto.TreeholePostRequest;
import com.xinyu.dto.TreeholePostVO;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 匿名树洞服务
 */
@Service
@RequiredArgsConstructor
public class TreeholeService {

    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final RateLimitService rateLimitService;
    private final CrisisSupportService crisisSupportService;
    private final CrisisAlertService crisisAlertService;

    public PageResult<TreeholePostVO> list(Long viewerId, long page, long size) {
        Page<TreeholePost> result = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TreeholePost>()
                        .eq(TreeholePost::getStatus, 1)
                        .orderByDesc(TreeholePost::getCreatedAt));
        Map<Long, String> nicknames = nicknames(result.getRecords().stream()
                .map(TreeholePost::getUserId).distinct().toList());
        // 点赞状态一次性批量查出，避免逐条 selectCount
        Set<Long> likedIds = likedPostIds(viewerId, result.getRecords().stream()
                .map(TreeholePost::getId).toList());
        List<TreeholePostVO> records = result.getRecords().stream()
                .map(p -> buildVO(p, viewerId, nicknames.get(p.getUserId()), likedIds.contains(p.getId())))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    public TreeholePostVO create(Long userId, TreeholePostRequest req) {
        if (sensitiveWordService.containsSensitive(req.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }
        if (!rateLimitService.checkPostRate(userId)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        TreeholePost post = new TreeholePost();
        post.setUserId(userId);
        post.setContent(req.getContent());
        post.setImages(req.getImages() == null || req.getImages().isEmpty()
                ? null : String.join(",", req.getImages()));
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        post.setIsAnonymous(Boolean.TRUE.equals(req.getIsAnonymous()) ? 1 : 0);
        postMapper.insert(post);
        TreeholePostVO vo = buildVO(post, userId, nickname(userId), false);
        // 危机表达不拦截：前端弹关怀弹窗给陪伴引导，同时记一条预警让管理员跟进
        if (crisisSupportService.containsCrisis(req.getContent())) {
            vo.setCrisisSupport(true);
            crisisAlertService.record(userId, CrisisAlert.TARGET_POST, post.getId(), req.getContent());
        }
        return vo;
    }

    public TreeholePostVO detail(Long viewerId, Long id) {
        TreeholePost post = requirePost(id);
        return buildVO(post, viewerId, nickname(post.getUserId()),
                likedPostIds(viewerId, List.of(id)).contains(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> like(Long userId, Long id) {
        requirePost(id);
        long count = likeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, id).eq(PostLike::getUserId, userId));
        if (count == 0) {
            try {
                PostLike like = new PostLike();
                like.setPostId(id);
                like.setUserId(userId);
                likeMapper.insert(like);
                postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                        .eq(TreeholePost::getId, id).setSql("like_count = like_count + 1"));
            } catch (DuplicateKeyException ignored) {
                // 并发下重复点赞，忽略
            }
        }
        return likeResult(userId, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unlike(Long userId, Long id) {
        requirePost(id);
        int deleted = likeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, id).eq(PostLike::getUserId, userId));
        if (deleted > 0) {
            postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                    .eq(TreeholePost::getId, id).setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
        return likeResult(userId, id);
    }

    public PageResult<CommentVO> comments(Long id, long page, long size) {
        requirePost(id);
        Page<PostComment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, id)
                        .eq(PostComment::getStatus, 1)
                        .orderByAsc(PostComment::getCreatedAt));
        List<PostComment> records = result.getRecords();

        // 被回复的评论可能不在当前页，单独批量查一次；昵称也一次查完，避免 N+1
        Map<Long, PostComment> parents = parents(records);
        List<Long> userIds = new ArrayList<>(records.stream().map(PostComment::getUserId).toList());
        parents.values().forEach(parent -> userIds.add(parent.getUserId()));
        Map<Long, String> nicknames = nicknames(userIds.stream().distinct().toList());

        List<CommentVO> voRecords = records.stream()
                .map(c -> {
                    PostComment parent = c.getParentId() == null ? null : parents.get(c.getParentId());
                    String replyTo = parent == null ? null : nicknames.getOrDefault(parent.getUserId(), "用户");
                    return CommentVO.from(c, nicknames.getOrDefault(c.getUserId(), "用户"), false, replyTo);
                })
                .toList();
        return PageResult.of(result.getTotal(), page, size, voRecords);
    }

    private Map<Long, PostComment> parents(List<PostComment> records) {
        Set<Long> ids = records.stream().map(PostComment::getParentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return commentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PostComment::getId, c -> c));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentVO addComment(Long userId, Long id, CommentRequest req) {
        if (sensitiveWordService.containsSensitive(req.getContent())) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }
        requirePost(id);
        PostComment parent = requireParent(id, req.getParentId());
        PostComment comment = new PostComment();
        comment.setPostId(id);
        comment.setUserId(userId);
        comment.setParentId(req.getParentId());
        comment.setContent(req.getContent());
        comment.setStatus(1);
        // 显式赋值：created_at 由数据库默认值填充，insert 后实体里仍是 null，
        // 而前端会把返回的评论直接插进列表，不赋值就会显示空白时间
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                .eq(TreeholePost::getId, id).setSql("comment_count = comment_count + 1"));
        return CommentVO.from(comment, nickname(userId), true,
                parent == null ? null : nickname(parent.getUserId()));
    }

    /** 校验被回复的评论：必须存在、未被删除，且属于同一个帖子 */
    private PostComment requireParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PostComment parent = commentMapper.selectById(parentId);
        if (parent == null || parent.getStatus() == null || parent.getStatus() != 1
                || !postId.equals(parent.getPostId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return parent;
    }

    private TreeholePost requirePost(Long id) {
        TreeholePost post = postMapper.selectById(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Map<String, Object> likeResult(Long userId, Long id) {
        boolean liked = likeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, id).eq(PostLike::getUserId, userId)) > 0;
        TreeholePost post = postMapper.selectById(id);
        Map<String, Object> map = new HashMap<>();
        map.put("liked", liked);
        map.put("likeCount", post == null ? 0 : post.getLikeCount());
        return map;
    }

    private TreeholePostVO buildVO(TreeholePost post, Long viewerId, String nickname, boolean liked) {
        boolean mine = viewerId != null && post.getUserId().equals(viewerId);
        return TreeholePostVO.from(post, nickname, liked, mine);
    }

    /** 批量查当前用户在这批帖子里的点赞集合，替代逐条 selectCount */
    private Set<Long> likedPostIds(Long viewerId, List<Long> postIds) {
        if (viewerId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return likeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, viewerId)
                        .in(PostLike::getPostId, postIds))
                .stream().map(PostLike::getPostId).collect(Collectors.toSet());
    }

    private String nickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getNickname()) ? "用户" : user.getNickname();
    }

    private Map<Long, String> nicknames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : "用户"));
    }
}
