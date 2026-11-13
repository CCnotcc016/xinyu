package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送短信验证码
 *
 * <p>短信接口必须带图形验证码，用来挡住脚本刷短信。</p>
 */
@Data
public class SmsCodeRequest {

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请先完成人机验证")
    private String captchaId;

    @NotBlank(message = "请填写图形验证码")
    private String captchaCode;
}
