// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DiaryRequest {

    @NotBlank(message = "日记内容不能为空")
    @Size(max = 5000, message = "日记内容过长")
    private String content;

    /** 用户选择/补充的情绪标签 */
    private List<String> emotionTags;

    private String weather;

    private String scene;

    /** PRIVATE / PUBLIC */
    private String privacy = "PRIVATE";
}
