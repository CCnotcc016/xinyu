package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.DeleteAccountRequest;
import com.xinyu.entity.ChatMessage;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.Friend;
import com.xinyu.entity.Notification;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Report;
import com.xinyu.entity.Tag;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.ChatMessageMapper;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.FriendMapper;
import com.xinyu.mapper.NotificationMapper;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.ReportMapper;
import com.xinyu.mapper.TagMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 个人信息导出与账号注销。
 *
 * <p>导出的是「账号里的全部内容」：日记（含 AI 回复）、树洞、评论、点赞、
 * 举报、好友、私聊、通知数量。密码等登录凭据不导出。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataService {

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;
    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final ReportMapper reportMapper;
    private final FriendMapper friendMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final NotificationMapper notificationMapper;
    private final AccountPurgeService accountPurgeService;
    private final PasswordEncoder passwordEncoder;

    /** 导出为可直接写成 JSON 文件的结构；不包含密码哈希等敏感字段 */
    public Map<String, Object> export(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "心语");
        data.put("exportedAt", LocalDateTime.now().toString());
        data.put("note", "这是你在心语里的全部个人数据；密码等登录凭据不会导出。");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("nickname", user.getNickname());
        profile.put("phone", user.getPhone());
        profile.put("avatar", user.getAvatar());
        profile.put("role", user.getRole());
        profile.put("createdAt", user.getCreatedAt());
        data.put("profile", profile);

        List<Diary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                .eq(Diary::getUserId, userId).orderByAsc(Diary::getCreatedAt));
        Map<Long, List<String>> tags = tagNames(diaries);
        List<Map<String, Object>> diaryList = new ArrayList<>();
        for (Diary d : diaries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("content", d.getContent());
            item.put("emotionScore", d.getEmotionScore());
            item.put("emotionLabel", d.getEmotionLabel());
            item.put("emotionTags", tags.getOrDefault(d.getId(), List.of()));
            item.put("weather", d.getWeather());
            item.put("scene", d.getScene());
            item.put("privacy", d.getPrivacy());
            item.put("aiReply", d.getAiReply());
            item.put("aiStatus", d.getAiStatus());
            item.put("createdAt", d.getCreatedAt());
            diaryList.add(item);
        }
        data.put("diaries", diaryList);

        List<TreeholePost> posts = postMapper.selectList(new LambdaQueryWrapper<TreeholePost>()
                .eq(TreeholePost::getUserId, userId).orderByAsc(TreeholePost::getCreatedAt));
        data.put("treeholePosts", posts.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("content", p.getContent());
            item.put("images", p.getImages());
            item.put("isAnonymous", p.getIsAnonymous());
            item.put("likeCount", p.getLikeCount());
            item.put("commentCount", p.getCommentCount());
            item.put("status", p.getStatus());
            item.put("createdAt", p.getCreatedAt());
            return item;
        }).toList());

        List<PostComment> comments = commentMapper.selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getUserId, userId).orderByAsc(PostComment::getCreatedAt));
        data.put("comments", comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("postId", c.getPostId());
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).toList());

        List<PostLike> likes = likeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId));
        data.put("likes", likes.stream().map(l -> Map.of(
                "postId", l.getPostId(),
                "createdAt", l.getCreatedAt() == null ? "" : l.getCreatedAt().toString())).toList());

        List<Report> reports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId).orderByAsc(Report::getCreatedAt));
        data.put("reports", reports.stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());
            item.put("targetType", r.getTargetType());
            item.put("targetId", r.getTargetId());
            item.put("reason", r.getReason());
            item.put("status", r.getStatus());
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).toList());

        List<Friend> friends = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId));
        Map<Long, User> friendUsers = friends.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(friends.stream().map(Friend::getFriendId).toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        data.put("friends", friends.stream().map(f -> {
            User fu = friendUsers.get(f.getFriendId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", f.getFriendId());
            item.put("nickname", fu == null ? "(已注销)" : fu.getNickname());
            item.put("createdAt", f.getCreatedAt());
            return item;
        }).toList());

        List<ChatMessage> chats = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFromUserId, userId).or().eq(ChatMessage::getToUserId, userId)
                .orderByAsc(ChatMessage::getCreatedAt));
        data.put("chatMessages", chats.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fromMe", c.getFromUserId() != null && c.getFromUserId().equals(userId));
            item.put("otherUserId", c.getFromUserId() != null && c.getFromUserId().equals(userId)
                    ? c.getToUserId() : c.getFromUserId());
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).toList());

        data.put("notificationCount", notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId)));
        return data;
    }

    /**
     * 自助注销：必须验证密码，删除后无法恢复。
     *
     * <p>管理员账号不允许自助注销 —— 后台的删除用户会校验「不能删掉最后一个管理员」，
     * 自己注销绕过了这道校验，会让系统失去管理员。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, DeleteAccountRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        accountPurgeService.purge(userId);
        log.info("用户自助注销: userId={} username={}", userId, user.getUsername());
    }

    /** 日记 ID -> 情绪标签名 */
    private Map<Long, List<String>> tagNames(List<Diary> diaries) {
        if (diaries.isEmpty()) {
            return Map.of();
        }
        List<DiaryTag> links = diaryTagMapper.selectList(new LambdaQueryWrapper<DiaryTag>()
                .in(DiaryTag::getDiaryId, diaries.stream().map(Diary::getId).toList()));
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = tagMapper.selectBatchIds(links.stream()
                        .map(DiaryTag::getTagId).distinct().toList()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        Map<Long, List<String>> grouped = new HashMap<>();
        for (DiaryTag link : links) {
            String name = names.get(link.getTagId());
            if (name != null) {
                grouped.computeIfAbsent(link.getDiaryId(), k -> new ArrayList<>()).add(name);
            }
        }
        return grouped;
    }
}
