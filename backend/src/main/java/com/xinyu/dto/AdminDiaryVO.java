// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import com.xinyu.entity.Diary;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台「日记管理」列表项：在日记本身的基础上补上作者信息，
 * 管理员选了具体账号后，可以直接看出这条日记是谁写的。
 */
@Data
public class AdminDiaryVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String content;
    private Integer emotionScore;
    private String emotionLabel;
    private String weather;
    private String scene;
    private String privacy;
    private String aiReply;
    private String aiStatus;
    /** 内容命中自残 / 自杀关键词，后台详情里给出提醒 */
    private Boolean crisisSupport;
    private LocalDateTime createdAt;

    public static AdminDiaryVO from(Diary diary, String username, String nickname, boolean crisisSupport) {
        AdminDiaryVO vo = new AdminDiaryVO();
        vo.id = diary.getId();
        vo.userId = diary.getUserId();
        vo.username = username;
        vo.nickname = nickname;
        vo.content = diary.getContent();
        vo.emotionScore = diary.getEmotionScore();
        vo.emotionLabel = diary.getEmotionLabel();
        vo.weather = diary.getWeather();
        vo.scene = diary.getScene();
        vo.privacy = diary.getPrivacy();
        vo.aiReply = diary.getAiReply();
        vo.aiStatus = diary.getAiStatus();
        vo.crisisSupport = crisisSupport;
        vo.createdAt = diary.getCreatedAt();
        return vo;
    }
}
