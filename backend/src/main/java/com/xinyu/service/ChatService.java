package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.ChatMessageVO;
import com.xinyu.dto.FriendVO;
import com.xinyu.entity.ChatMessage;
import com.xinyu.entity.User;
import com.xinyu.mapper.ChatMessageMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 私聊。只有好友之间能发消息，消息是纯文本（不存图片，避免出现内容审核死角）。
 *
 * <p>没有做 WebSocket：课程设计/毕业设计级别的部署环境里，长连接要考虑 Nginx、
 * 心跳、断线重连，性价比不高。这里用「打开会话时轮询」+ 未读角标，够用且稳。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final FriendService friendService;
    private final SensitiveWordService sensitiveWordService;
    private final CrisisSupportService crisisSupportService;
    private final NotificationService notificationService;

    /** 聊天列表：好友 + 有历史记录的会话（含已不是好友但聊过的） */
    public List<FriendVO> conversations(Long me) {
        List<FriendVO> list = new ArrayList<>(friendService.friends(me));
        Map<Long, FriendVO> byUser = list.stream()
                .collect(Collectors.toMap(FriendVO::getUserId, v -> v, (a, b) -> a, HashMap::new));

        // 已解除好友但聊过的会话仍然出现在列表里，用户能翻历史（只是不能再发新消息）
        List<ChatMessage> convs = chatMessageMapper.selectConversations(me);
        Map<Long, Integer> unread = chatMessageMapper.selectUnreadByPeer(me).stream()
                .collect(Collectors.toMap(u -> u.getPeerId(), u -> u.getUnread(), (a, b) -> a));

        List<Long> unknown = convs.stream()
                .map(m -> FriendService.peerOf(m, me))
                .filter(id -> !byUser.containsKey(id))
                .toList();
        if (!unknown.isEmpty()) {
            Map<Long, User> users = userMapper.selectBatchIds(unknown).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            for (Long id : unknown) {
                User u = users.get(id);
                if (u == null) {
                    continue;
                }
                FriendVO vo = new FriendVO();
                vo.setUserId(u.getId());
                vo.setNickname(u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname());
                vo.setAvatar(u.getAvatar());
                list.add(vo);
                byUser.put(id, vo);
            }
        }

        for (ChatMessage m : convs) {
            FriendVO vo = byUser.get(FriendService.peerOf(m, me));
            if (vo == null) {
                continue;
            }
            vo.setLastMessage(m.getContent().length() > 40 ? m.getContent().substring(0, 40) + "…" : m.getContent());
            vo.setLastMessageAt(m.getCreatedAt());
            vo.setLastMessageMine(me.equals(m.getFromUserId()));
            vo.setUnread(unread.getOrDefault(vo.getUserId(), 0));
        }
        list.sort((a, b) -> {
            var ta = a.getLastMessageAt();
            var tb = b.getLastMessageAt();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        return list;
    }

    /** 底部导航的未读角标 */
    public int unreadTotal(Long me) {
        return chatMessageMapper.countUnread(me);
    }

    /**
     * 会话消息，倒序取一页后在返回前翻正（前端按时间正序展示）。
     * beforeId 传上一页最小 id 即可继续往上翻。
     */
    public PageResult<ChatMessageVO> messages(Long me, Long peerId, Long beforeId, long size) {
        if (peerId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        // 不校验好友关系：查询永远限定在 (我, 对方) 这一对上，
        // 解除了好友也要能翻看历史，只是不能再发新消息（send 里才校验）。
        long limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ChatMessage> rows = chatMessageMapper.selectPair(me, peerId, beforeId, (int) limit);
        Collections.reverse(rows);
        List<ChatMessageVO> records = rows.stream().map(m -> ChatMessageVO.from(m, me)).toList();
        return PageResult.of(chatMessageMapper.countPair(me, peerId), 1, limit, records);
    }

    /**
     * 发消息。
     *
     * <p>命中危机关键词时不写预警、不通知管理员——私聊内容不应该被第三方看到。
     * 只把 crisis 标记回给发送方的界面，由前端弹一段关怀提示。</p>
     */
    @Transactional
    public ChatMessageVO send(Long me, Long peerId, String content) {
        requireFriend(me, peerId);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (sensitiveWordService.containsSensitive(text)) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }
        ChatMessage m = new ChatMessage();
        m.setFromUserId(me);
        m.setToUserId(peerId);
        m.setContent(text);
        // 字段长度只有 1000，再兜一层，避免超长文本直接抛 SQL 异常
        if (m.getContent().length() > 1000) {
            m.setContent(m.getContent().substring(0, 1000));
        }
        // 显式给时间：列表要靠它排序，前端气泡上也要显示时间，不能等响应回来时还是 null
        m.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(m);
        ChatMessageVO vo = ChatMessageVO.from(m, me);
        vo.setCrisis(crisisSupportService.containsCrisis(text));
        notificationService.onChat(me, peerId, text);
        return vo;
    }

    /** 打开会话：把对方发来的消息标记为已读 */
    @Transactional
    public int markRead(Long me, Long peerId) {
        chatMessageMapper.markRead(me, peerId);
        return chatMessageMapper.countUnread(me);
    }

    private void requireFriend(Long me, Long peerId) {
        if (peerId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!friendService.isFriend(me, peerId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND);
        }
    }
}
