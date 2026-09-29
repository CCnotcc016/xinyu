// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.dto.LoginRequest;
import com.xinyu.dto.LoginResponse;
import com.xinyu.dto.RegisterRequest;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import com.xinyu.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest req) {
        // 先验短信验证码：通过之后才做唯一性检查，避免短信接口被当成「查重工具」刷
        smsCodeService.verifyAndConsume(req.getPhone(), req.getSmsCode());

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
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return UserVO.from(user);
    }

    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .user(UserVO.from(user))
                .build();
    }
}
