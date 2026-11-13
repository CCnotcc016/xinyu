package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流与登录防护配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.rate-limit")
public class RateLimitProperties {

    /** 每人每天 AI 分析次数上限 */
    private int aiDailyLimit = 50;
    /** 每分钟发帖上限 */
    private int postPerMinute = 3;

    /** 普通账号连续登录失败多少次后锁定 */
    private int loginMaxAttempts = 5;
    /** 普通账号锁定时长（分钟） */
    private int loginLockMinutes = 15;
    /** 管理员 / 客服连续登录失败多少次后锁定：权限越大，容忍度越低 */
    private int adminLoginMaxAttempts = 3;
    /** 管理员 / 客服锁定时长（分钟） */
    private int adminLoginLockMinutes = 30;
    /** 同一 IP 在窗口期内允许的登录失败次数，防撞库与密码喷洒 */
    private int loginIpMaxAttempts = 20;
    /** 上面这个窗口的长度（秒） */
    private int loginIpWindowSeconds = 600;
}
