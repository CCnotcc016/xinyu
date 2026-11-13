package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.config.RateLimitProperties;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定 + 同 IP 失败频次限制。
 *
 * <p>两层防护：</p>
 * <ul>
 *   <li><b>按账号</b>：失败次数落库（user.failed_attempts / locked_until），
 *       普通账号 5 次锁 15 分钟，管理员与客服 3 次锁 30 分钟。
 *       放库里而不是 Redis，是因为本机演示环境常常没起 Redis，
 *       而「锁定」这种保护一旦降级就等于没有。</li>
 *   <li><b>按 IP</b>：窗口内失败过多直接拒绝，挡撞库（一个密码试很多账号）。
 *       单机内存计数即可，重启丢失可以接受。</li>
 * </ul>
 *
 * <p>锁定期内即使密码正确也不放行，否则攻击者只要在窗口里试中就能继续用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String PRIVILEGED_ROLE_ADMIN = "ADMIN";
    private static final String PRIVILEGED_ROLE_STAFF = "STAFF";

    private final UserMapper userMapper;
    private final RateLimitProperties properties;

    /** IP -> 失败时间戳队列（只保留窗口内的） */
    private final Map<String, Deque<Long>> ipFailures = new ConcurrentHashMap<>();

    /**
     * 校验当前是否允许尝试登录：IP 频次 → 账号锁定。
     *
     * @param user 按用户名查到的用户，不存在时传 null（仍要做 IP 限制）
     */
    public void assertAllowed(User user, String ip) {
        assertIpAllowed(ip);
        if (user == null) {
            return;
        }
        LocalDateTime until = user.getLockedUntil();
        if (until != null && until.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED.getCode(),
                    "密码错误次数过多，账号已锁定，请 " + minutesUntil(until) + " 分钟后再试");
        }
    }

    /**
     * 记一次失败。
     *
     * @return 剩余可尝试次数；已锁定或账号不存在时返回 0
     */
    public int onFailure(User user, String ip) {
        recordIpFailure(ip);
        if (user == null) {
            return 0;
        }
        int max = maxAttempts(user);
        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        LocalDateTime now = LocalDateTime.now();

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId());
        if (attempts >= max) {
            LocalDateTime until = now.plusMinutes(lockMinutes(user));
            update.set(User::getFailedAttempts, 0).set(User::getLockedUntil, until);
            userMapper.update(null, update);
            // 同步内存状态，调用方才能立刻判断「这一次刚好触发锁定」
            user.setFailedAttempts(0);
            user.setLockedUntil(until);
            log.warn("账号 {}（{}）连续登录失败 {} 次，锁定至 {}", user.getUsername(), user.getRole(), attempts, until);
            return 0;
        }
        update.set(User::getFailedAttempts, attempts);
        userMapper.update(null, update);
        user.setFailedAttempts(attempts);
        return max - attempts;
    }

    /** 登录成功：清空失败计数与锁定状态 */
    public void onSuccess(User user) {
        if (user == null) {
            return;
        }
        boolean dirty = (user.getFailedAttempts() != null && user.getFailedAttempts() > 0)
                || user.getLockedUntil() != null;
        if (!dirty) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getFailedAttempts, 0)
                .set(User::getLockedUntil, null));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
    }

    /** 是否处于锁定期（后台列表要显示按钮用） */
    public boolean isLocked(User user) {
        return user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private int maxAttempts(User user) {
        return isPrivileged(user) ? properties.getAdminLoginMaxAttempts() : properties.getLoginMaxAttempts();
    }

    private long lockMinutes(User user) {
        return isPrivileged(user) ? properties.getAdminLoginLockMinutes() : properties.getLoginLockMinutes();
    }

    private boolean isPrivileged(User user) {
        String role = user.getRole();
        return PRIVILEGED_ROLE_ADMIN.equalsIgnoreCase(role) || PRIVILEGED_ROLE_STAFF.equalsIgnoreCase(role);
    }

    /** 距离解锁还剩多少分钟；未锁定返回 0 */
    public long minutesUntilUnlock(User user) {
        if (!isLocked(user)) {
            return 0;
        }
        return minutesUntil(user.getLockedUntil());
    }

    private long minutesUntil(LocalDateTime until) {
        long seconds = Duration.between(LocalDateTime.now(), until).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    private void assertIpAllowed(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        long windowMillis = properties.getLoginIpWindowSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Deque<Long> queue = ipFailures.get(ip);
        if (queue == null) {
            return;
        }
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.pollFirst();
            }
            if (queue.size() >= properties.getLoginIpMaxAttempts()) {
                log.warn("IP {} 登录失败次数过多，暂时拒绝", ip);
                throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(),
                        "登录尝试过于频繁，请 " + Math.max(1, properties.getLoginIpWindowSeconds() / 60) + " 分钟后再试");
            }
        }
    }

    private void recordIpFailure(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMillis = properties.getLoginIpWindowSeconds() * 1000L;
        Deque<Long> queue = ipFailures.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
                queue.pollFirst();
            }
            queue.addLast(now);
        }
        // 防止被大量不同 IP 撑爆内存：超阈值就清掉已经过期的桶
        if (ipFailures.size() > 5000) {
            ipFailures.entrySet().removeIf(e -> {
                Deque<Long> q = e.getValue();
                synchronized (q) {
                    return q.isEmpty() || now - q.peekLast() > windowMillis;
                }
            });
        }
    }
}
