// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定 / 更换手机号。
 *
 * <p>新号码必须通过短信验证码验证；如果账号已经绑过手机号（即「换号」场景），
 * 还要再输一次当前密码，防止 token 被盗后直接改掉联系方式。</p>
 */
@Data
public class ChangePhoneRequest {

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请填写验证码")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为 6 位数字")
    private String smsCode;

    /** 换号时必填（首次绑定可留空） */
    private String password;
}
