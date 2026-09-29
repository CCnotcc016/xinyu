// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.config.UploadProperties;
import com.xinyu.dto.ChangePasswordRequest;
import com.xinyu.dto.ChangePhoneRequest;
import com.xinyu.dto.UserVO;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 个人设置：昵称、密码、手机号、头像。
 *
 * <p>所有方法的第一道防线都是「只能改自己的」——用户 id 一律从 token 里取，
 * 不接受请求体传入，避免越权改别人的账号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;
    private final ForbiddenNameService forbiddenNameService;
    private final UploadProperties uploadProperties;

    public User require(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** 修改昵称（走禁用词校验） */
    public UserVO updateNickname(Long userId, String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.trim();
        if (nickname.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        forbiddenNameService.assertAllowed(nickname);
        User user = require(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getNickname, nickname));
        user.setNickname(nickname);
        return UserVO.from(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = require(userId);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG);
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPasswordHash, passwordEncoder.encode(req.getNewPassword())));
        log.info("用户 {} 修改了密码", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO changePhone(Long userId, ChangePhoneRequest req) {
        User user = require(userId);
        String phone = req.getPhone().trim();

        if (phone.equals(user.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_SAME);
        }
        // 已绑过手机号 = 换号，必须再验一次密码
        if (StringUtils.hasText(user.getPhone())) {
            if (!StringUtils.hasText(req.getPassword())
                    || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_WRONG);
            }
        }
        smsCodeService.verifyAndConsume(phone, req.getSmsCode());

        Long used = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .ne(User::getId, userId));
        if (used != null && used > 0) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getPhone, phone));
        user.setPhone(phone);
        return UserVO.from(user);
    }

    /** 上传头像，只留本地一份，返回可直接访问的相对路径 */
    @Transactional(rollbackFor = Exception.class)
    public UserVO uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_EMPTY);
        }
        if (file.getSize() > uploadProperties.getMaxAvatarBytes()) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.UPLOAD_TYPE_UNSUPPORTED);
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.UPLOAD_TYPE_UNSUPPORTED);
        }

        User user = require(userId);
        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = uploadProperties.avatarDir();
        Path target = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            // 再校验一次真实大小，multipart 上限被调大时也不会漏
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像保存失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }

        String url = uploadProperties.getPublicPrefix() + "/avatar/" + filename;
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatar, url));
        deleteOldAvatar(user.getAvatar());
        user.setAvatar(url);
        return UserVO.from(user);
    }

    /** 换头像后把旧文件删掉，避免 uploads 目录无限增长；外链头像不动 */
    private void deleteOldAvatar(String oldAvatar) {
        String prefix = uploadProperties.getPublicPrefix();
        if (!StringUtils.hasText(oldAvatar) || !oldAvatar.startsWith(prefix + "/avatar/")) {
            return;
        }
        String name = oldAvatar.substring((prefix + "/avatar/").length());
        if (name.contains("/") || name.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(uploadProperties.avatarDir().resolve(name));
        } catch (IOException e) {
            log.warn("旧头像删除失败（忽略）: {}", e.getMessage());
        }
    }

    private String extension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
