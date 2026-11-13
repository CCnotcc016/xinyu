package com.xinyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 别人的「主页」上能看到什么。
 *
 * <p>刻意不放 username（登录凭据，知道用户名就多一半爆破信息）和手机号，
 * 也不暴露日记数量——日记是私密的，对外只统计树洞里公开的活跃度。</p>
 */
@Data
public class UserProfileVO {

    private Long id;
    private String nickname;
    private String avatar;
    /** 注册时间，只精确到月（前端显示「加入于 2026 年 9 月」） */
    private LocalDateTime joinedAt;
    /** 公开帖子数（不含匿名帖） */
    private Integer postCount;
    /** 公开帖子获得的点赞总数 */
    private Integer likeReceived;
    /** 写过的日记天数，只有看自己的主页时才返回 */
    private Integer diaryCount;
    /** 连续记录天数，只有看自己的主页时才返回 */
    private Integer streak;
    /** SELF / FRIEND / REQUESTING（我已申请）/ INCOMING（对方申请了我）/ STRANGER */
    private String relation;
    /** relation = INCOMING 时，待我处理的申请 ID */
    private Long pendingRequestId;
}
