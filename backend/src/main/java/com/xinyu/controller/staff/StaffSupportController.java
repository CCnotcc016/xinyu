package com.xinyu.controller.staff;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.SupportConversationVO;
import com.xinyu.dto.SupportMessageRequest;
import com.xinyu.dto.SupportMessageVO;
import com.xinyu.dto.SupportThreadVO;
import com.xinyu.dto.SupportUnreadVO;
import com.xinyu.security.LoginUser;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服工作台接口（ADMIN 与 STAFF 都能访问，STAFF 只看得到自己接手与待接手的会话）
 */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffSupportController {

    private final SupportService supportService;

    @GetMapping("/conversations")
    public Result<PageResult<SupportConversationVO>> conversations(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LoginUser me = SecurityUtil.getLoginUser();
        return Result.ok(supportService.listConversations(me.getRole(), me.getId(), page, size));
    }

    @GetMapping("/conversations/{id}")
    public Result<SupportThreadVO> thread(@PathVariable Long id) {
        LoginUser me = SecurityUtil.getLoginUser();
        return Result.ok(supportService.staffThread(me.getRole(), me.getId(), id));
    }

    /** 回复用户；会话无人接手时自动接手 */
    @PostMapping("/conversations/{id}/messages")
    public Result<SupportMessageVO> reply(@PathVariable Long id,
                                          @Valid @RequestBody SupportMessageRequest req) {
        LoginUser me = SecurityUtil.getLoginUser();
        return Result.ok(supportService.staffSend(me.getRole(), me.getId(), id, req.getContent()));
    }

    /**
     * 结单：把已经处理完的会话标记为结束。
     * 返回结单后的会话，前端直接拿去刷新（多了一条给用户的系统提示）。
     */
    @PutMapping("/conversations/{id}/close")
    public Result<SupportThreadVO> close(@PathVariable Long id) {
        LoginUser me = SecurityUtil.getLoginUser();
        return Result.ok(supportService.closeConversation(me.getRole(), me.getId(), id));
    }

    /** 工作台红点：待回复的会话数 */
    @GetMapping("/unread-count")
    public Result<SupportUnreadVO> unreadCount() {
        LoginUser me = SecurityUtil.getLoginUser();
        return Result.ok(supportService.staffUnread(me.getRole(), me.getId()));
    }
}
