package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 本地文件上传配置（头像、树洞配图）
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

    /** 单张树洞配图大小上限（字节），比头像宽松一些 */
    private long maxPostImageBytes = 5 * 1024 * 1024;

    /** 头像子目录：uploads/avatar/ */
    public Path avatarDir() {
        return Path.of(dir, "avatar");
    }

    /** 树洞配图子目录：uploads/treehole/ */
    public Path postImageDir() {
        return Path.of(dir, "treehole");
    }
}
