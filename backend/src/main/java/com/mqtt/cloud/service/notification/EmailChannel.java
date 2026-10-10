package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import com.mqtt.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件通知渠道（配置型渠道 {@code EMAIL}，T-27 设计文档 §4.3 / §8.1）。
 * <p>
 * 配置行由分发器 / 重试巡检经 {@link NotificationMessage#getChannelId()} 指定，收件人默认取
 * 当前用户 {@code sys_user.email}，配置行 {@code to} 给出时覆盖（用于把告警发到值班组邮箱）。
 * 两者都缺失时投递记 {@code FAILED}，错误摘要写明「无收件人」。
 * <p>
 * 失败一律以 {@link ChannelSendResult#failure} 表达，由分发器写投递记录并交巡检按退避重试。
 */
@Slf4j
@Component
public class EmailChannel implements NotificationChannel {

    private static final int MAX_SUBJECT_LENGTH = 200;

    private final NotificationChannelConfigService configService;
    private final NotificationProperties properties;
    private final UserService userService;
    private final MailSenderFactory mailSenderFactory;
    private final ObjectMapper objectMapper;

    public EmailChannel(NotificationChannelConfigService configService,
                        NotificationProperties properties,
                        UserService userService,
                        MailSenderFactory mailSenderFactory,
                        ObjectMapper objectMapper) {
        this.configService = configService;
        this.properties = properties;
        this.userService = userService;
        this.mailSenderFactory = mailSenderFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelType() {
        return NotificationConstants.CHANNEL_EMAIL;
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        if (!properties.isEmailEnabled()) {
            return ChannelSendResult.skipped();
        }
        NotificationChannelConfig config = resolve(message);
        if (config == null) {
            return ChannelSendResult.skipped();
        }
        Map<String, Object> values = parse(config.getConfig());
        List<String> recipients = recipients(values, message.getUserId());
        if (recipients.isEmpty()) {
            return ChannelSendResult.failure(null, "无收件人：用户未配置邮箱且渠道未指定 to");
        }

        String target = recipients.get(0);
        try {
            JavaMailSender sender = mailSenderFactory.create(values);
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from(values, target));
            mail.setTo(recipients.toArray(String[]::new));
            mail.setSubject(subject(message));
            mail.setText(content(message));
            sender.send(mail);
            configService.markSuccess(config.getId());
            return ChannelSendResult.success(target);
        } catch (Exception e) {
            log.warn("邮件投递失败: channelId={}, userId={}, event={}",
                    config.getId(), message.getUserId(), message.getEventType(), e);
            configService.markFailure(config.getId());
            return ChannelSendResult.failure(target, "邮件发送失败: " + e.getMessage());
        }
    }

    /** 定位配置行：优先用分发器 / 巡检指定的 {@code channelId}，否则按用户 + 事件回查。 */
    private NotificationChannelConfig resolve(NotificationMessage message) {
        if (message.getChannelId() != null) {
            return configService.getRawById(message.getChannelId());
        }
        return configService.listActiveMatching(message.getUserId(), channelType(), message.getEventType())
                .stream().findFirst().orElse(null);
    }

    private List<String> recipients(Map<String, Object> config, Long userId) {
        String to = text(config.get("to"));
        if (to != null && !to.isBlank()) {
            return Arrays.stream(to.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
        }
        if (userId == null) {
            return List.of();
        }
        User user = userService.getById(userId);
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return List.of(user.getEmail().trim());
        }
        return List.of();
    }

    private String from(Map<String, Object> config, String fallback) {
        String from = text(config.get("from"));
        if (from == null || from.isBlank()) {
            from = text(config.get("username"));
        }
        return (from == null || from.isBlank()) ? fallback : from;
    }

    private String subject(NotificationMessage message) {
        String title = message.getTitle();
        if (title == null || title.isBlank()) {
            return "MQTT 云平台通知";
        }
        return title.length() > MAX_SUBJECT_LENGTH ? title.substring(0, MAX_SUBJECT_LENGTH) : title;
    }

    private String content(NotificationMessage message) {
        if (message.getContent() != null && !message.getContent().isBlank()) {
            return message.getContent();
        }
        return message.getTitle() == null ? "" : message.getTitle();
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("邮件渠道配置解析失败，按空配置处理: config={}", json, e);
            return new LinkedHashMap<>();
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}