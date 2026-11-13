package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.PageResult;
import com.xinyu.dto.SupportConversationVO;
import com.xinyu.dto.SupportMessageVO;
import com.xinyu.dto.SupportThreadVO;
import com.xinyu.dto.SupportUnreadVO;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.SupportConversation;
import com.xinyu.entity.SupportMessage;
import com.xinyu.entity.User;
import com.xinyu.mapper.SupportConversationMapper;
import com.xinyu.mapper.SupportMessageMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 「联系管理员/客服」会话服务。
 *
 * <p>规则（和前台提示保持一致）：</p>
 * <ul>
 *   <li>一个用户只有一条会话，不会因为换客服而断线；</li>
 *   <li>会话初始无接手人，所有客服/管理员都能看到并抢单；</li>
 *   <li>客服首次回复即接手，之后只有该客服和 ADMIN 能看到这条会话；</li>
 *   <li>客服会话不做敏感词拦截 —— 用户来求助的场景（「我被诈骗了」）本身就可能踩词库，
 *       拦下来反而会挡住真正需要帮助的人。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    /** 单次打开会话返回的最大消息数，按 id 倒序取最近 N 条后再正序输出 */
    private static final int THREAD_LIMIT = 100;
    /** 会话列表里显示的摘要长度 */
    private static final int PREVIEW_LENGTH = 60;
    /** 每分钟每人的消息上限，只防刷屏，不参与业务判断 */
    private static final int MESSAGES_PER_MINUTE = 10;

    /** 结单时留给用户的系统提示 */
    private static final String CLOSE_HINT = "本次咨询已结束。如果还有问题，再发一条消息就可以重新接入客服。";

    private final SupportConversationMapper conversationMapper;
    private final SupportMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final RateLimitService rateLimitService;
    private final NotificationService notificationService;

    public boolean isSupportRole(String role) {
        return SupportMessage.ROLE_ADMIN.equalsIgnoreCase(role) || SupportMessage.ROLE_STAFF.equalsIgnoreCase(role);
    }

    private boolean isAdmin(String role) {
        return SupportMessage.ROLE_ADMIN.equalsIgnoreCase(role);
    }

    // ---------------- 用户侧 ----------------

    /** 用户打开会话：返回最近 100 条消息，并把未读清零 */
    @Transactional(rollbackFor = Exception.class)
    public SupportThreadVO userThread(Long userId) {
        SupportConversation conversation = findByUser(userId);
        if (conversation == null) {
            return SupportThreadVO.empty();
        }
        if (conversation.getUserUnread() != null && conversation.getUserUnread() > 0) {
            conversationMapper.update(null, new LambdaUpdateWrapper<SupportConversation>()
                    .eq(SupportConversation::getId, conversation.getId())
                    .set(SupportConversation::getUserUnread, 0));
            conversation.setUserUnread(0);
        }
        return thread(conversation, true, null, null);
    }

    /** 用户发消息（没有会话时自动建一条；上一轮已结单则开启新的一轮） */
    @Transactional(rollbackFor = Exception.class)
    public SupportMessageVO userSend(Long userId, String content) {
        String text = normalize(content);
        if (!rateLimitService.tryAcquire("support:" + userId, MESSAGES_PER_MINUTE, 60L)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        SupportConversation conversation = findOrCreate(userId);
        // 结单之后用户再来问：从这条消息开始算新一轮，接手人清空（客服/管理员重新抢单），
        // 本轮起点定在这条消息前一条，接手人就看不到结单之前的记录了。
        boolean reopening = SupportConversation.STATUS_CLOSED.equals(conversation.getStatus());
        SupportMessageVO vo = append(conversation, userId, SupportMessage.ROLE_USER, text);
        LambdaUpdateWrapper<SupportConversation> update = new LambdaUpdateWrapper<SupportConversation>()
                .eq(SupportConversation::getId, conversation.getId())
                .set(SupportConversation::getLastMessage, preview(text))
                .set(SupportConversation::getLastMessageAt, LocalDateTime.now())
                .set(SupportConversation::getUserUnread, 0)
                .setSql("staff_unread = staff_unread + 1");
        if (reopening) {
            update.set(SupportConversation::getStatus, SupportConversation.STATUS_OPEN)
                    .set(SupportConversation::getClosedAt, null)
                    .set(SupportConversation::getClosedById, null)
                    .set(SupportConversation::getAssigneeId, null)
                    .set(SupportConversation::getSessionStartId, vo.getId() - 1);
        }
        conversationMapper.update(null, update);
        return vo;
    }

    /** 用户侧首页红点：客服回复了几条 */
    public SupportUnreadVO userUnread(Long userId) {
        SupportConversation conversation = findByUser(userId);
        if (conversation == null || conversation.getUserUnread() == null || conversation.getUserUnread() == 0) {
            return SupportUnreadVO.none();
        }
        SupportMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<SupportMessage>()
                .eq(SupportMessage::getConversationId, conversation.getId())
                // 只认客服/管理员的回复：结单留下的 SYSTEM 提示不该算成「客服回复了你」
                .in(SupportMessage::getSenderRole, SupportMessage.ROLE_ADMIN, SupportMessage.ROLE_STAFF)
                .orderByDesc(SupportMessage::getId)
                .last("LIMIT 1"));
        SupportUnreadVO vo = new SupportUnreadVO();
        vo.setCount(conversation.getUserUnread());
        vo.setConversationId(conversation.getId());
        if (latest != null) {
            vo.setLatestMessage(preview(latest.getContent()));
            vo.setLatestSenderRole(latest.getSenderRole());
            vo.setLatestSenderName(nickname(latest.getSenderId()));
            vo.setLatestAt(latest.getCreatedAt());
        }
        return vo;
    }

    // ---------------- 客服 / 管理员侧 ----------------

    /** 客服工作台列表：ADMIN 看全部，STAFF 只看自己接手的 + 待接手的 */
    public PageResult<SupportConversationVO> listConversations(String role, Long staffId, long page, long size) {
        Page<SupportConversation> result = conversationMapper.selectPage(new Page<>(page, size),
                scopeWrapper(role, staffId).orderByDesc(SupportConversation::getLastMessageAt)
                        .orderByDesc(SupportConversation::getId));
        List<SupportConversation> records = result.getRecords();
        Map<Long, User> users = users(records.stream().map(SupportConversation::getUserId).toList());
        Map<Long, User> assignees = users(records.stream().map(SupportConversation::getAssigneeId)
                .filter(Objects::nonNull).toList());
        List<SupportConversationVO> voRecords = records.stream()
                .map(c -> SupportConversationVO.from(c,
                        nickname(users.get(c.getUserId())),
                        displayUsername(users.get(c.getUserId())),
                        c.getAssigneeId() == null ? null : nickname(assignees.get(c.getAssigneeId())),
                        c.getAssigneeId() != null && c.getAssigneeId().equals(staffId)))
                .toList();
        return PageResult.of(result.getTotal(), page, size, voRecords);
    }

    /** 客服打开某个用户的会话：校验可见性，未读清零 */
    @Transactional(rollbackFor = Exception.class)
    public SupportThreadVO staffThread(String role, Long staffId, Long conversationId) {
        SupportConversation conversation = requireVisible(role, staffId, conversationId);
        if (conversation.getStaffUnread() != null && conversation.getStaffUnread() > 0) {
            conversationMapper.update(null, new LambdaUpdateWrapper<SupportConversation>()
                    .eq(SupportConversation::getId, conversation.getId())
                    .set(SupportConversation::getStaffUnread, 0));
            conversation.setStaffUnread(0);
        }
        return thread(conversation, false, role, staffId);
    }

    /**
     * 结单：把这轮咨询标记为已处理完。
     *
     * <p>只有接手的客服（或管理员）能结单，避免别人替你把会话关掉。
     * 结单会给用户留一条系统提示，用户下次再发消息就自动开启新一轮，
     * 接手人清空、并且看不到结单之前的记录。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SupportThreadVO closeConversation(String role, Long staffId, Long conversationId) {
        SupportConversation conversation = requireVisible(role, staffId, conversationId);
        if (SupportConversation.STATUS_CLOSED.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPORT_CONVERSATION_CLOSED);
        }
        if (!isAdmin(role) && !staffId.equals(conversation.getAssigneeId())) {
            throw new BusinessException(ErrorCode.SUPPORT_CLOSE_NOT_ASSIGNEE);
        }
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.update(null, new LambdaUpdateWrapper<SupportConversation>()
                .eq(SupportConversation::getId, conversation.getId())
                .set(SupportConversation::getStatus, SupportConversation.STATUS_CLOSED)
                .set(SupportConversation::getClosedAt, now)
                .set(SupportConversation::getClosedById, staffId)
                .set(SupportConversation::getStaffUnread, 0));
        conversation.setStatus(SupportConversation.STATUS_CLOSED);
        conversation.setClosedAt(now);
        conversation.setClosedById(staffId);
        conversation.setStaffUnread(0);
        insertMessage(conversation.getId(), staffId, SupportMessage.ROLE_SYSTEM, CLOSE_HINT);
        return thread(conversation, false, role, staffId);
    }

    /** 客服回复：无人接手时自动接手，之后这条会话就固定归属该客服 */
    @Transactional(rollbackFor = Exception.class)
    public SupportMessageVO staffSend(String role, Long staffId, Long conversationId, String content) {
        SupportConversation conversation = requireVisible(role, staffId, conversationId);
        if (SupportConversation.STATUS_CLOSED.equals(conversation.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPORT_CONVERSATION_CLOSED);
        }
        String text = normalize(content);
        if (!rateLimitService.tryAcquire("support-staff:" + staffId, MESSAGES_PER_MINUTE * 3, 60L)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        LambdaUpdateWrapper<SupportConversation> update = new LambdaUpdateWrapper<SupportConversation>()
                .eq(SupportConversation::getId, conversation.getId())
                .set(SupportConversation::getLastMessage, preview(text))
                .set(SupportConversation::getLastMessageAt, LocalDateTime.now())
                .set(SupportConversation::getStaffUnread, 0)
                .setSql("user_unread = user_unread + 1");
        if (conversation.getAssigneeId() == null) {
            update.set(SupportConversation::getAssigneeId, staffId);
        }
        conversationMapper.update(null, update);
        SupportMessageVO vo = SupportMessageVO.from(
                insertMessage(conversation.getId(), staffId, role, text), nickname(staffId));
        // 管理员代答同样算「客服回复」，用户的通知中心里不必区分是谁
        notificationService.onSupportReply(staffId, conversation.getUserId(), text);
        return vo;
    }

    /** 客服侧首页红点：待回复的会话数 */
    public SupportUnreadVO staffUnread(String role, Long staffId) {
        long count = conversationMapper.selectCount(scopeWrapper(role, staffId)
                .gt(SupportConversation::getStaffUnread, 0));
        if (count == 0) {
            return SupportUnreadVO.none();
        }
        SupportConversation latest = conversationMapper.selectOne(scopeWrapper(role, staffId)
                .gt(SupportConversation::getStaffUnread, 0)
                .orderByDesc(SupportConversation::getLastMessageAt)
                .orderByDesc(SupportConversation::getId)
                .last("LIMIT 1"));
        SupportUnreadVO vo = new SupportUnreadVO();
        vo.setCount(count);
        if (latest != null) {
            vo.setConversationId(latest.getId());
            vo.setLatestMessage(latest.getLastMessage());
            vo.setLatestSenderRole(SupportMessage.ROLE_USER);
            vo.setLatestSenderName(nickname(latest.getUserId()));
            vo.setLatestAt(latest.getLastMessageAt());
        }
        return vo;
    }

    // ---------------- 内部实现 ----------------

    /** 会话可见范围：ADMIN 全部；STAFF 自己接手的 + 还没人接手的 */
    private LambdaQueryWrapper<SupportConversation> scopeWrapper(String role, Long staffId) {
        LambdaQueryWrapper<SupportConversation> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin(role)) {
            wrapper.and(w -> w.eq(SupportConversation::getAssigneeId, staffId)
                    .or().isNull(SupportConversation::getAssigneeId));
        }
        return wrapper;
    }

    private SupportConversation requireVisible(String role, Long staffId, Long conversationId) {
        SupportConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.SUPPORT_CONVERSATION_NOT_FOUND);
        }
        boolean visible = isAdmin(role)
                || conversation.getAssigneeId() == null
                || conversation.getAssigneeId().equals(staffId);
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return conversation;
    }

    private SupportThreadVO thread(SupportConversation conversation, boolean userSide, String role, Long staffId) {
        SupportThreadVO vo = new SupportThreadVO();
        vo.setConversationId(conversation.getId());
        vo.setUserId(conversation.getUserId());
        User user = userMapper.selectById(conversation.getUserId());
        vo.setNickname(nickname(user));
        vo.setUsername(displayUsername(user));
        vo.setAssigneeId(conversation.getAssigneeId());
        vo.setAssigneeName(conversation.getAssigneeId() == null ? null : nickname(conversation.getAssigneeId()));
        vo.setUnread(userSide
                ? nvl(conversation.getUserUnread())
                : nvl(conversation.getStaffUnread()));
        vo.setStatus(conversation.getStatus() == null ? SupportConversation.STATUS_OPEN : conversation.getStatus());
        vo.setClosedAt(conversation.getClosedAt());
        vo.setClosedByName(conversation.getClosedById() == null ? null : nickname(conversation.getClosedById()));
        vo.setCanClose(!userSide && canClose(role, staffId, conversation));
        // 客服侧只给「本轮」的消息：结单后用户再来问会开新的一轮，
        // session_start_id 往前的记录（含上一轮的结单提示）都不再展示
        Long sessionStart = userSide ? null : conversation.getSessionStartId();
        LambdaQueryWrapper<SupportMessage> query = new LambdaQueryWrapper<SupportMessage>()
                .eq(SupportMessage::getConversationId, conversation.getId())
                .gt(sessionStart != null && sessionStart > 0, SupportMessage::getId, sessionStart)
                .orderByDesc(SupportMessage::getId);
        // 取最近 THREAD_LIMIT 条：先按 id 倒序分页，再反转成正序给前端渲染
        List<SupportMessage> messages = messageMapper.selectPage(new Page<>(1, THREAD_LIMIT), query).getRecords();
        Map<Long, String> names = nicknames(messages.stream().map(SupportMessage::getSenderId).toList());
        List<SupportMessageVO> list = new ArrayList<>(messages.stream()
                .map(m -> SupportMessageVO.from(m, names.getOrDefault(m.getSenderId(), fallbackName(m))))
                .toList());
        Collections.reverse(list);
        vo.setMessages(list);
        return vo;
    }

    /**
     * 发送者账号已被注销时的兜底昵称：按角色显示成「客服」或「用户」，
     * 总比把客服的消息标成「用户」好
     */
    private String fallbackName(SupportMessage message) {
        return SupportMessage.ROLE_STAFF.equals(message.getSenderRole())
                || SupportMessage.ROLE_ADMIN.equals(message.getSenderRole()) ? "客服" : "用户";
    }

    /** 能结单的人：接手的客服本身，或管理员（会话进行中才可结单） */
    private boolean canClose(String role, Long staffId, SupportConversation conversation) {
        String status = conversation.getStatus() == null
                ? SupportConversation.STATUS_OPEN : conversation.getStatus();
        if (!SupportConversation.STATUS_OPEN.equals(status) || role == null || staffId == null) {
            return false;
        }
        return isAdmin(role) || staffId.equals(conversation.getAssigneeId());
    }

    private SupportMessageVO append(SupportConversation conversation, Long senderId, String role, String text) {
        return SupportMessageVO.from(insertMessage(conversation.getId(), senderId, role, text), nickname(senderId));
    }

    private SupportMessage insertMessage(Long conversationId, Long senderId, String role, String text) {
        SupportMessage message = new SupportMessage();
        message.setConversationId(conversationId);
        message.setSenderId(senderId);
        message.setSenderRole(role);
        message.setContent(text);
        // 显式赋值：created_at 由数据库默认值填充，insert 后实体里还是 null，前端会显示空时间
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        return message;
    }

    private SupportConversation findByUser(Long userId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<SupportConversation>()
                .eq(SupportConversation::getUserId, userId));
    }

    private SupportConversation findOrCreate(Long userId) {
        SupportConversation existing = findByUser(userId);
        if (existing != null) {
            return existing;
        }
        SupportConversation created = new SupportConversation();
        created.setUserId(userId);
        created.setUserUnread(0);
        created.setStaffUnread(0);
        created.setStatus(SupportConversation.STATUS_OPEN);
        created.setSessionStartId(0L);
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        try {
            conversationMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发下同时首次发消息，唯一索引兜住，回读已存在的那条
            SupportConversation current = findByUser(userId);
            if (current == null) {
                throw e;
            }
            return current;
        }
    }

    private String normalize(String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return text;
    }

    private String preview(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH) + "…";
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<Long, User> users(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, String> nicknames(List<Long> ids) {
        return users(ids).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> nickname(e.getValue())));
    }

    private String nickname(Long userId) {
        return nickname(userMapper.selectById(userId));
    }

    private String nickname(User user) {
        return user == null || !StringUtils.hasText(user.getNickname()) ? "用户" : user.getNickname();
    }

    /** 账号是手机号时打码后再给客服看；普通用户名原样返回 */
    private String displayUsername(User user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return null;
        }
        String username = user.getUsername();
        return username.matches("\\d{7,}") ? UserVO.maskPhone(username) : username;
    }
}
