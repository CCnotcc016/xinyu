// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.ChangePasswordRequest;
import com.xinyu.dto.ChangePhoneRequest;
import com.xinyu.dto.UpdateProfileRequest;
import com.xinyu.dto.UserVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户接口（个人设置相关）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(UserVO.from(userService.require(SecurityUtil.getCurrentUserId())));
    }

    /**
     * 修改昵称。username 是登录凭据，不允许改，所以这里只开放昵称。
     */
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(userService.updateNickname(SecurityUtil.getCurrentUserId(), req.getNickname()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(SecurityUtil.getCurrentUserId(), req);
        return Result.ok();
    }

    /** 绑定（首次）或更换手机号，需要新号码的短信验证码 */
    @PutMapping("/phone")
    public Result<UserVO> changePhone(@Valid @RequestBody ChangePhoneRequest req) {
        return Result.ok(userService.changePhone(SecurityUtil.getCurrentUserId(), req));
    }

    @PostMapping("/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.uploadAvatar(SecurityUtil.getCurrentUserId(), file));
    }
}
