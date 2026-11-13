package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.common.Legal;
import com.xinyu.dto.LoginRequest;
import com.xinyu.dto.LoginResponse;
import com.xinyu.dto.RegisterRequest;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import com.xinyu.security.JwtUtil;
import com.xinyu.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SmsCodeService smsCodeService;
    private final ForbiddenNameService forbiddenNameService;
    private final LoginAttemptService loginAttemptService;

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest req) {
        // 先验短信验证码：通过之后才做唯一性检查，避免短信接口被当成「查重工具」刷
        smsCodeService.verifyAndConsume(req.getPhone(), req.getSmsCode());

        // 密码强度在落库前就卡住：长度、字母+数字、弱口令、包含用户名
        PasswordPolicy.assertStrong(req.getUsername(), req.getPassword());

        String nickname = StringUtils.hasText(req.getNickname()) ? req.getNickname().trim() : req.getUsername();
        forbiddenNameService.assertAllowed(nickname);
        forbiddenNameService.assertAllowed(req.getUsername());

        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        Long phoneExists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (phoneExists != null && phoneExists > 0) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(nickname);
        user.setPhone(req.getPhone());
        // 注册页勾了「我已阅读并同意」，这里落一条带时间的凭证，而不是只当作前端的一个状态
        user.setAgreementVersion(Legal.CURRENT_VERSION);
        user.setAgreementAcceptedAt(LocalDateTime.now());
        user.setLocationConsent(0);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    /**
     * 退出登录：把 token_version +1，让当前（以及所有设备上）的旧 token 立即失效。
     *
     * <p>以前「退出」只清前端 localStorage，token 还能继续用到自然过期 ——
     * 设备被借走 / 被抓包的人，退不退出没区别。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId) {
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("token_version = token_version + 1"));
    }

    /**
     * 登录。
     *
     * <p>三道关：账号未锁定 → 密码正确 → 账号未被禁用。
     * 失败次数与锁定时间都落库，Redis 挂了也不会失效；
     * 管理员 / 客服的阈值更严（3 次 / 30 分钟），因为这个账号被爆破的代价最大。</p>
     *
     * <p>刻意不加 @Transactional：登录失败要抛异常，而异常会回滚「失败次数 +1」，
     * 这样锁定计数永远涨不上去。计数必须独立提交。</p>
     */
    public LoginResponse login(LoginRequest req, String clientIp) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        loginAttemptService.assertAllowed(user, clientIp);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            int remaining = loginAttemptService.onFailure(user, clientIp);
            if (user != null) {
                // 锁定可能正好是「这一次」触发的，要说清楚，别让用户以为还能继续试
                if (loginAttemptService.isLocked(user)) {
                    throw new BusinessException(ErrorCode.ACCOUNT_LOCKED.getCode(),
                            "密码错误次数过多，账号已锁定，请 "
                                    + loginAttemptService.minutesUntilUnlock(user) + " 分钟后再试");
                }
                throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR.getCode(),
                        "用户名或密码错误，还可尝试 " + remaining + " 次");
            }
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        loginAttemptService.onSuccess(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(),
                user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        return LoginResponse.builder()
                .token(token)
                .user(UserVO.from(user))
                .build();
    }
}
