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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 邮件渠道单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖成功 / 失败分支、无收件人、渠道停用与配置行缺失；失败须写回渠道失败计数，
 * 由投递记录与巡检承担重试。
 */
@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    private static final Long USER_ID = 10L;
    private static final Long CHANNEL_ID = 21L;

    @Mock
    private NotificationChannelConfigService configService;

    @Mock
    private UserService userService;

    @Mock
    private MailSenderFactory mailSenderFactory;

    @Mock
    private JavaMailSender mailSender;

    private NotificationProperties properties;
    private ObjectMapper objectMapper;
    private EmailChannel channel;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        objectMapper = new ObjectMapper();
        channel = new EmailChannel(configService, properties, userService, mailSenderFactory, objectMapper);
    }

    @Test
    void send_should_skip_when_email_disabled() {
        properties.setEmailEnabled(false);

        assertThat(channel.send(message()).skip()).isTrue();
        verifyNoInteractions(configService, mailSenderFactory);
    }

    @Test
    void send_should_skip_when_config_row_missing() {
        when(configService.getRawById(CHANNEL_ID)).thenReturn(null);

        assertThat(channel.send(message()).skip()).isTrue();
        verifyNoInteractions(mailSenderFactory);
    }

    @Test
    void send_should_fail_when_no_recipient() {
        when(configService.getRawById(CHANNEL_ID)).thenReturn(config(configJson("smtp.example.com", 587, null)));
        User user = new User();
        user.setId(USER_ID);
        when(userService.getById(USER_ID)).thenReturn(user);

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("无收件人");
        verify(mailSenderFactory, never()).create(any());
    }

    @Test
    void send_should_succeed_and_mark_success() {
        when(configService.getRawById(CHANNEL_ID))
                .thenReturn(config(configJson("smtp.example.com", 587, "ops@example.com")));
        when(mailSenderFactory.create(any())).thenReturn(mailSender);

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isTrue();
        assertThat(result.target()).isEqualTo("ops@example.com");
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(configService).markSuccess(CHANNEL_ID);
    }

    @Test
    void send_should_fail_and_mark_failure_on_sender_error() {
        when(configService.getRawById(CHANNEL_ID))
                .thenReturn(config(configJson("smtp.example.com", 587, "ops@example.com")));
        when(mailSenderFactory.create(any())).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        ChannelSendResult result = channel.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("smtp down");
        verify(configService).markFailure(CHANNEL_ID);
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
        config.setChannelType(NotificationConstants.CHANNEL_EMAIL);
        config.setConfig(json);
        return config;
    }

    private String configJson(String host, int port, String to) {
        Map<String, Object> values = to == null
                ? Map.of("host", host, "port", port, "from", "ops@example.com")
                : Map.of("host", host, "port", port, "from", "ops@example.com", "to", to);
        return objectMapper.writeValueAsString(values);
    }
}