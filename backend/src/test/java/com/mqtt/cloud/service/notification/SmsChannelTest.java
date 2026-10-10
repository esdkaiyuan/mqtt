package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import com.mqtt.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短信渠道单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖成功 / 网关非 2xx / 成功关键字缺失 / 无收件人 / 渠道停用五条分支，
 * 以及投递目标号码「前 3 后 4」脱敏。
 */
@ExtendWith(MockitoExtension.class)
class SmsChannelTest {

    private static final Long USER_ID = 10L;
    private static final Long CHANNEL_ID = 31L;

    @Mock
    private NotificationChannelConfigService configService;

    @Mock
    private UserService userService;

    @Mock
    private RestTemplate restTemplate;

    private NotificationProperties properties;
    private ObjectMapper objectMapper;
    private SmsChannel channel;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        objectMapper = new ObjectMapper();
        channel = new SmsChannel(configService, properties, userService, restTemplate, objectMapper);
    }

    @Test
    void send_should_skip_when_sms_disabled() {
        properties.setSmsEnabled(false);

        assertThat(channel.send(message()).skip()).isTrue();
        verifyNoInteractions(configService, restTemplate);
    }

    @Test
    void send_should_fail_when_no_recipient() {
        when(configService.getRawById(CHANNEL_ID)).thenReturn(config(configJson(null, null)));
        User user = new User();
        user.setId(USER_ID);
        when(userService.getById(USER_ID)).thenReturn(user);

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("无收件人");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void send_should_succeed_when_gateway_returns_2xx_and_keyword() {
        when(configService.getRawById(CHANNEL_ID))
                .thenReturn(config(configJson("13800138000", "OK")));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":\"OK\"}"));

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isTrue();
        assertThat(result.target()).isEqualTo("138****8000");
        verify(configService).markSuccess(CHANNEL_ID);
    }

    @Test
    void send_should_fail_when_gateway_returns_error_status() {
        when(configService.getRawById(CHANNEL_ID))
                .thenReturn(config(configJson("13800138000", null)));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(500).body("boom"));

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("500");
        verify(configService).markFailure(CHANNEL_ID);
    }

    @Test
    void send_should_fail_when_success_keyword_missing() {
        when(configService.getRawById(CHANNEL_ID))
                .thenReturn(config(configJson("13800138000", "SUCCESS")));
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"code\":\"FAIL\"}"));

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("成功关键字");
        verify(configService).markFailure(CHANNEL_ID);
    }

    @Test
    void mask_should_keep_head_and_tail() {
        assertThat(SmsChannel.mask("13800138000")).isEqualTo("138****8000");
        assertThat(SmsChannel.mask("1234567")).isEqualTo("1234567");
        assertThat(SmsChannel.mask(null)).isNull();
    }

    private NotificationMessage message() {
        NotificationMessage message = new NotificationMessage();
        message.setUserId(USER_ID);
        message.setEventType("alert.triggered");
        message.setChannelId(CHANNEL_ID);
        message.setTitle("温度过高");
        message.setContent("设备「网关一号」触发告警「温度过高」");
        return message;
    }

    private NotificationChannelConfig config(String json) {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setId(CHANNEL_ID);
        config.setUserId(USER_ID);
        config.setChannelType(NotificationConstants.CHANNEL_SMS);
        config.setConfig(json);
        return config;
    }

    private String configJson(String phones, String keyword) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("url", "https://sms.example.com/send");
        values.put("method", "POST");
        values.put("bodyTemplate", "{\"phone\":\"{phone}\",\"content\":\"{content}\"}");
        if (phones != null) {
            values.put("phones", phones);
        }
        if (keyword != null) {
            values.put("successKeyword", keyword);
        }
        return objectMapper.writeValueAsString(values);
    }
}