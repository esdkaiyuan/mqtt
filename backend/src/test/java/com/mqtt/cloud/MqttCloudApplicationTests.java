package com.mqtt.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "app.jwt.secret=test-only-jwt-secret-which-is-long-enough-123")
class MqttCloudApplicationTests {

    @Test
    void contextLoads() {
    }
}
