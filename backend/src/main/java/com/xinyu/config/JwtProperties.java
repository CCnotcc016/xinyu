// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
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
