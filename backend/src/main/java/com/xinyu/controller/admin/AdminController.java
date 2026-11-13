package com.xinyu.controller.admin;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.AdminDiaryVO;
import com.xinyu.dto.CrisisAlertHandleRequest;
import com.xinyu.dto.CrisisAlertVO;
import com.xinyu.dto.CrisisFollowUpRequest;
import com.xinyu.dto.ReportVO;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.SensitiveWord;
import com.xinyu.entity.TreeholePost;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.AdminService;
import com.xinyu.service.CrisisAlertService;
import com.xinyu.service.SensitiveWordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台管理接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final CrisisAlertService crisisAlertService;
    private final SensitiveWordService sensitiveWordService;

    @GetMapping("/users")
    public Result<PageResult<UserVO>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateUserStatus(id, status);
        return Result.ok();
    }

    /** 解除登录锁定（连续密码错误触发的临时锁定） */
    @PostMapping("/users/{id}/unlock")
    public Result<Void> unlockUser(@PathVariable Long id) {
        adminService.unlockUser(id);
        return Result.ok();
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.ok();
    }

    @GetMapping("/diaries")
    public Result<PageResult<AdminDiaryVO>> diaries(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long userId) {
        return Result.ok(adminService.listDiaries(page, size, userId));
    }

    /**
     * 查看日记原文：必须带理由，服务端会写审计日志。
     * 列表接口默认不返回正文，避免后台成规模浏览用户私密内容。
     */
    @PostMapping("/diaries/{id}/reveal")
    public Result<AdminDiaryVO> revealDiary(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return Result.ok(adminService.revealDiary(id, reason));
    }

    @DeleteMapping("/diaries/{id}")
    public Result<Void> deleteDiary(@PathVariable Long id) {
        adminService.deleteDiary(id);
        return Result.ok();
    }

    @GetMapping("/posts")
    public Result<PageResult<TreeholePost>> posts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.listPosts(page, size, status));
    }

    @PutMapping("/posts/{id}/status")
    public Result<Void> updatePostStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updatePostStatus(id, status);
        return Result.ok();
    }

    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        adminService.deletePost(id);
        return Result.ok();
    }

    @GetMapping("/comments")
    public Result<PageResult<PostComment>> comments(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.listComments(page, size));
    }

    @PutMapping("/comments/{id}/status")
    public Result<Void> updateCommentStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.updateCommentStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/reports")
    public Result<PageResult<ReportVO>> reports(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(adminService.listReports(page, size, status));
    }

    /** 后台红点：待处理的举报数（首页弹窗与「举报」tab 角标用） */
    @GetMapping("/reports/pending-count")
    public Result<Map<String, Long>> reportPendingCount() {
        return Result.ok(Map.of("count", adminService.reportPendingCount()));
    }

    /** 处理举报；remark 会随「举报回执」通知一起发给举报人 */
    @PutMapping("/reports/{id}")
    public Result<Void> handleReport(@PathVariable Long id,
                                     @RequestParam String action,
                                     @RequestParam(required = false) String remark) {
        adminService.handleReport(id, action, remark);
        return Result.ok();
    }

    // ---------- 危机预警 ----------

    @GetMapping("/crisis-alerts")
    public Result<PageResult<CrisisAlertVO>> crisisAlerts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(crisisAlertService.list(status, page, size));
    }

    /** 后台红点：待跟进的预警数 */
    @GetMapping("/crisis-alerts/pending-count")
    public Result<Map<String, Long>> crisisPendingCount() {
        return Result.ok(Map.of("count", crisisAlertService.pendingCount()));
    }

    @PutMapping("/crisis-alerts/{id}")
    public Result<Void> handleCrisisAlert(@PathVariable Long id,
                                          @Valid @RequestBody CrisisAlertHandleRequest req) {
        crisisAlertService.handle(SecurityUtil.getCurrentUserId(), id, req);
        return Result.ok();
    }

    /** 回访：记录「已联系、状态如何」，只对已处理 / 已忽略的预警开放 */
    @PutMapping("/crisis-alerts/{id}/follow-up")
    public Result<Void> followUpCrisisAlert(@PathVariable Long id,
                                            @Valid @RequestBody CrisisFollowUpRequest req) {
        crisisAlertService.followUp(SecurityUtil.getCurrentUserId(), id, req);
        return Result.ok();
    }

    // ---------- 敏感词 ----------

    @GetMapping("/sensitive-words")
    public Result<PageResult<SensitiveWord>> sensitiveWords(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(sensitiveWordService.list(page, size, keyword));
    }

    @PostMapping("/sensitive-words")
    public Result<Void> addSensitiveWord(@RequestParam String word) {
        sensitiveWordService.add(word);
        return Result.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    public Result<Void> deleteSensitiveWord(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return Result.ok();
    }
}
