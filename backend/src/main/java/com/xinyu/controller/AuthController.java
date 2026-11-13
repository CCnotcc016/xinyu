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
import com.xinyu.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * 存活探针：前端登录页用一个 3 秒超时的 GET 判断「后端开没开」，
     * 决定是直接尝试登录，还是走本机保存过的账号离线进入。
     *
     * 刻意不查库、不碰 Redis —— 它只回答「端口通不通」。
     * 数据库有问题时登录会直接返回明确错误，比在这里伪装成离线更好排查。
     */
    @GetMapping("/ping")
    public Result<Void> ping() {
        return Result.ok();
    }

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

    /** 登录：按账号失败次数锁定 + 同 IP 失败频次限制，见 LoginAttemptService */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return Result.ok(authService.login(req, ClientIp.of(request)));
    }
}
