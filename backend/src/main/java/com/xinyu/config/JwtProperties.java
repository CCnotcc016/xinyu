package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.jwt")
public class JwtProperties {

    private String secret = "change-me-to-a-long-random-secret-key-please-32chars";
    /** 有效期，单位毫秒。默认 24 小时：token 泄漏后的攻击窗口越短越好 */
    private long expiration = 24 * 60 * 60 * 1000L;
}
