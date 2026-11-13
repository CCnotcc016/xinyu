package com.xinyu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 认证过滤器：从请求头 Authorization 中解析令牌。
 *
 * <p>三处安全约束：</p>
 * <ol>
 *   <li><b>版本号校验</b>：token 里的 tv 必须等于库里的 token_version，
 *       否则视为已吊销（改密码 / 换手机号 / 退出登录后旧 token 立刻作废）。</li>
 *   <li><b>查询参数 token 只给 SSE</b>：EventSource 不能自定义请求头，所以流式接口要支持
 *       {@code ?token=}；但以前所有接口都接受它，URL 会进 Nginx access log、浏览器历史、
 *       Referer，等于把令牌到处撒。现在只在 /stream 结尾的接口上生效。</li>
 *   <li><b>首次登录强制改密</b>：管理员初始化账号带 must_change_password=1，
 *       改密前除「读自己信息 / 改密码 / 同意协议」外的接口一律 403。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 强制改密期间仍然允许的接口：否则前端连"我是谁"都拿不到，会卡死在空白页 */
    private static final Set<String> MUST_CHANGE_PASSWORD_ALLOWED = Set.of(
            "/api/user/me",
            "/api/user/password",
            "/api/user/agreement");

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            LoginUser loginUser = authenticate(token);
            if (loginUser != null) {
                if (loginUser.isMustChangePassword() && !mustChangePasswordAllowed(request)) {
                    writePasswordChangeRequired(response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 验签 + 版本号比对 + 账号可用性校验，任何一项不过都返回 null（当作未登录） */
    private LoginUser authenticate(String token) {
        try {
            if (!jwtUtil.isValid(token)) {
                return null;
            }
            Long userId = jwtUtil.parseUserId(token);
            UserDetails userDetails = userDetailsService.loadByUserId(userId);
            if (!(userDetails instanceof LoginUser loginUser) || !loginUser.isEnabled()) {
                return null;
            }
            if (loginUser.getTokenVersion() != jwtUtil.parseTokenVersion(token)) {
                log.debug("token 已被吊销: userId={}", userId);
                return null;
            }
            return loginUser;
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 只有流式接口才接受查询参数（EventSource 无法自定义请求头）
        String uri = request.getRequestURI();
        if (uri != null && uri.endsWith("/stream")) {
            return request.getParameter("token");
        }
        return null;
    }

    private boolean mustChangePasswordAllowed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null
                || uri.startsWith("/api/auth/")
                || MUST_CHANGE_PASSWORD_ALLOWED.contains(uri);
    }

    private void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ErrorCode.MUST_CHANGE_PASSWORD.getCode(),
                        ErrorCode.MUST_CHANGE_PASSWORD.getMessage())));
    }
}
