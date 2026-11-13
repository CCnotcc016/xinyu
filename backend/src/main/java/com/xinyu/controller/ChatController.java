package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.ChatMessageRequest;
import com.xinyu.dto.ChatMessageVO;
import com.xinyu.dto.FriendVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 一对一私聊接口（同样对客服账号关闭）。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('STAFF')")
public class ChatController {

    private final ChatService chatService;

    /** 聊天列表：好友 + 聊过天的人，按最后一条消息时间倒序 */
    @GetMapping("/conversations")
    public Result<List<FriendVO>> conversations() {
        return Result.ok(chatService.conversations(SecurityUtil.getCurrentUserId()));
    }

    /** 底部导航的未读角标 */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok(Map.of("count", chatService.unreadTotal(SecurityUtil.getCurrentUserId())));
    }

    /** 聊天记录：beforeId 传上一页最小 id，往前翻 */
    @GetMapping("/{peerId}/messages")
    public Result<PageResult<ChatMessageVO>> messages(
            @PathVariable Long peerId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") long size) {
        return Result.ok(chatService.messages(SecurityUtil.getCurrentUserId(), peerId, beforeId, size));
    }

    @PostMapping("/{peerId}/messages")
    public Result<ChatMessageVO> send(@PathVariable Long peerId,
                                      @Valid @RequestBody ChatMessageRequest req) {
        return Result.ok(chatService.send(SecurityUtil.getCurrentUserId(), peerId, req.getContent()));
    }

    /** 打开会话时调用：把对方发来的消息标记为已读 */
    @PutMapping("/{peerId}/read")
    public Result<Map<String, Object>> read(@PathVariable Long peerId) {
        int unread = chatService.markRead(SecurityUtil.getCurrentUserId(), peerId);
        return Result.ok(Map.of("count", unread));
    }
}
