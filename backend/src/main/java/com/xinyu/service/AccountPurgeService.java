package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xinyu.entity.ChatMessage;
import com.xinyu.entity.CrisisAlert;
import com.xinyu.entity.Diary;
import com.xinyu.entity.DiaryTag;
import com.xinyu.entity.EmotionDailyStat;
import com.xinyu.entity.EmotionWeeklyReport;
import com.xinyu.entity.Friend;
import com.xinyu.entity.FriendRequest;
import com.xinyu.entity.Notification;
import com.xinyu.entity.PostComment;
import com.xinyu.entity.PostLike;
import com.xinyu.entity.Report;
import com.xinyu.entity.SupportConversation;
import com.xinyu.entity.SupportMessage;
import com.xinyu.entity.TreeholePost;
import com.xinyu.entity.User;
import com.xinyu.mapper.ChatMessageMapper;
import com.xinyu.mapper.CrisisAlertMapper;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.DiaryTagMapper;
import com.xinyu.mapper.EmotionDailyStatMapper;
import com.xinyu.mapper.EmotionWeeklyReportMapper;
import com.xinyu.mapper.FriendMapper;
import com.xinyu.mapper.FriendRequestMapper;
import com.xinyu.mapper.NotificationMapper;
import com.xinyu.mapper.PostCommentMapper;
import com.xinyu.mapper.PostLikeMapper;
import com.xinyu.mapper.ReportMapper;
import com.xinyu.mapper.SupportConversationMapper;
import com.xinyu.mapper.SupportMessageMapper;
import com.xinyu.mapper.TreeholePostMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账号数据清理：后台删除用户、用户自助注销都走这里。
 *
 * <p>顺序不能乱：先删从属数据（日记标签 / 评论 / 点赞），再删主体，
 * 最后删用户行 —— 中途失败时时间会整体回滚，不会留下「用户没了但内容还在」的孤儿数据。</p>
 *
 * <p>注销是不可撤销的：日记（含回收站）、树洞、评论、点赞、私聊、好友、通知、举报、
 * 危机预警记录都会被物理删除，之后任何方式都找不回来。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPurgeService {

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final EmotionDailyStatMapper statMapper;
    private final EmotionWeeklyReportMapper weeklyReportMapper;
    private final TreeholePostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostLikeMapper likeMapper;
    private final ReportMapper reportMapper;
    private final CrisisAlertMapper crisisAlertMapper;
    private final SupportConversationMapper conversationMapper;
    private final SupportMessageMapper messageMapper;
    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final NotificationMapper notificationMapper;

    /**
     * 彻底删除一个账号及其全部内容。
     *
     * @return 是否真的删掉了（用户不存在时返回 false）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean purge(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }

        // 1) 日记
        List<Long> diaryIds = diaryMapper.selectList(new LambdaQueryWrapper<Diary>()
                        .eq(Diary::getUserId, userId)).stream().map(Diary::getId).toList();
        if (!diaryIds.isEmpty()) {
            diaryTagMapper.delete(new LambdaQueryWrapper<DiaryTag>().in(DiaryTag::getDiaryId, diaryIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "DIARY").in(Report::getTargetId, diaryIds));
        }
        diaryMapper.delete(new LambdaQueryWrapper<Diary>().eq(Diary::getUserId, userId));
        statMapper.delete(new LambdaQueryWrapper<EmotionDailyStat>().eq(EmotionDailyStat::getUserId, userId));
        weeklyReportMapper.delete(new LambdaQueryWrapper<EmotionWeeklyReport>()
                .eq(EmotionWeeklyReport::getUserId, userId));

        // 2) 自己的树洞帖子（连带评论、点赞、举报目标）
        List<Long> postIds = postMapper.selectList(new LambdaQueryWrapper<TreeholePost>()
                        .eq(TreeholePost::getUserId, userId)).stream().map(TreeholePost::getId).toList();
        if (!postIds.isEmpty()) {
            commentMapper.delete(new LambdaQueryWrapper<PostComment>().in(PostComment::getPostId, postIds));
            likeMapper.delete(new LambdaQueryWrapper<PostLike>().in(PostLike::getPostId, postIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "POST").in(Report::getTargetId, postIds));
        }
        postMapper.delete(new LambdaQueryWrapper<TreeholePost>().eq(TreeholePost::getUserId, userId));

        // 3) 对他人帖子留下的评论 / 点赞：删掉后帖子计数要跟着减
        List<PostComment> ownComments = commentMapper.selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getUserId, userId));
        if (!ownComments.isEmpty()) {
            List<Long> commentIds = ownComments.stream().map(PostComment::getId).toList();
            commentMapper.delete(new LambdaQueryWrapper<PostComment>().in(PostComment::getId, commentIds));
            reportMapper.delete(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "COMMENT").in(Report::getTargetId, commentIds));
            Map<Long, Long> perPost = ownComments.stream()
                    .collect(Collectors.groupingBy(PostComment::getPostId, Collectors.counting()));
            perPost.forEach((postId, n) -> postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                    .eq(TreeholePost::getId, postId)
                    .setSql("comment_count = GREATEST(comment_count - " + n + ", 0)")));
        }

        List<PostLike> ownLikes = likeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId));
        if (!ownLikes.isEmpty()) {
            likeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, userId));
            Map<Long, Long> perPost = ownLikes.stream()
                    .collect(Collectors.groupingBy(PostLike::getPostId, Collectors.counting()));
            perPost.forEach((postId, n) -> postMapper.update(null, new LambdaUpdateWrapper<TreeholePost>()
                    .eq(TreeholePost::getId, postId)
                    .setSql("like_count = GREATEST(like_count - " + n + ", 0)")));
        }

        // 4) 举报：自己发起的 + 指向自己的
        reportMapper.delete(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .or(w -> w.eq(Report::getTargetType, "USER").eq(Report::getTargetId, userId)));

        // 5) 危机预警里的内容摘要与手机号也不应继续留在后台
        crisisAlertMapper.delete(new LambdaQueryWrapper<CrisisAlert>().eq(CrisisAlert::getUserId, userId));

        // 6) 好友 / 好友申请 / 私聊 / 通知
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId).or().eq(Friend::getFriendId, userId));
        friendRequestMapper.delete(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, userId).or().eq(FriendRequest::getToUserId, userId));
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFromUserId, userId).or().eq(ChatMessage::getToUserId, userId));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));

        // 7) 客服会话：用户自己的会话整条删掉（否则客服工作台会留下一个找不到用户的幽灵会话）；
        //    被删客服（或管理员）接手的会话回到「待接手」，结单人也要清空，避免显示已注销的昵称
        SupportConversation ownConversation = conversationMapper.selectOne(new LambdaQueryWrapper<SupportConversation>()
                .eq(SupportConversation::getUserId, userId)
                .last("LIMIT 1"));
        if (ownConversation != null) {
            messageMapper.delete(new LambdaQueryWrapper<SupportMessage>()
                    .eq(SupportMessage::getConversationId, ownConversation.getId()));
            conversationMapper.deleteById(ownConversation.getId());
        }
        conversationMapper.update(null, new LambdaUpdateWrapper<SupportConversation>()
                .eq(SupportConversation::getAssigneeId, userId)
                .set(SupportConversation::getAssigneeId, null));
        conversationMapper.update(null, new LambdaUpdateWrapper<SupportConversation>()
                .eq(SupportConversation::getClosedById, userId)
                .set(SupportConversation::getClosedById, null));

        userMapper.deleteById(userId);
        log.info("账号数据已清理: userId={} username={}", userId, user.getUsername());
        return true;
    }
}
