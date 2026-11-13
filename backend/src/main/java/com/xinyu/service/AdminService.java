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
import com.xinyu.entity.AdminAuditLog;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.EmotionDailyStat;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Report;
import com.xinyu.entity.SupportConversation;
import com.xinyu.entity.SupportMessage;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.AdminAuditLogMapper;
import com.xinyu.mapper.CrisisAlertMapper;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.EmotionDailyStatMapper;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.ReportMapper;
import com.xinyu.mapper.SupportConversationMapper;
import com.xinyu.mapper.SupportMessageMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final SupportConversationMapper conversationMapper;
    private final SupportMessageMapper messageMapper;
    private final CrisisSupportService crisisSupportService;
    private final AccountPurgeService accountPurgeService;
    private final NotificationService notificationService;
    private final AdminAuditLogMapper auditLogMapper;

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

    /** 解除登录锁定：清空失败次数与锁定时间（用户被锁时后台手动放行） */
    public void unlockUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getFailedAttempts, 0)
                .set(User::getLockedUntil, null));
        log.info("管理员解除了用户 {} 的登录锁定", id);
    }

    /**
     * 删除用户：级联清理见 {@link AccountPurgeService}（后台删除与用户自助注销共用一套逻辑）。
     * 这里只保留管理端的保护规则：不能删自己、不能删掉最后一个管理员。
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
        accountPurgeService.purge(id);
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
                    // 列表只给元数据：正文 / AI 回复要单独申请查看（见 revealDiary）
                    return AdminDiaryVO.fromMeta(diary, owner == null ? null : owner.getUsername(),
                            nickname, crisisSupportService.containsCrisis(diary.getContent()));
                })
                .toList();
        return PageResult.of(result.getTotal(), page, size, vos);
    }

    /**
     * 查看日记原文：必须写明理由，并写一条审计日志。
     *
     * <p>后台默认只看到元数据。确实需要看正文（例如危机预警跟进、举报核实）时，
     * 走这个接口把「谁在什么时候看了谁的哪条日记、为什么」记下来。</p>
     */
    @Transactional
    public AdminDiaryVO revealDiary(Long id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "请填写查看正文的理由（至少 5 个字）");
        }
        Diary diary = diaryMapper.selectById(id);
        if (diary == null) {
            throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
        }
        writeAuditStrict(AdminAuditLog.ACTION_REVEAL_DIARY, "DIARY", id, diary.getUserId(), why);
        User owner = userMapper.selectById(diary.getUserId());
        String nickname = owner == null || !StringUtils.hasText(owner.getNickname())
                ? "用户" : owner.getNickname();
        return AdminDiaryVO.from(diary, owner == null ? null : owner.getUsername(), nickname,
                crisisSupportService.containsCrisis(diary.getContent()));
    }

    @Transactional
    public void deleteDiary(Long id) {
        Diary diary = diaryMapper.selectById(id);
        diaryMapper.deleteById(id);
        diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().eq(DiaryTag::getDiaryId, id));
        // 用户删了日记，预警里就不该再留着这段正文（否则这段隐私就删不干净了）
        clearAlertContent(CrisisAlert.TARGET_DIARY, id);
        if (diary != null) {
            writeAudit(AdminAuditLog.ACTION_DELETE_DIARY, "DIARY", id, diary.getUserId(), "后台删除日记");
        }
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
     * 指向它的举报故意保留 —— 举报页会把目标显示为「(已删除)」，处理链路仍然完整。
     * 危机预警保留记录但清空正文摘要：既不影响后续跟进，也不留存正文内容。
     */
    @Transactional
    public void deletePost(Long id) {
        if (postMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        TreeholePost post = postMapper.selectById(id);
        commentMapper.delete(new LambdaQueryWrapper<PostComment>().eq(PostComment::getPostId, id));
        likeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, id));
        postMapper.deleteById(id);
        clearAlertContent(CrisisAlert.TARGET_POST, id);
        writeAudit(AdminAuditLog.ACTION_DELETE_POST, "POST", id,
                post == null ? null : post.getUserId(), "后台删除帖子");
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

    public PageResult<ReportVO> listReports(long page, long size, String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>()
                        .eq(normalized != null, Report::getStatus, normalized)
                        .orderByDesc(Report::getCreatedAt));
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

    /** 后台红点：待处理的举报数 */
    public long reportPendingCount() {
        return reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, "PENDING"));
    }

    @Transactional
    public void handleReport(Long id, String action, String remark) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        String status = action == null ? "" : action.toUpperCase();
        if (!List.of("PENDING", "HANDLED", "DISMISSED").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String before = report.getStatus();
        report.setStatus(status);
        if (remark != null && !remark.isBlank()) {
            report.setRemark(remark.length() > 255 ? remark.substring(0, 255) : remark);
        }
        reportMapper.updateById(report);
        if ("HANDLED".equals(status)) {
            downTarget(report);
        }
        // 状态真的变了才发回执，重复点「处理」不会刷屏
        if (!status.equals(before)) {
            notificationService.onReportResult(report.getReporterId(), report.getId(), status, remark);
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

    /**
     * 清空某条目标对应预警里的正文摘要（保留记录，跟进链路不断）。
     */
    private void clearAlertContent(String targetType, Long targetId) {
        try {
            alertContentUpdate(targetType, targetId);
        } catch (Exception e) {
            log.warn("清理危机预警摘要失败: {}", e.getMessage());
        }
    }

    private void alertContentUpdate(String targetType, Long targetId) {
        crisisAlertMapper.update(null, new LambdaUpdateWrapper<CrisisAlert>()
                .eq(CrisisAlert::getTargetType, targetType)
                .eq(CrisisAlert::getTargetId, targetId)
                .set(CrisisAlert::getContent, null));
    }

    /** 删除类操作的审计：写失败只告警，不能让删除本身回滚 */
    private void writeAudit(String action, String targetType, Long targetId, Long targetUserId, String reason) {
        try {
            insertAudit(action, targetType, targetId, targetUserId, reason);
        } catch (Exception e) {
            log.warn("写审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 查看私密正文的审计：写失败就要中止本次查看。
     * 「每一次查看都留痕」是隐私政策里写死的承诺，没有痕迹的查看等于没承诺。
     */
    private void writeAuditStrict(String action, String targetType, Long targetId, Long targetUserId, String reason) {
        try {
            insertAudit(action, targetType, targetId, targetUserId, reason);
        } catch (Exception e) {
            log.error("写审计日志失败，已拒绝本次查看: {}", e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "审计日志写入失败，暂时无法查看正文");
        }
    }

    private void insertAudit(String action, String targetType, Long targetId, Long targetUserId, String reason) {
        AdminAuditLog logRow = new AdminAuditLog();
        logRow.setAdminId(SecurityUtil.getCurrentUserId());
        logRow.setAction(action);
        logRow.setTargetType(targetType);
        logRow.setTargetId(targetId);
        logRow.setTargetUserId(targetUserId);
        logRow.setReason(reason);
        logRow.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(logRow);
    }

    private static String key(String type, Long id) {
        return type + "#" + id;
    }

    private static boolean knownType(String type) {
        return "POST".equals(type) || "COMMENT".equals(type) || "USER".equals(type);
    }
}
