package com.mqtt.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // 启用定时任务（设备状态检查）
public class MqttCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(MqttCloudApplication.class, args);
    }
}
