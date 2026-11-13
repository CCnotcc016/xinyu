package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.PageResult;
import com.xinyu.dto.NotificationVO;
import com.xinyu.entity.Notification;
import com.xinyu.entity.User;
import com.xinyu.mapper.NotificationMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知。所有写入都吞异常：通知失败不应该影响主业务（点赞、发消息等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int CONTENT_LIMIT = 60;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public PageResult<NotificationVO> list(Long userId, long page, long size) {
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt)
                        .orderByDesc(Notification::getId));
        List<NotificationVO> records = result.getRecords().stream().map(NotificationVO::from).toList();
        return PageResult.of(result.getTotal(), page, size, records);
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    public void markRead(Long userId, Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
    }

    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    public void onLike(Long actorId, Long postAuthorId, Long postId, String excerpt) {
        push(actorId, postAuthorId, Notification.LIKE,
                nick(actorId) + " 赞了你的树洞", excerpt, Notification.TARGET_POST, postId);
    }

    public void onComment(Long actorId, Long postAuthorId, Long postId, String excerpt) {
        push(actorId, postAuthorId, Notification.COMMENT,
                nick(actorId) + " 评论了你的树洞", excerpt, Notification.TARGET_POST, postId);
    }

    public void onCommentReply(Long actorId, Long parentAuthorId, Long postId, String excerpt) {
        push(actorId, parentAuthorId, Notification.COMMENT_REPLY,
                nick(actorId) + " 回复了你的评论", excerpt, Notification.TARGET_POST, postId);
    }

    public void onFriendRequest(Long actorId, Long targetId, String message) {
        push(actorId, targetId, Notification.FRIEND_REQUEST,
                nick(actorId) + " 想加你为好友", message, Notification.TARGET_USER, actorId);
    }

    public void onFriendAccept(Long actorId, Long targetId) {
        push(actorId, targetId, Notification.FRIEND_ACCEPT,
                nick(actorId) + " 通过了你的好友申请", "现在可以开始聊天了", Notification.TARGET_USER, actorId);
    }

    public void onChat(Long actorId, Long targetId, String excerpt) {
        push(actorId, targetId, Notification.CHAT,
                nick(actorId) + " 给你发来一条消息", excerpt, Notification.TARGET_CONVERSATION, actorId);
    }

    public void onSupportReply(Long actorId, Long targetId, String excerpt) {
        push(actorId, targetId, Notification.SUPPORT,
                "客服回复了你", excerpt, Notification.TARGET_CONVERSATION, actorId);
    }

    /**
     * 举报回执。actorId 传 null：管理员和举报人可能是同一个人（自己举报自己处理），
     * 这里不该因为「自己操作」而吞掉回执。
     */
    public void onReportResult(Long reporterId, Long reportId, String status, String reason) {
        String title = switch (status == null ? "" : status) {
            case "HANDLED" -> "你的举报已处理";
            case "DISMISSED" -> "你的举报未通过";
            default -> "你的举报已恢复为待处理";
        };
        String content = "HANDLED".equals(status)
                ? "我们已下架被举报的内容，感谢你帮忙维护树洞的氛围。"
                : "DISMISSED".equals(status)
                ? "经审核未发现明显违规，内容暂不下架。"
                : "该举报已重新进入待处理队列。";
        if (reason != null && !reason.isBlank()) {
            content = content + " 理由：" + reason;
        }
        push(null, reporterId, Notification.REPORT, title, content,
                Notification.TARGET_REPORT, reportId);
    }

    public void push(Long actorId, Long userId, String type, String title,
                     String content, String targetType, Long targetId) {
        if (userId == null || userId.equals(actorId)) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(truncate(content));
            n.setTargetType(targetType);
            n.setTargetId(targetId);
            n.setIsRead(0);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("站内通知写入失败（忽略）: {}", e.getMessage());
        }
    }

    private String nick(Long userId) {
        if (userId == null) {
            return "有人";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "有人";
        }
        return user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname();
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= CONTENT_LIMIT ? flat : flat.substring(0, CONTENT_LIMIT) + "…";
    }
}
