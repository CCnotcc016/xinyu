package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.FriendRequestCreateRequest;
import com.xinyu.dto.FriendRequestHandleRequest;
import com.xinyu.dto.FriendRequestVO;
import com.xinyu.dto.FriendVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 好友接口。
 *
 * <p>客服账号（STAFF）不参与社交：它们只负责在客服工作台里回复用户，
 * 所以整个控制器都对 STAFF 关闭。</p>
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('STAFF')")
public class FriendController {

    private final FriendService friendService;

    /** 我的好友（含最后一条消息、未读数，聊天页直接用） */
    @GetMapping
    public Result<List<FriendVO>> friends() {
        return Result.ok(friendService.friends(SecurityUtil.getCurrentUserId()));
    }

    /** direction = RECEIVED（默认，收到的）/ SENT（我发出的） */
    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> requests(
            @RequestParam(defaultValue = "RECEIVED") String direction) {
        return Result.ok(friendService.requests(SecurityUtil.getCurrentUserId(), direction));
    }

    /** 「我的」页面上的红点 */
    @GetMapping("/requests/pending-count")
    public Result<Map<String, Object>> pendingCount() {
        long count = friendService.pendingCount(SecurityUtil.getCurrentUserId());
        return Result.ok(Map.of("count", count));
    }

    @PostMapping("/requests")
    public Result<Void> sendRequest(@Valid @RequestBody FriendRequestCreateRequest req) {
        friendService.sendRequest(SecurityUtil.getCurrentUserId(), req.getToUserId(), req.getMessage());
        return Result.ok();
    }

    /** 同意 / 拒绝别人发来的申请 */
    @PutMapping("/requests/{id}")
    public Result<Void> handleRequest(@PathVariable Long id,
                                      @Valid @RequestBody FriendRequestHandleRequest req) {
        friendService.handle(SecurityUtil.getCurrentUserId(), id, req.getAction());
        return Result.ok();
    }

    /** 删除好友（聊天记录保留，只是不能再发新消息） */
    @DeleteMapping("/{friendId}")
    public Result<Void> remove(@PathVariable Long friendId) {
        friendService.removeFriend(SecurityUtil.getCurrentUserId(), friendId);
        return Result.ok();
    }
}
