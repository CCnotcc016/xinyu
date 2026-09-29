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
import com.xinyu.dto.AdminDiaryVO;
import com.xinyu.dto.ReportVO;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.EmotionDailyStat;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Report;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.CrisisAlertMapper;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.EmotionDailyStatMapper;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.ReportMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final EmotionDailyStatMapper statMapper;
    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final ReportMapper reportMapper;
    private final CrisisAlertMapper crisisAlertMapper;
    private final CrisisSupportService crisisSupportService;

    public PageResult<UserVO> listUsers(long page, long size, String keyword) {
        Page<User> result = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>()
                        .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                        .orderByDesc(User::getId));
        return PageResult.of(result.getTotal(), page, size,
                result.getRecords().stream().map(UserVO::from).toList());
    }

    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (isDisabling(status)) {
            assertCanDisable(user);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /**
     * 删除用户，并级联清理其产生的全部数据：日记、日记标签、情绪统计、
     * 树洞帖子及其评论/点赞、该用户发出的评论与点赞、相关举报记录。
     * 删除评论/点赞后会同步修正受影响帖子的计数。
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
        }
        if (isAdmin(user) && enabledAdminCount() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        }

        List<Long> diaryIds = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                        .eq(Diary::getUserId, id)).stream().map(Diary::getId).toList();
        if (!diaryIds.isEmpty()) {
            diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().in(DiaryTag::getDiaryId, diaryIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "DIARY").in(Report::getTargetId, diaryIds));
        }
        diaryMapper.delete(new LambdaQueryWrapper<Diary>().eq(Diary::getUserId, id));
        statMapper.delete(new LambdaQueryWrapper<EmotionDailyStat>().eq(EmotionDailyStat::getUserId, id));

        List<Long> postIds = postMapper.selectList(new LambdaQueryWrapper<TreeholePost>()
                        .eq(TreeholePost::getUserId, id)).stream().map(TreeholePost::getId).toList();
        if (!postIds.isEmpty()) {
            commentMapper.delete(new LambdaQueryWrapper<PostComment>().in(PostComment::getPostId, postIds));
            likeMapper.delete(new LambdaQueryWrapper<PostLike>().in(PostLike::getPostId, postIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "POST").in(Report::getTargetId, postIds));
        }
        postMapper.delete(new LambdaQueryWrapper<TreeholePost>().eq(TreeholePost::getUserId, id));

        // 该用户对他人帖子留下的评论 / 点赞：删掉后帖子计数要跟着减
        List<PostComment> ownComments = commentMapper.selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getUserId, id));
        if (!ownComments.isEmpty()) {
            List<Long> commentIds = ownComments.stream().map(PostComment::getId).toList();
            commentMapper.delete(new LambdaQueryWrapper<PostComment>().in(PostComment::getId, commentIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "COMMENT").in(Report::getTargetId, commentIds));
            Map<Long, Long> perPost = ownComments.stream()
                    .collect(Collectors.groupingBy(PostComment::getPostId, Collectors.counting()));
            perPost.forEach((postId, n) -> postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                    .eq(TreeholePost::getId, postId)
                    .setSql("comment_count = GREATEST(comment_count - " + n + ", 0)")));
        }

        List<PostLike> ownLikes = likeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, id));
        if (!ownLikes.isEmpty()) {
            likeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, id));
            Map<Long, Long> perPost = ownLikes.stream()
                    .collect(Collectors.groupingBy(PostLike::getPostId, Collectors.counting()));
            perPost.forEach((postId, n) -> postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                    .eq(TreeholePost::getId, postId)
                    .setSql("like_count = GREATEST(like_count - " + n + ", 0)")));
        }

        reportMapper.delete(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, id)
                .or(w -> w.eq(Report::getTargetType, "USER").eq(Report::getTargetId, id)));

        // 账号注销后，危机预警里的内容摘要与手机号也不应继续留在后台
        crisisAlertMapper.delete(new LambdaQueryWrapper<CrisisAlert>().eq(CrisisAlert::getUserId, id));

        userMapper.deleteById(id);
    }

    private boolean isDisabling(Integer status) {
        return status != null && status == 0;
    }

    private boolean isAdmin(User user) {
        return "ADMIN".equals(user.getRole());
    }

    /** 禁用前校验：不能禁用自己，也不能让系统一个可用管理员都不剩 */
    private void assertCanDisable(User target) {
        if (target.getId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        // 已是禁用状态时无需再校验，否则重复禁用会误报「最后一个管理员」
        boolean alreadyDisabled = Integer.valueOf(0).equals(target.getStatus());
        if (isAdmin(target) && !alreadyDisabled && enabledAdminCount() <= 1) {
            throw new BusinessException(ErrorCode.LAST_ADMIN);
        }
    }

    private long enabledAdminCount() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "ADMIN")
                .eq(User::getStatus, 1));
    }

    /**
     * 日记列表：userId 为空表示查看全部用户，传入后只看该账号的日记
     */
    public PageResult<AdminDiaryVO> listDiaries(long page, long size, Long userId) {
        Page<Diary> result = diaryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Diary>()
                        .eq(userId != null, Diary::getUserId, userId)
                        .orderByDesc(Diary::getCreatedAt));
        List<Diary> records = result.getRecords();
        Map<Long, User> owners = records.isEmpty() ? Map.of() : userMapper.selectBatchIds(
                        records.stream().map(Diary::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<AdminDiaryVO> vos = records.stream()
                .map(diary -> {
                    User owner = owners.get(diary.getUserId());
                    String nickname = owner == null || !StringUtils.hasText(owner.getNickname())
                            ? "用户" : owner.getNickname();
                    return AdminDiaryVO.from(diary, owner == null ? null : owner.getUsername(),
                            nickname, crisisSupportService.containsCrisis(diary.getContent()));
                })
                .toList();
        return PageResult.of(result.getTotal(), page, size, vos);
    }

    @Transactional
    public void deleteDiary(Long id) {
        diaryMapper.deleteById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
    }

    public PageResult<TreeholePost> listPosts(long page, long size, Integer status) {
        Page<TreeholePost> result = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TreeholePost>()
                        .eq(status != null, TreeholePost::getStatus, status)
                        .orderByDesc(TreeholePost::getCreatedAt));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public void updatePostStatus(Long id, Integer status) {
        TreeholePost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        post.setStatus(status);
        postMapper.updateById(post);
    }

    /**
     * 彻底删除帖子：帖子的评论、点赞是它的从属数据，一并清掉。
     * 指向它的举报与危机预警故意保留 —— 举报页会把目标显示为「(已删除)」，
     * 预警页存的是内容摘要和手机号，删帖后仍要能联系到当事人。
     */
    @Transactional
    public void deletePost(Long id) {
        if (postMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        commentMapper.delete(new LambdaQueryWrapper<PostComment>().eq(PostComment::getPostId, id));
        likeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, id));
        postMapper.deleteById(id);
    }

    public PageResult<PostComment> listComments(long page, long size) {
        Page<PostComment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PostComment>().orderByDesc(PostComment::getCreatedAt));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public void updateCommentStatus(Long id, Integer status) {
        PostComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        comment.setStatus(status);
        commentMapper.updateById(comment);
    }

    public PageResult<ReportVO> listReports(long page, long size) {
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>().orderByDesc(Report::getCreatedAt));
        List<Report> reports = result.getRecords();
        // 举报人和目标内容都批量取，避免每条举报查 2 次库
        Map<Long, String> reporterNames = reporterNames(reports.stream()
                .map(Report::getReporterId).distinct().toList());
        Map<String, String> targetContents = targetContents(reports);
        List<ReportVO> records = reports.stream()
                .map(r -> ReportVO.from(r,
                        reporterNames.getOrDefault(r.getReporterId(), "未知用户"),
                        targetContents.getOrDefault(key(r.getTargetType(), r.getTargetId()),
                                knownType(r.getTargetType()) ? "(已删除)" : "")))
                .toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    @Transactional
    public void handleReport(Long id, String action) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        String status = action == null ? "" : action.toUpperCase();
        if (!List.of("PENDING", "HANDLED", "DISMISSED").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        report.setStatus(status);
        reportMapper.updateById(report);
        if ("HANDLED".equals(status)) {
            downTarget(report);
        }
    }

    private void downTarget(Report report) {
        switch (report.getTargetType()) {
            case "POST" -> {
                TreeholePost post = postMapper.selectById(report.getTargetId());
                if (post != null) {
                    post.setStatus(0);
                    postMapper.updateById(post);
                }
            }
            case "COMMENT" -> {
                PostComment comment = commentMapper.selectById(report.getTargetId());
                if (comment != null) {
                    comment.setStatus(0);
                    commentMapper.updateById(comment);
                }
            }
            case "USER" -> {
                User user = userMapper.selectById(report.getTargetId());
                if (user != null && !Integer.valueOf(0).equals(user.getStatus())) {
                    assertCanDisable(user);
                    user.setStatus(0);
                    userMapper.updateById(user);
                }
            }
            default -> {
            }
        }
    }

    private Map<Long, String> reporterNames(List<Long> reporterIds) {
        if (reporterIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(reporterIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));
    }

    /** 按目标类型分组批量取内容；单类查询异常时降级为空，不让整页举报列表失败 */
    private Map<String, String> targetContents(List<Report> reports) {
        Map<String, String> contents = new HashMap<>();
        Map<String, List<Long>> idsByType = reports.stream()
                .filter(r -> r.getTargetType() != null && r.getTargetId() != null)
                .collect(Collectors.groupingBy(Report::getTargetType,
                        Collectors.mapping(Report::getTargetId, Collectors.toList())));
        try {
            List<Long> postIds = idsByType.getOrDefault("POST", List.of());
            if (!postIds.isEmpty()) {
                postMapper.selectBatchIds(postIds)
                        .forEach(p -> contents.put(key("POST", p.getId()), p.getContent()));
            }
            List<Long> commentIds = idsByType.getOrDefault("COMMENT", List.of());
            if (!commentIds.isEmpty()) {
                commentMapper.selectBatchIds(commentIds)
                        .forEach(c -> contents.put(key("COMMENT", c.getId()), c.getContent()));
            }
            List<Long> userIds = idsByType.getOrDefault("USER", List.of());
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds)
                        .forEach(u -> contents.put(key("USER", u.getId()), u.getUsername()));
            }
        } catch (Exception e) {
            log.warn("批量加载举报目标内容失败", e);
        }
        return contents;
    }

    private static String key(String type, Long id) {
        return type + "#" + id;
    }

    private static boolean knownType(String type) {
        return "POST".equals(type) || "COMMENT".equals(type) || "USER".equals(type);
    }
}
