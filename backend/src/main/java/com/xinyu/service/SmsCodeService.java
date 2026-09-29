// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.config.SmsProperties;
import com.xinyu.dto.SmsCodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码：注册 / 换手机号前必须过一次。
 *
 * <p>发短信这一侧目前是 <b>本地模拟</b>：本机没有接任何短信服务商，
 * 所以验证码只写日志。</p>
 *
 * <p>上线时把 {@link #deliver} 换成真实通道即可（阿里云 / 腾讯云短信 SDK），
 * 其余限流、过期、一次性消费的逻辑都不用动。
 * 同时务必在配置里关掉 {@code xinyu.sms.echo-code}，否则验证码会直接返回给调用方。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private final SmsProperties properties;
    private final CaptchaService captchaService;
    private final SecureRandom random = new SecureRandom();

    /** 手机号 -> 验证码 */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    /** 手机号 -> 上次发送时间戳（毫秒） */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();
    /** 手机号 -> 当天已发送次数 */
    private final Map<String, DailyCounter> dailyCounter = new ConcurrentHashMap<>();

    /**
     * 发送验证码。必须先通过图形验证码，再受「60 秒一次 + 每天 10 次」限流保护。
     */
    public SmsCodeVO send(String phone, String captchaId, String captchaCode) {
        captchaService.verifyAndConsume(captchaId, captchaCode);

        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(phone);
        if (last != null && now - last < properties.getResendIntervalSeconds() * 1000L) {
            throw new BusinessException(ErrorCode.SMS_TOO_FREQUENT);
        }

        LocalDate today = LocalDate.now();
        DailyCounter counter = dailyCounter.compute(phone, (k, old) ->
                old == null || !old.day.equals(today) ? new DailyCounter(today, 0) : old);
        synchronized (counter) {
            if (counter.count >= properties.getDailyLimit()) {
                throw new BusinessException(ErrorCode.SMS_LIMIT_EXCEEDED);
            }
            counter.count++;
        }

        String code = randomCode();
        store.put(phone, new Entry(code, now + properties.getExpireSeconds() * 1000L));
        lastSentAt.put(phone, now);
        deliver(phone, code);
        purgeExpired(now);

        return new SmsCodeVO(properties.getExpireSeconds(), properties.getResendIntervalSeconds(),
                properties.isEchoCode() ? code : null);
    }

    /**
     * 校验并消费验证码（一次性）。
     */
    public void verifyAndConsume(String phone, String code) {
        Entry entry = phone == null ? null : store.get(phone);
        if (entry == null || entry.expireAt < System.currentTimeMillis()
                || code == null || !entry.code.equals(code.trim())) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
        store.remove(phone);
    }

    /**
     * 真正把验证码发给用户。当前是模拟实现，只写日志。
     *
     * <p>接入真实短信服务时替换这里：调用 SDK 发送模板短信，
     * 并且不要再把验证码写进日志（日志会被采集，等于泄漏验证码）。</p>
     */
    private void deliver(String phone, String code) {
        log.info("【模拟短信】向 {} 发送验证码 {}，{} 秒内有效",
                mask(phone), code, properties.getExpireSeconds());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(properties.getCodeLength());
        for (int i = 0; i < properties.getCodeLength(); i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private void purgeExpired(long now) {
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
        if (store.size() > 5000) {
            store.clear();
            log.warn("短信验证码缓存异常增长，已整体清理");
        }
    }

    /** 日志里不写完整手机号 */
    private String mask(String phone) {
        return phone.length() < 7 ? "****" : phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private record Entry(String code, long expireAt) {
    }

    private static final class DailyCounter {
        private final LocalDate day;
        private int count;

        private DailyCounter(LocalDate day, int count) {
            this.day = day;
            this.count = count;
        }
    }
}
