// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信验证码配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.sms")
public class SmsProperties {

    /** 验证码位数 */
    private int codeLength = 6;

    /** 有效期（秒） */
    private int expireSeconds = 300;

    /** 同一手机号两次发送的最小间隔（秒） */
    private int resendIntervalSeconds = 60;

    /** 同一手机号 24 小时发送上限 */
    private int dailyLimit = 10;

    /**
     * 是否在接口响应里回显验证码。
     * 本机 / 演示环境没有接短信服务商时用它把流程跑通，
     * 生产环境必须为 false（application-prod.yml 已固化）。
     */
    private boolean echoCode = false;
}
