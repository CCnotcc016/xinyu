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

    private static final int MAX_POST_IMAGES = 9;

    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final UserMapper userMapper;
    private final SensitiveWordService sensitiveWordService;
    private final RateLimitService rateLimitService;
    private final CrisisSupportService crisisSupportService;
    private final CrisisAlertService crisisAlertService;
    private final ImageUploadService imageUploadService;
    private final NotificationService notificationService;

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

    /**
     * 某个人公开的树洞帖子。匿名帖不在这里出现——匿名帖一旦能和某个账号对上号，匿名就没意义了。
     */
    public PageResult<TreeholePostVO> userPosts(Long viewerId, Long authorId, long page, long size) {
        Page<TreeholePost> result = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TreeholePost>()
                        .eq(TreeholePost::getUserId, authorId)
                        .eq(TreeholePost::getStatus, 1)
                        .eq(TreeholePost::getIsAnonymous, 0)
                        .orderByDesc(TreeholePost::getCreatedAt));
        String nickname = nickname(authorId);
        Set<Long> likedIds = likedPostIds(viewerId, result.getRecords().stream()
                .map(TreeholePost::getId).toList());
        List<TreeholePostVO> records = result.getRecords().stream()
                .map(p -> buildVO(p, viewerId, nickname, likedIds.contains(p.getId())))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    /** 公开帖子数 + 收到的点赞总数，个人主页上的活跃度 */
    public int[] publicStats(Long authorId) {
        List<TreeholePost> posts = postMapper.selectList(new LambdaQueryWrapper<TreeholePost>()
                .eq(TreeholePost::getUserId, authorId)
                .eq(TreeholePost::getStatus, 1)
                .eq(TreeholePost::getIsAnonymous, 0));
        int likes = posts.stream().mapToInt(p -> p.getLikeCount() == null ? 0 : p.getLikeCount()).sum();
        return new int[]{posts.size(), likes};
    }

    /** 单帖最多 9 张图，且只留本站上传目录下的地址，外链一律丢弃 */
    private List<String> safeImages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(imageUploadService::isOwnedPostImage)
                .distinct()
                .limit(MAX_POST_IMAGES)
                .toList();
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
        List<String> images = safeImages(req.getImages());
        post.setImages(images.isEmpty() ? null : String.join(",", images));
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

    /**
     * 贴主置顶/取消置顶评论区的一条留言。
     *
     * <p>「贴主」指帖子作者，置顶对象可以是别人发的评论；每个帖子最多一条置顶，
     * 所以先清掉该帖的全部置顶，需要时再置顶目标评论，重复调用不会出现多条置顶。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentVO pinComment(Long userId, Long commentId, boolean pinned) {
        PostComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == null || comment.getStatus() != 1) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        TreeholePost post = requirePost(comment.getPostId());
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.POST_NOT_OWNED);
        }
        if (pinned) {
            LocalDateTime now = LocalDateTime.now();
            commentMapper.update(null, new LambdaUpdateWrapper<PostComment>()
                    .eq(PostComment::getPostId, post.getId())
                    .isNotNull(PostComment::getPinnedAt)
                    .set(PostComment::getPinnedAt, null));
            commentMapper.update(null, new LambdaUpdateWrapper<PostComment>()
                    .eq(PostComment::getId, commentId)
                    .set(PostComment::getPinnedAt, now));
            comment.setPinnedAt(now);
        } else {
            commentMapper.update(null, new LambdaUpdateWrapper<PostComment>()
                    .eq(PostComment::getId, commentId)
                    .set(PostComment::getPinnedAt, null));
            comment.setPinnedAt(null);
        }
        PostComment parent = comment.getParentId() == null ? null : commentMapper.selectById(comment.getParentId());
        CommentVO vo = CommentVO.from(comment, nickname(comment.getUserId()),
                comment.getUserId().equals(userId),
                parent == null ? null : nickname(parent.getUserId()));
        vo.setCanPin(true);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> like(Long userId, Long id) {
        TreeholePost post = requirePost(id);
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
                notificationService.onLike(userId, post.getUserId(), id, post.getContent());
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

    public PageResult<CommentVO> comments(Long viewerId, Long id, long page, long size) {
        TreeholePost post = requirePost(id);
        boolean viewerIsAuthor = viewerId != null && viewerId.equals(post.getUserId());
        Page<PostComment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, id)
                        .eq(PostComment::getStatus, 1)
                        // 置顶的留言排最前：MySQL 的 DESC 排序里 NULL 排最后，
                        // 所以未置顶的评论会自动落到第二排序键（发言时间）
                        .orderByDesc(PostComment::getPinnedAt)
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
                    CommentVO vo = CommentVO.from(c, nicknames.getOrDefault(c.getUserId(), "用户"),
                            viewerId != null && viewerId.equals(c.getUserId()), replyTo);
                    // 只有贴主能置顶评论，前端据此决定要不要显示「置顶」按钮
                    vo.setCanPin(viewerIsAuthor);
                    return vo;
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
        TreeholePost post = requirePost(id);
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
        CommentVO vo = CommentVO.from(comment, nickname(userId), true,
                parent == null ? null : nickname(parent.getUserId()));
        // 贴主自己留言时同样可以顺手置顶
        vo.setCanPin(post.getUserId().equals(userId));
        notificationService.onComment(userId, post.getUserId(), id, req.getContent());
        // 回复别人的评论时单独再通知一次被回复的人；被回复人就是贴主时不重复打扰
        if (parent != null && !parent.getUserId().equals(post.getUserId())) {
            notificationService.onCommentReply(userId, parent.getUserId(), id, req.getContent());
        }
        return vo;
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
