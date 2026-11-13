package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import com.xinyu.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
 * 树洞配图上传：只允许常见图片后缀，落盘到 uploads/treehole/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UploadProperties uploadProperties;

    public String uploadPostImage(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UPLOAD_EMPTY);
        }
        if (file.getSize() > uploadProperties.getMaxPostImageBytes()) {
            throw new BusinessException(ErrorCode.POST_IMAGE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.UPLOAD_TYPE_UNSUPPORTED);
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.UPLOAD_TYPE_UNSUPPORTED);
        }
        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = uploadProperties.postImageDir();
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("树洞配图保存失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }
        return uploadProperties.getPublicPrefix() + "/treehole/" + filename;
    }

    /** 校验 url 确实是本服务上传的树洞配图，避免伪造外链 */
    public boolean isOwnedPostImage(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String prefix = uploadProperties.getPublicPrefix() + "/treehole/";
        if (!url.startsWith(prefix)) {
            return false;
        }
        String name = url.substring(prefix.length());
        return !name.isBlank() && !name.contains("/") && !name.contains("..");
    }

    private String extension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
