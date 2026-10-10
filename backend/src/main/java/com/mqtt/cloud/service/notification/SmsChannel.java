package com.mqtt.cloud.service.notification;

import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.entity.User;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import com.mqtt.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信通知渠道（配置型渠道 {@code SMS}，T-27 设计文档 §4.3 / §8.1）。
 * <p>
 * 走部署方既有的短信网关：配置行给出 {@code url} / {@code method} / {@code headers} /
 * {@code bodyTemplate} / {@code successKeyword}，收件人默认取 {@code sys_user.phone}，
 * 配置行 {@code phones} 给出时覆盖。模板支持 {@code {phone}} / {@code {content}} / {@code {title}} 占位。
 * <p>
 * 成功判定：HTTP 2xx 且（若配置了 {@code successKeyword}）响应体包含该关键字；否则记 {@code FAILED}
 * 并写明失败原因，由巡检按退避重试。投递记录中的号码脱敏为「前 3 后 4」。
 */
@Slf4j
@Component
public class SmsChannel implements NotificationChannel {

    private static final String PLACEHOLDER_PHONE = "{phone}";
    private static final String PLACEHOLDER_CONTENT = "{content}";
    private static final String PLACEHOLDER_TITLE = "{title}";

    private final NotificationChannelConfigService configService;
    private final NotificationProperties properties;
    private final UserService userService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SmsChannel(NotificationChannelConfigService configService,
                      NotificationProperties properties,
                      UserService userService,
                      RestTemplate restTemplate,
                      ObjectMapper objectMapper) {
        this.configService = configService;
        this.properties = properties;
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelType() {
        return NotificationConstants.CHANNEL_SMS;
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        if (!properties.isSmsEnabled()) {
            return ChannelSendResult.skipped();
        }
        NotificationChannelConfig config = resolve(message);
        if (config == null) {
            return ChannelSendResult.skipped();
        }
        Map<String, Object> values = parse(config.getConfig());
        List<String> phones = recipients(values, message.getUserId());
        if (phones.isEmpty()) {
            return ChannelSendResult.failure(null, "无收件人：用户未配置手机号且渠道未指定 phones");
        }

        String target = mask(phones.get(0));
        try {
            for (String phone : phones) {
                ChannelSendResult failure = deliverOne(config, values, phone, message);
                if (failure != null) {
                    configService.markFailure(config.getId());
                    return failure;
                }
            }
            configService.markSuccess(config.getId());
            return ChannelSendResult.success(target);
        } catch (Exception e) {
            log.warn("短信投递失败: channelId={}, userId={}, event={}",
                    config.getId(), message.getUserId(), message.getEventType(), e);
            configService.markFailure(config.getId());
            return ChannelSendResult.failure(target, "短信发送失败: " + e.getMessage());
        }
    }

    /** 单号码投递；成功返回 {@code null}，失败返回失败结果。 */
    private ChannelSendResult deliverOne(NotificationChannelConfig config, Map<String, Object> values,
                                         String phone, NotificationMessage message) {
        String url = text(values.get("url"));
        String method = text(values.get("method"));
        HttpMethod httpMethod = method == null || method.isBlank()
                ? HttpMethod.POST : HttpMethod.valueOf(method.trim().toUpperCase());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (values.get("headers") instanceof Map<?, ?> custom) {
            custom.forEach((key, value) -> {
                if (key != null && value != null) {
                    headers.set(String.valueOf(key), String.valueOf(value));
                }
            });
        }

        String body = render(values.get("bodyTemplate"), phone, message);
        ResponseEntity<String> response = restTemplate.exchange(
                url, httpMethod, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            return ChannelSendResult.failure(mask(phone),
                    "短信网关返回状态码 " + response.getStatusCode().value());
        }
        String keyword = text(values.get("successKeyword"));
        if (keyword != null && !keyword.isBlank()
                && (response.getBody() == null || !response.getBody().contains(keyword))) {
            return ChannelSendResult.failure(mask(phone), "短信网关响应未包含成功关键字");
        }
        return null;
    }

    private String render(Object template, String phone, NotificationMessage message) {
        String content = message.getContent() != null ? message.getContent()
                : (message.getTitle() == null ? "" : message.getTitle());
        String title = message.getTitle() == null ? "" : message.getTitle();
        String raw = text(template);
        if (raw == null || raw.isBlank()) {
            return objectMapper.writeValueAsString(Map.of("phone", phone, "content", content));
        }
        return raw.replace(PLACEHOLDER_PHONE, phone)
                .replace(PLACEHOLDER_CONTENT, content)
                .replace(PLACEHOLDER_TITLE, title);
    }

    private NotificationChannelConfig resolve(NotificationMessage message) {
        if (message.getChannelId() != null) {
            return configService.getRawById(message.getChannelId());
        }
        return configService.listActiveMatching(message.getUserId(), channelType(), message.getEventType())
                .stream().findFirst().orElse(null);
    }

    private List<String> recipients(Map<String, Object> config, Long userId) {
        String phones = text(config.get("phones"));
        if (phones != null && !phones.isBlank()) {
            return Arrays.stream(phones.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
        }
        if (userId == null) {
            return List.of();
        }
        User user = userService.getById(userId);
        if (user != null && user.getPhone() != null && !user.getPhone().isBlank()) {
            return List.of(user.getPhone().trim());
        }
        return List.of();
    }

    /** 号码脱敏：保留前 3 与后 4，中间以 {@code *} 覆盖。 */
    static String mask(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 7) {
            return trimmed;
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
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
            log.warn("短信渠道配置解析失败，按空配置处理: config={}", json, e);
            return new LinkedHashMap<>();
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}