package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.NotificationVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内通知接口（点赞 / 评论 / 好友 / 私聊 / 客服回复）
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("!hasRole('STAFF')")
    public Result<PageResult<NotificationVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(notificationService.list(SecurityUtil.getCurrentUserId(), page, size));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount(SecurityUtil.getCurrentUserId())));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> read(@PathVariable Long id) {
        notificationService.markRead(SecurityUtil.getCurrentUserId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> readAll() {
        notificationService.markAllRead(SecurityUtil.getCurrentUserId());
        return Result.ok();
    }
}
