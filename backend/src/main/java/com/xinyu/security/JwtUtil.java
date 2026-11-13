package com.xinyu.security;

import com.xinyu.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String TOKEN_VERSION_CLAIM = "tv";

    private final JwtProperties jwtProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecret();
        // 占位符解析不到时 Spring 会把字面量 "${JWT_SECRET}" 原样传入，这里显式拦住并给出可操作的中文提示
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalStateException(
                    "未配置 jwt 密钥：请设置环境变量 JWT_SECRET（生成方式：openssl rand -hex 32），"
                            + "Docker 部署可写入项目根目录的 .env");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt 密钥过短：HS256 要求至少 32 字节，当前 " + keyBytes.length
                            + " 字节。请用 openssl rand -hex 32 重新生成 JWT_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 token。
     *
     * <p>带上 tokenVersion：改密码 / 换手机号 / 退出登录时数据库里 +1，
     * 之前签发的 token 立刻失效，不用再等 24 小时自然过期。</p>
     */
    public String generateToken(Long userId, String username, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /** token 里的版本号；老 token 没有这个字段时按 0 处理 */
    public int parseTokenVersion(String token) {
        Object raw = parseClaims(token).get(TOKEN_VERSION_CLAIM);
        return raw instanceof Number n ? n.intValue() : 0;
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
