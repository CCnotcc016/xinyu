package com.xinyu.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 取客户端真实 IP。
 *
 * <p>线上经由 Nginx 反代，{@code getRemoteAddr()} 只会拿到 127.0.0.1，
 * 所以优先读代理写入的 X-Forwarded-For / X-Real-IP。
 * 这两个头是客户端可以伪造的，因此只用它做「限流分桶」，
 * 不用于鉴权或审计定责。</p>
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // 可能是 "客户端, 代理1, 代理2"，第一个才是真实发起方
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
