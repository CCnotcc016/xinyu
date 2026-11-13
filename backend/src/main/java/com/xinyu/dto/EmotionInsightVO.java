package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 情绪洞察：天气 / 场景 / 标签 / 星期 与情绪分的交叉统计
 */
@Data
public class EmotionInsightVO {

    private Integer diaryCount;
    private List<Group> weather;
    private List<Group> scene;
    private List<Group> tags;
    private List<Group> weekday;
    private List<String> highlights;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String key;
        private double avgScore;
        private int count;
    }
}
