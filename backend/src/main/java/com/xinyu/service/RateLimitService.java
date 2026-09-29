// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.xinyu.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的简单限流（Redis 不可用时放行，避免影响主流程）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties rateLimitProperties;

    /** 通用计数限流 */
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        try {
            String fullKey = "rl:" + key;
            Long count = stringRedisTemplate.opsForValue().increment(fullKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(fullKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            log.warn("限流检查失败（Redis 不可用），放行: {}", e.getMessage());
            return true;
        }
    }

    /** 检查 AI 分析日配额 */
    public boolean checkAiDaily(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return tryAcquire("ai:" + userId + ":" + today, rateLimitProperties.getAiDailyLimit(), 86400L);
    }

    /** 检查发帖频率 */
    public boolean checkPostRate(Long userId) {
        return tryAcquire("post:" + userId, rateLimitProperties.getPostPerMinute(), 60L);
    }
}
