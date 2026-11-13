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
    /** 有效期，单位毫秒 */
    private long expiration = 7 * 24 * 60 * 60 * 1000L;
}
