// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import com.xinyu.entity.Diary;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日记展示对象
 */
@Data
public class DiaryVO {

    private Long id;
    private Long userId;
    private String content;
    private Integer emotionScore;
    private String emotionLabel;
    private String weather;
    private String scene;
    private String privacy;
    private String aiReply;
    private String aiStatus;
    private List<String> emotionTags;
    private LocalDateTime createdAt;
    /** 内容命中自残 / 自杀关键词：前端据此弹关怀提示（不落库，只在当次响应里给） */
    private Boolean crisisSupport = false;

    public static DiaryVO from(Diary diary, List<String> emotionTags) {
        DiaryVO vo = new DiaryVO();
        vo.id = diary.getId();
        vo.userId = diary.getUserId();
        vo.content = diary.getContent();
        vo.emotionScore = diary.getEmotionScore();
        vo.emotionLabel = diary.getEmotionLabel();
        vo.weather = diary.getWeather();
        vo.scene = diary.getScene();
        vo.privacy = diary.getPrivacy();
        vo.aiReply = diary.getAiReply();
        vo.aiStatus = diary.getAiStatus();
        vo.emotionTags = emotionTags;
        vo.createdAt = diary.getCreatedAt();
        return vo;
    }
}
