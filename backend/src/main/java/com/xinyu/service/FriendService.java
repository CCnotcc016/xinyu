package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.FriendRequestVO;
import com.xinyu.dto.FriendVO;
import com.xinyu.entity.ChatMessage;
import com.xinyu.entity.Friend;
import com.xinyu.entity.FriendRequest;
import com.xinyu.entity.User;
import com.xinyu.mapper.ChatMessageMapper;
import com.xinyu.mapper.FriendMapper;
import com.xinyu.mapper.FriendRequestMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友关系：申请、同意/拒绝、好友列表。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>只有好友才能私聊。陌生人不能直接发消息，从机制上挡住骚扰。</li>
 *   <li>成为好友必须对方明确同意，没有「关注即好友」这类隐性关系。</li>
 *   <li>互相申请时直接算成为好友，避免两个人互发申请卡住。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    /** 一次最多处理多少条申请/好友，防止列表被刷爆 */
    private static final int MAX_LIST = 200;

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    /** 好友列表（含最后一条消息与未读数，直接给聊天页用） */
    public List<FriendVO> friends(Long me) {
        List<Friend> rows = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, me)
                .orderByDesc(Friend::getCreatedAt)
                .last("LIMIT " + MAX_LIST));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = loadUsers(rows.stream().map(Friend::getFriendId).toList());
        Map<Long, ChatMessage> lastByPeer = lastByPeer(me);
        Map<Long, Integer> unread = unreadByPeer(me);

        List<FriendVO> list = new ArrayList<>();
        for (Friend f : rows) {
            User u = users.get(f.getFriendId());
            if (u == null) {
                continue;
            }
            ChatMessage last = lastByPeer.get(u.getId());
            FriendVO vo = new FriendVO();
            vo.setUserId(u.getId());
            vo.setNickname(displayName(u));
            vo.setAvatar(u.getAvatar());
            vo.setLastMessage(last == null ? null : summary(last.getContent()));
            vo.setLastMessageAt(last == null ? null : last.getCreatedAt());
            vo.setLastMessageMine(last != null && me.equals(last.getFromUserId()));
            vo.setUnread(unread.getOrDefault(u.getId(), 0));
            vo.setFriendsSince(f.getCreatedAt());
            list.add(vo);
        }
        // 聊过的排前面（按最后一条消息时间倒序），没聊过的沉到后面（按加好友时间倒序）
        list.sort(Comparator.comparing(
                (FriendVO v) -> v.getLastMessageAt() == null ? NO_MESSAGE : v.getLastMessageAt()).reversed());
        return list;
    }

    /** 没有消息的好友在排序里当成「很久以前」，自然排到最后 */
    private static final LocalDateTime NO_MESSAGE = LocalDateTime.of(1970, 1, 1, 0, 0);

    private String summary(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 40 ? content.substring(0, 40) + "…" : content;
    }

    public boolean isFriend(Long me, Long other) {
        if (me == null || other == null || me.equals(other)) {
            return false;
        }
        return friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, me)
                .eq(Friend::getFriendId, other)) > 0;
    }

    /** 好友数量，个人主页上展示 */
    public long countFriends(Long userId) {
        return friendMapper.selectCount(new LambdaQueryWrapper<Friend>().eq(Friend::getUserId, userId));
    }

    /**
     * 我与他人的关系，决定个人主页上显示哪个按钮。
     */
    public String relation(Long me, Long other) {
        if (me == null || other == null) {
            return "STRANGER";
        }
        if (me.equals(other)) {
            return "SELF";
        }
        if (isFriend(me, other)) {
            return "FRIEND";
        }
        FriendRequest incoming = pendingBetween(me, other, true);
        if (incoming != null) {
            return "INCOMING";
        }
        FriendRequest outgoing = pendingBetween(me, other, false);
        if (outgoing != null) {
            return "REQUESTING";
        }
        return "STRANGER";
    }

    /** 对方发给我、还没处理的申请（主页上直接给「同意」按钮用） */
    public FriendRequest pendingIncoming(Long me, Long other) {
        return pendingBetween(me, other, true);
    }

    private FriendRequest pendingBetween(Long me, Long other, boolean incoming) {
        Long from = incoming ? other : me;
        Long to = incoming ? me : other;
        return friendRequestMapper.selectOne(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, from)
                .eq(FriendRequest::getToUserId, to)
                .eq(FriendRequest::getStatus, FriendRequest.PENDING)
                .orderByDesc(FriendRequest::getId)
                .last("LIMIT 1"));
    }

    /** 发出好友申请 */
    @Transactional
    public void sendRequest(Long me, Long toUserId, String message) {
        if (me.equals(toUserId)) {
            throw new BusinessException(ErrorCode.FRIEND_SELF);
        }
        User target = userMapper.selectById(toUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (isFriend(me, toUserId)) {
            throw new BusinessException(ErrorCode.FRIEND_EXISTS);
        }
        // 对方已经申请过我：直接互相成为好友，不用再多点一步
        FriendRequest incoming = pendingBetween(me, toUserId, true);
        if (incoming != null) {
            accept(me, incoming);
            return;
        }
        if (pendingBetween(me, toUserId, false) != null) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_EXISTS);
        }

        FriendRequest req = new FriendRequest();
        req.setFromUserId(me);
        req.setToUserId(toUserId);
        req.setMessage(message == null || message.isBlank() ? null : message.trim());
        req.setStatus(FriendRequest.PENDING);
        friendRequestMapper.insert(req);
        notificationService.onFriendRequest(me, toUserId, req.getMessage());
    }

    /** 收到的 / 发出的申请 */
    public List<FriendRequestVO> requests(Long me, String direction) {
        boolean received = !"SENT".equalsIgnoreCase(direction);
        LambdaQueryWrapper<FriendRequest> w = new LambdaQueryWrapper<FriendRequest>()
                .orderByDesc(FriendRequest::getId)
                .last("LIMIT " + MAX_LIST);
        if (received) {
            w.eq(FriendRequest::getToUserId, me);
        } else {
            w.eq(FriendRequest::getFromUserId, me);
        }
        List<FriendRequest> rows = friendRequestMapper.selectList(w);
        Map<Long, User> users = loadUsers(rows.stream()
                .map(r -> received ? r.getFromUserId() : r.getToUserId()).toList());
        List<FriendRequestVO> list = new ArrayList<>();
        for (FriendRequest r : rows) {
            Long otherId = received ? r.getFromUserId() : r.getToUserId();
            User u = users.get(otherId);
            FriendRequestVO vo = new FriendRequestVO();
            vo.setId(r.getId());
            vo.setUserId(otherId);
            vo.setNickname(u == null ? "已注销用户" : displayName(u));
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setMessage(r.getMessage());
            vo.setStatus(r.getStatus());
            vo.setDirection(received ? "RECEIVED" : "SENT");
            vo.setCreatedAt(r.getCreatedAt());
            vo.setHandledAt(r.getHandledAt());
            list.add(vo);
        }
        return list;
    }

    /** 待我处理的申请条数（「我的」页面的红点） */
    public long pendingCount(Long me) {
        return friendRequestMapper.selectCount(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getToUserId, me)
                .eq(FriendRequest::getStatus, FriendRequest.PENDING));
    }

    /** 同意或拒绝 */
    @Transactional
    public void handle(Long me, Long requestId, String action) {
        FriendRequest req = friendRequestMapper.selectById(requestId);
        if (req == null) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!me.equals(req.getToUserId())) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_TARGET);
        }
        if (!FriendRequest.PENDING.equals(req.getStatus())) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if ("REJECT".equalsIgnoreCase(action)) {
            req.setStatus(FriendRequest.REJECTED);
            req.setHandledAt(LocalDateTime.now());
            friendRequestMapper.updateById(req);
            return;
        }
        accept(me, req);
    }

    /** 成为好友：双向各写一行，重复调用不会产生重复数据 */
    private void accept(Long me, FriendRequest req) {
        Long other = req.getFromUserId();
        req.setStatus(FriendRequest.ACCEPTED);
        req.setHandledAt(LocalDateTime.now());
        friendRequestMapper.updateById(req);
        link(me, other);
        link(other, me);
        notificationService.onFriendAccept(me, other);
    }

    private void link(Long userId, Long friendId) {
        long exists = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId));
        if (exists > 0) {
            return;
        }
        Friend f = new Friend();
        f.setUserId(userId);
        f.setFriendId(friendId);
        friendMapper.insert(f);
    }

    /** 删除好友：两边的关系一起删掉 */
    @Transactional
    public void removeFriend(Long me, Long friendId) {
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, me).eq(Friend::getFriendId, friendId));
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, friendId).eq(Friend::getFriendId, me));
    }

    private Map<Long, ChatMessage> lastByPeer(Long me) {
        Map<Long, ChatMessage> map = new HashMap<>();
        chatMessageMapper.selectConversations(me).forEach(m -> map.put(peerOf(m, me), m));
        return map;
    }

    static Long peerOf(ChatMessage m, Long me) {
        return me.equals(m.getFromUserId()) ? m.getToUserId() : m.getFromUserId();
    }

    private Map<Long, Integer> unreadByPeer(Long me) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        chatMessageMapper.selectUnreadByPeer(me).forEach(u -> map.put(u.getPeerId(), u.getUnread()));
        return map;
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>();
        userMapper.selectBatchIds(distinct).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private String displayName(User u) {
        return u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname();
    }
}
