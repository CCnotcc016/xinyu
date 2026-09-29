// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.CaptchaVO;
import com.xinyu.dto.LoginRequest;
import com.xinyu.dto.LoginResponse;
import com.xinyu.dto.RegisterRequest;
import com.xinyu.dto.SmsCodeRequest;
import com.xinyu.dto.SmsCodeVO;
import com.xinyu.dto.UserVO;
import com.xinyu.service.AuthService;
import com.xinyu.service.CaptchaService;
import com.xinyu.service.SmsCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SmsCodeService smsCodeService;

    /** 图形验证码：注册页与换手机号页在「获取验证码」前先过这道 */
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(captchaService.issue());
    }

    /** 发送短信验证码，必须携带图形验证码的答案 */
    @PostMapping("/sms-code")
    public Result<SmsCodeVO> smsCode(@Valid @RequestBody SmsCodeRequest req) {
        return Result.ok(smsCodeService.send(req.getPhone(), req.getCaptchaId(), req.getCaptchaCode()));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }
}
