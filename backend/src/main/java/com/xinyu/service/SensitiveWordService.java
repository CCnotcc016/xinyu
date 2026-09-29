// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
package com.xinyu.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 敏感词检测
 */
@Slf4j
@Service
public class SensitiveWordService {

    private final Set<String> words = new HashSet<>();

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("sensitive-words.txt").getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String w = line.trim();
                if (!w.isEmpty() && !w.startsWith("#")) {
                    words.add(w);
                }
            }
            log.info("敏感词库加载完成，共 {} 个词", words.size());
        } catch (Exception e) {
            log.warn("敏感词库加载失败: {}", e.getMessage());
        }
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
