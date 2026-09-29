// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TreeholePostRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容过长")
    private String content;

    private List<String> images;

    /** 是否匿名，默认匿名 */
    private Boolean isAnonymous = true;
}
