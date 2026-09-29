// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller.admin;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.AdminDiaryVO;
import com.xinyu.dto.CrisisAlertHandleRequest;
import com.xinyu.dto.CrisisAlertVO;
import com.xinyu.dto.ReportVO;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.TreeholePost;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.AdminService;
import com.xinyu.service.CrisisAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.listReports(page, size));
    }

    @PutMapping("/reports/{id}")
    public Result<Void> handleReport(@PathVariable Long id, @RequestParam String action) {
        adminService.handleReport(id, action);
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
}
