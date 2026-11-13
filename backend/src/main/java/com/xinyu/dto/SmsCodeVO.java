package com.xinyu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 短信验证码发送结果
 */
@Data
@AllArgsConstructor
public class SmsCodeVO {

    /** 验证码有效期（秒） */
    private int expireSeconds;
    /** 下次可重发的间隔（秒） */
    private int resendAfter;
    /**
     * 仅本地开发 / 自动化测试会带上验证码（xinyu.sms.echo-code=true）。
     * 线上接好短信服务商后该字段恒为 null。
     */
    private String code;
}
