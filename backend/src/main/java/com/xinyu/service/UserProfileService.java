package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.UserProfileVO;
import com.xinyu.entity.Diary;
import com.xinyu.entity.FriendRequest;
import com.xinyu.entity.User;
import com.xinyu.mapper.DiaryMapper;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 「点开某个人的名字看到的主页」。
 *
 * <p>这里只放公开信息：昵称、头像、加入时间、树洞活跃度。
 * 日记天数和连续记录只在自己看自己主页时才带出来——那是私密数据，
 * 就算只是「写了多少篇」也不该让陌生人看到。</p>
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final FriendService friendService;
    private final TreeholeService treeholeService;
    private final StatsService statsService;

    public UserProfileVO profile(Long viewerId, Long targetId) {
        User user = userMapper.selectById(targetId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setJoinedAt(user.getCreatedAt());

        int[] stats = treeholeService.publicStats(targetId);
        vo.setPostCount(stats[0]);
        vo.setLikeReceived(stats[1]);

        boolean self = viewerId != null && viewerId.equals(targetId);
        if (self) {
            // 日记总条数 + 连续记录天数，只有自己看自己主页时才返回
            vo.setDiaryCount(diaryMapper.selectCount(new LambdaQueryWrapper<Diary>()
                    .eq(Diary::getUserId, targetId)).intValue());
            vo.setStreak(statsService.calendar(targetId, 30).getStreak());
        }

        String relation = friendService.relation(viewerId, targetId);
        vo.setRelation(relation);
        if ("INCOMING".equals(relation)) {
            FriendRequest pending = friendService.pendingIncoming(viewerId, targetId);
            vo.setPendingRequestId(pending == null ? null : pending.getId());
        }
        return vo;
    }
}
