package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.SupportMessageRequest;
import com.xinyu.dto.SupportMessageVO;
import com.xinyu.dto.SupportThreadVO;
import com.xinyu.dto.SupportUnreadVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧「联系管理员/客服」接口
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /** 打开与客服的会话（会把未读清零） */
    @GetMapping("/thread")
    public Result<SupportThreadVO> thread() {
        return Result.ok(supportService.userThread(SecurityUtil.getCurrentUserId()));
    }

    @PostMapping("/messages")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<SupportMessageVO> send(@Valid @RequestBody SupportMessageRequest req) {
        return Result.ok(supportService.userSend(SecurityUtil.getCurrentUserId(), req.getContent()));
    }

    /** 首页未读提示：客服回复了几条 */
    @GetMapping("/unread-count")
    public Result<SupportUnreadVO> unreadCount() {
        return Result.ok(supportService.userUnread(SecurityUtil.getCurrentUserId()));
    }
}
