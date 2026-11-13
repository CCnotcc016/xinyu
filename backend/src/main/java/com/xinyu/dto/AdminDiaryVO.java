package com.xinyu.dto;

import com.xinyu.entity.Diary;
import com.xinyu.service.CrisisSupportService;
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
        // 命中危机词但 AI 回复还是空的（老数据 / 用户没点生成）时，兜底显示干预文案，
        // 否则后台详情里只会看到「（还没有 AI 回复）」，等于干预内容没出现
        if (crisisSupport && (vo.aiReply == null || vo.aiReply.isBlank())) {
            vo.aiReply = CrisisSupportService.CRISIS_REPLY;
            vo.aiStatus = "CRISIS";
        }
        vo.createdAt = diary.getCreatedAt();
        return vo;
    }
}
