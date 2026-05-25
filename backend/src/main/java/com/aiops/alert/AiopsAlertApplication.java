package com.aiops.alert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AIOps Alert 启动类。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.aiops.alert.mapper")
public class AiopsAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiopsAlertApplication.class, args);
    }
}
