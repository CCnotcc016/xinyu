// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图形验证码
 */
@Data
@AllArgsConstructor
public class CaptchaVO {

    /** 验证码 id，提交时原样带回 */
    private String captchaId;
    /** PNG 图片的 data URL，前端直接塞进 <img :src> */
    private String image;
    /** 有效期（秒） */
    private int expireSeconds;
    /**
     * 仅本地开发 / 自动化测试会带上答案（xinyu.captcha.echo-code=true）。
     * 生产环境该字段恒为 null。
     */
    private String code;
}
