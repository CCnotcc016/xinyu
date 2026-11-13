package com.xinyu.controller;

import com.xinyu.common.PageResult;
import com.xinyu.common.Result;
import com.xinyu.dto.AgreementRequest;
import com.xinyu.dto.ChangePasswordRequest;
import com.xinyu.dto.ChangePhoneRequest;
import com.xinyu.dto.DeleteAccountRequest;
import com.xinyu.dto.LocationConsentRequest;
import com.xinyu.dto.TreeholePostVO;
import com.xinyu.dto.UpdateProfileRequest;
import com.xinyu.dto.UserProfileVO;
import com.xinyu.dto.UserVO;
import com.xinyu.security.SecurityUtil;
import com.xinyu.service.TreeholeService;
import com.xinyu.service.UserDataService;
import com.xinyu.service.UserProfileService;
import com.xinyu.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户接口（个人设置相关）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;
    private final TreeholeService treeholeService;
    private final UserDataService userDataService;

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(UserVO.from(userService.require(SecurityUtil.getCurrentUserId())));
    }

    /**
     * 别人的主页（从树洞帖主、评论昵称点进来）。
     *
     * <p>只返回公开信息：昵称、头像、注册时间、树洞统计与好友关系。
     * 日记数量/连续天数这类隐私数据只在看自己的主页时才带上，由服务层判断。</p>
     */
    @GetMapping("/{id}/profile")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<UserProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userProfileService.profile(SecurityUtil.getCurrentUserId(), id));
    }

    /** 某人公开的树洞帖子（匿名帖不会被列出来） */
    @GetMapping("/{id}/posts")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<PageResult<TreeholePostVO>> posts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(treeholeService.userPosts(SecurityUtil.getCurrentUserId(), id, page, size));
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

    /**
     * 确认同意现行《用户协议》《隐私政策》。
     *
     * <p>老账号在 v2.3 之前的库里没有同意记录，前端会强制弹窗，用户点过这里才算数。</p>
     */
    @PostMapping("/agreement")
    public Result<UserVO> acceptAgreement(@Valid @RequestBody AgreementRequest req) {
        return Result.ok(userService.acceptAgreement(SecurityUtil.getCurrentUserId(), req.getVersion()));
    }

    /** 导出我的全部数据（JSON，前端直接下载成文件） */
    @GetMapping("/data-export")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Map<String, Object>> dataExport() {
        return Result.ok(userDataService.export(SecurityUtil.getCurrentUserId()));
    }

    /** 注销账号：验证密码后彻底删除账号与全部内容，不可恢复 */
    @DeleteMapping("/account")
    @PreAuthorize("!hasRole('STAFF')")
    public Result<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest req) {
        userDataService.deleteAccount(SecurityUtil.getCurrentUserId(), req);
        return Result.ok();
    }

    /** 定位与天气授权开关，随时可撤回；撤回只影响天气卡片，不影响任何已有日记 */
    @PutMapping("/location-consent")
    public Result<UserVO> updateLocationConsent(@Valid @RequestBody LocationConsentRequest req) {
        return Result.ok(userService.updateLocationConsent(SecurityUtil.getCurrentUserId(), req.getConsent()));
    }
}
