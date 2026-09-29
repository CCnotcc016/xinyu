// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.rate-limit")
public class RateLimitProperties {

    /** 每人每天 AI 分析次数上限 */
    private int aiDailyLimit = 50;
    /** 每分钟发帖上限 */
    private int postPerMinute = 3;
}
