// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 昵称合规校验。
 *
 * <p>拦截两类名字：冒用国家领导人姓名、涉黄涉赌涉毒的低俗词。
 * 与 {@link SensitiveWordService} 共用同一套「读文件进 Set + contains 匹配」的实现思路，
 * 但词库与适用范围分开：这里的词只在昵称 / 用户名上生效，
 * 不会因为日记正文里出现「乳房」这类正常叙述就被拦下来。</p>
 */
@Slf4j
@Service
public class ForbiddenNameService {

    private final Set<String> words = new HashSet<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("forbidden-names.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty() && !w.startsWith("#")) {
                    words.add(w.toLowerCase());
                }
            }
            log.info("昵称禁用词库加载完成，共 {} 个词", words.size());
        } catch (Exception e) {
            log.warn("昵称禁用词库加载失败: {}", e.getMessage());
        }
    }

    /** 命中禁用词返回命中的那个词，否则返回 null */
    public String match(String text) {
        if (!StringUtils.hasText(text) || words.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase();
        for (String word : words) {
            if (lower.contains(word)) {
                return word;
            }
        }
        return null;
    }

    /** 校验不通过直接抛业务异常，调用方无需自己判断 */
    public void assertAllowed(String text) {
        if (match(text) != null) {
            throw new BusinessException(ErrorCode.NICKNAME_FORBIDDEN);
        }
    }
}
