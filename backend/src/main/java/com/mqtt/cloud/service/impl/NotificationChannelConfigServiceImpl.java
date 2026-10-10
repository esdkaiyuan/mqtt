package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.NotificationConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.NotificationChannelConfigRequest;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.mapper.NotificationChannelConfigMapper;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 通知渠道配置服务实现（T-27 设计文档 §6.2 / §7 / §13）。
 * <p>
 * 校验矩阵集中在此：类型 → {@code 6263}，配置 / 事件 / 重名 → {@code 6262}，数量上限 → {@code 6263}。
 * 响应侧敏感字段统一脱敏为 {@code ******}；更新时敏感字段留空或回填脱敏串表示保留原值，
 * 避免前端把脱敏串写回库而毁掉凭据。
 */
@Slf4j
@Service
public class NotificationChannelConfigServiceImpl
        extends ServiceImpl<NotificationChannelConfigMapper, NotificationChannelConfig>
        implements NotificationChannelConfigService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{5,20}$");

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public NotificationChannelConfigServiceImpl(NotificationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NotificationChannelConfig> listByUser(Long userId) {
        LambdaQueryWrapper<NotificationChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannelConfig::getUserId, userId)
                .orderByDesc(NotificationChannelConfig::getId);
        return list(wrapper).stream().map(this::desensitized).toList();
    }

    @Override
    public NotificationChannelConfig getOwned(Long userId, Long id) {
        return desensitized(requireOwned(userId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationChannelConfig create(Long userId, NotificationChannelConfigRequest request) {
        String channelType = normalizeType(request.getChannelType());
        String name = requireName(request.getName());
        validateChannelCount(userId);
        validateNameUnique(userId, name, null);
        Map<String, Object> config = validateConfig(channelType, request.getConfig());
        String events = serializeEvents(request.getEvents());

        NotificationChannelConfig entity = new NotificationChannelConfig();
        entity.setUserId(userId);
        entity.setChannelType(channelType);
        entity.setName(name);
        entity.setConfig(toJson(config));
        entity.setEvents(events);
        entity.setIsActive(Boolean.FALSE.equals(request.getIsActive()) ? 0 : 1);
        entity.setFailureCount(0);
        save(entity);
        return desensitized(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationChannelConfig update(Long userId, Long id, NotificationChannelConfigRequest request) {
        NotificationChannelConfig existing = requireOwned(userId, id);
        if (request.getChannelType() != null && !request.getChannelType().isBlank()
                && !existing.getChannelType().equalsIgnoreCase(request.getChannelType().trim())) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED,
                    "渠道类型不可变更");
        }
        String channelType = existing.getChannelType();
        String name = requireName(request.getName());
        validateNameUnique(userId, name, id);
        Map<String, Object> merged = mergeConfig(existing, request.getConfig());
        Map<String, Object> config = validateConfig(channelType, merged);
        String events = serializeEvents(request.getEvents());

        LambdaUpdateWrapper<NotificationChannelConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationChannelConfig::getId, id)
                .eq(NotificationChannelConfig::getUserId, userId)
                .set(NotificationChannelConfig::getName, name)
                .set(NotificationChannelConfig::getConfig, toJson(config))
                .set(NotificationChannelConfig::getEvents, events)
                .set(NotificationChannelConfig::getIsActive,
                        Boolean.FALSE.equals(request.getIsActive()) ? 0 : 1);
        update(wrapper);

        existing.setName(name);
        existing.setConfig(toJson(config));
        existing.setEvents(events);
        existing.setIsActive(Boolean.FALSE.equals(request.getIsActive()) ? 0 : 1);
        return desensitized(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        removeById(id);
    }

    @Override
    public List<NotificationChannelConfig> listActiveMatching(Long userId, String channelType, String eventType) {
        LambdaQueryWrapper<NotificationChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannelConfig::getUserId, userId)
                .eq(NotificationChannelConfig::getChannelType, channelType)
                .eq(NotificationChannelConfig::getIsActive, 1)
                .orderByAsc(NotificationChannelConfig::getId);
        return list(wrapper).stream()
                .filter(row -> subscribes(row.getEvents(), eventType))
                .toList();
    }

    @Override
    public NotificationChannelConfig getRawById(Long id) {
        return id == null ? null : getById(id);
    }

    @Override
    public void markSuccess(Long id) {
        if (id == null) {
            return;
        }
        LambdaUpdateWrapper<NotificationChannelConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationChannelConfig::getId, id)
                .set(NotificationChannelConfig::getLastTriggeredAt, LocalDateTime.now())
                .set(NotificationChannelConfig::getFailureCount, 0);
        update(wrapper);
    }

    @Override
    public void markFailure(Long id) {
        if (id == null) {
            return;
        }
        LambdaUpdateWrapper<NotificationChannelConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(NotificationChannelConfig::getId, id)
                .setSql("failure_count = failure_count + 1");
        update(wrapper);
    }

    private NotificationChannelConfig requireOwned(Long userId, Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        NotificationChannelConfig entity = getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return entity;
    }

    /** 类型校验：仅接受 EMAIL / SMS，其余返 {@code 6263}。 */
    private String normalizeType(String channelType) {
        if (channelType == null || channelType.isBlank()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED, "渠道类型不能为空");
        }
        String normalized = channelType.trim().toUpperCase();
        if (!NotificationConstants.CONFIGURED_CHANNELS.contains(normalized)) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED,
                    "不支持的渠道类型: " + channelType);
        }
        return normalized;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "渠道名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "渠道名称不能超过 100 个字符");
        }
        return trimmed;
    }

    private void validateChannelCount(Long userId) {
        LambdaQueryWrapper<NotificationChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannelConfig::getUserId, userId);
        if (count(wrapper) >= properties.getMaxChannelsPerUser()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED,
                    "渠道数量已达上限 " + properties.getMaxChannelsPerUser());
        }
    }

    private void validateNameUnique(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<NotificationChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationChannelConfig::getUserId, userId)
                .eq(NotificationChannelConfig::getName, name);
        if (excludeId != null) {
            wrapper.ne(NotificationChannelConfig::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "渠道名称已存在: " + name);
        }
    }

    /** 按类型校验配置必填项与可选字段格式，命中即抛 {@code 6262}。 */
    private Map<String, Object> validateConfig(String channelType, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "渠道配置不能为空");
        }
        Map<String, Object> normalized = new LinkedHashMap<>(config);
        if (NotificationConstants.CHANNEL_EMAIL.equals(channelType)) {
            validateEmailConfig(normalized);
        } else {
            validateSmsConfig(normalized);
        }
        return normalized;
    }

    private void validateEmailConfig(Map<String, Object> config) {
        requireText(config, "host", "邮件服务器地址不能为空");
        Object port = config.get("port");
        int portValue = parsePort(port);
        config.put("port", portValue);
        requireText(config, "from", "发件人不能为空");
        validateRecipientList(config.get("to"), EMAIL_PATTERN, "收件人格式非法");
    }

    private void validateSmsConfig(Map<String, Object> config) {
        String url = requireText(config, "url", "短信网关地址不能为空");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "短信网关地址须为 http(s)");
        }
        String method = requireText(config, "method", "短信请求方法不能为空").toUpperCase();
        if (!"POST".equals(method) && !"PUT".equals(method)) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "短信请求方法仅支持 POST / PUT");
        }
        config.put("method", method);
        validateRecipientList(config.get("phones"), PHONE_PATTERN, "手机号格式非法");
    }

    private String requireText(Map<String, Object> config, String key, String message) {
        Object value = config.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, message);
        }
        return value.toString().trim();
    }

    private int parsePort(Object port) {
        int value;
        try {
            value = port == null ? 0 : Integer.parseInt(port.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "邮件端口非法");
        }
        if (value < 1 || value > 65535) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "邮件端口须在 1~65535 之间");
        }
        return value;
    }

    private void validateRecipientList(Object raw, Pattern pattern, String message) {
        if (raw == null || raw.toString().isBlank()) {
            return;
        }
        for (String item : raw.toString().split(",")) {
            String candidate = item.trim();
            if (!candidate.isEmpty() && !pattern.matcher(candidate).matches()) {
                throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, message + ": " + candidate);
            }
        }
    }

    private String serializeEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "至少订阅一个通知事件");
        }
        List<String> normalized = new ArrayList<>();
        for (String event : events) {
            String candidate = event == null ? null : event.trim();
            if (candidate == null || !NotificationConstants.NOTIFICATION_EVENTS.contains(candidate)) {
                throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_INVALID, "不支持的通知事件: " + event);
            }
            if (!normalized.contains(candidate)) {
                normalized.add(candidate);
            }
        }
        return toJson(normalized);
    }

    /** 更新时合并配置：敏感字段留空或回填脱敏串表示保留原值。 */
    private Map<String, Object> mergeConfig(NotificationChannelConfig existing, Map<String, Object> incoming) {
        Map<String, Object> stored = parseMap(existing.getConfig());
        if (incoming == null || incoming.isEmpty()) {
            return stored;
        }
        Map<String, Object> merged = new LinkedHashMap<>(incoming);
        for (String key : NotificationConstants.SENSITIVE_CONFIG_KEYS) {
            if (isBlankOrMasked(merged.get(key))) {
                merged.put(key, stored.get(key));
            }
        }
        return merged;
    }

    private boolean isBlankOrMasked(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank() || NotificationConstants.MASK.equals(text);
        }
        return false;
    }

    private NotificationChannelConfig desensitized(NotificationChannelConfig entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> config = parseMap(entity.getConfig());
        for (String key : NotificationConstants.SENSITIVE_CONFIG_KEYS) {
            if (config.containsKey(key)) {
                config.put(key, NotificationConstants.MASK);
            }
        }
        entity.setConfig(toJson(config));
        return entity;
    }

    private boolean subscribes(String eventsJson, String eventType) {
        if (eventsJson == null || eventType == null) {
            return false;
        }
        try {
            List<String> events = objectMapper.readValue(eventsJson, new TypeReference<>() {
            });
            return events.contains(eventType);
        } catch (Exception e) {
            log.warn("渠道事件列表解析失败，按未订阅处理: events={}", eventsJson, e);
            return false;
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("渠道配置解析失败，按空配置处理: config={}", json, e);
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}