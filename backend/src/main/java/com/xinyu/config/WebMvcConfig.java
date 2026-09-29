// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 静态资源映射：把本地上传目录（默认 ./uploads）暴露成 /uploads/**。
 *
 * <p>开发时直接由 Spring Boot 提供，生产环境建议在 Nginx 里再配一层，
 * 让图片请求不进 Java 进程（见 nginx/nginx.conf）。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Path.of(uploadProperties.getDir()).toAbsolutePath().normalize();
        try {
            // 目录必须先存在：Path.toUri() 对不存在的目录不会补结尾的斜杠，
            // 少了斜杠 Spring 会把它当成「文件」而不是「目录」，/uploads/** 全部 404。
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建上传目录：" + dir, e);
        }
        String location = dir.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(uploadProperties.getPublicPrefix() + "/**")
                .addResourceLocations(location);
    }
}
