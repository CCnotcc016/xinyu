package com.xinyu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 心语 · 启动入口
 * AI 情绪日记与匿名树洞
 */
@EnableScheduling
@MapperScan("com.xinyu.mapper")
@SpringBootApplication
public class XinyuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XinyuApplication.class, args);
    }
}
