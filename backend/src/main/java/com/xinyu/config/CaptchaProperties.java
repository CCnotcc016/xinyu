package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.captcha")
public class CaptchaProperties {

    /** 有效期（秒） */
    private int expireSeconds = 120;

    /** 图片宽高与可读性相关，这里固定尺寸即可 */
    private int width = 120;
    private int height = 40;

    /**
     * 是否在接口响应里回显答案。
     * 只有本地开发 / 自动化测试可以打开，生产环境必须为 false。
     */
    private boolean echoCode = false;
}
