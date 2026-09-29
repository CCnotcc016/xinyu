// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 本地文件上传配置（目前用于头像）
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.upload")
public class UploadProperties {

    /** 落盘目录，相对进程工作目录 */
    private String dir = "./uploads";

    /** 对外访问前缀，需与 WebMvcConfig 的资源映射一致 */
    private String publicPrefix = "/uploads";

    /** 头像大小上限（字节） */
    private long maxAvatarBytes = 2 * 1024 * 1024;

    /** 头像子目录：uploads/avatar/ */
    public Path avatarDir() {
        return Path.of(dir, "avatar");
    }
}
