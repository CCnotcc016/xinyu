// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.config.CaptchaProperties;
import com.xinyu.dto.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：短信接口的前置人机验证。
 *
 * <p>为什么不用第三方的滑块验证：本机环境没有 Redis 也没有外网服务，
 * 自绘图片验证码零依赖、可控、够挡住脚本批量刷短信。</p>
 *
 * <p>验证码存在进程内存里（带过期时间）。多实例部署时需要换成 Redis，
 * 但同一时间只有一个实例在用，内存实现足够。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 去掉 0O1Il 这些容易看错的字符 */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final int CODE_LENGTH = 4;

    /** 过期条目靠这个上限兜底清理，避免极端情况下内存持续增长 */
    private static final int MAX_ENTRIES = 5000;

    private final CaptchaProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public CaptchaVO issue() {
        purgeExpired();
        String code = randomCode();
        String id = randomId();
        store.put(id, new Entry(code, System.currentTimeMillis() + properties.getExpireSeconds() * 1000L));
        String image = render(code);
        log.debug("生成图形验证码 id={}", id);
        return new CaptchaVO(id, image, properties.getExpireSeconds(),
                properties.isEchoCode() ? code : null);
    }

    /**
     * 校验并消费验证码。校验通过后立即失效，防止同一张图被反复使用。
     * 答案错误时不消费，用户可以重新输入。
     */
    public void verifyAndConsume(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        Entry entry = store.get(captchaId);
        if (entry == null || entry.expireAt < System.currentTimeMillis()) {
            store.remove(captchaId);
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        if (!entry.code.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException(ErrorCode.CAPTCHA_WRONG);
        }
        store.remove(captchaId);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
        if (store.size() > MAX_ENTRIES) {
            store.clear();
            log.warn("图形验证码缓存超过 {} 条，已整体清理", MAX_ENTRIES);
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private String randomId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String render(String code) {
        int w = properties.getWidth();
        int h = properties.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xF2, 0xF5, 0xF7));
            g.fillRect(0, 0, w, h);

            // 干扰线
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 5; i++) {
                g.setColor(randomColor(170, 220));
                g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
            }
            // 噪点
            for (int i = 0; i < 60; i++) {
                g.setColor(randomColor(170, 230));
                g.fillOval(random.nextInt(w), random.nextInt(h), 2, 2);
            }

            Font font = new Font(Font.SANS_SERIF, Font.BOLD, h - 14);
            int step = (w - 16) / code.length();
            for (int i = 0; i < code.length(); i++) {
                AffineTransform saved = g.getTransform();
                double angle = (random.nextDouble() - 0.5) * 0.5;
                int x = 8 + i * step;
                int y = h - 9;
                g.rotate(angle, x + step / 2.0, y - font.getSize() / 2.0);
                g.setFont(font);
                g.setColor(randomColor(40, 130));
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.setTransform(saved);
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            // 画不出图说明运行环境缺少 AWT 支持，此时宁可让接口报错也不要放行空验证码
            log.error("图形验证码生成失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private Color randomColor(int min, int max) {
        int span = max - min;
        return new Color(min + random.nextInt(span), min + random.nextInt(span), min + random.nextInt(span));
    }

    private record Entry(String code, long expireAt) {
    }
}
