package com.xinyu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.ai")
public class AiProperties {

    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private double temperature = 0.7;
    private int maxTokens = 1024;
    private int timeoutSeconds = 90;
}
