package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.DeviceLogConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.DeviceLogProperties;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DeviceLogMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 设备统一日志查询实现（T-20 设计文档 §7.3）。
 * <p>
 * 只读链路：<b>设备鉴权 → 参数归一化 → 计数 → 分页查询 → 关联键 / 展示字段回填</b>。
 * 四张来源表在 SQL 侧一次 {@code UNION ALL} 归并并全局排序分页，
 * 应用层不再做跨源合并（多份 {@code IPage} 在应用层归并无法正确处理 {@code total} 与跨源排序）。
 * <p>
 * 关联键回填只在 Java 侧进行：{@code message.payload} 为 TEXT 且可能非 JSON，
 * SQL 侧 {@code JSON_EXTRACT} 会直接报错，故仅对 {@code topic} 以 {@code /reply} 结尾的条目用
 * {@link ObjectMapper} 安全解析 {@code payload.id}（沿用 T-15 {@code CommandReplyServiceImpl} 同款解析口径），
 * 解析失败保持 {@code null}、只记 DEBUG，绝不因此让整页查询失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLogServiceImpl implements DeviceLogService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int MIN_PAGE_NUM = 1;
    private static final int MIN_PAGE_SIZE = 1;

    private static final String DIRECTION_PUBLISH = "PUBLISH";
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_OFFLINE = "OFFLINE";

    private static final String TITLE_COMMAND = "命令";
    private static final String TITLE_EVENT = "事件";
    private static final String TITLE_STATUS_CHANGED = "状态变更";
    private static final String TITLE_STATUS_ONLINE = "设备上线";
    private static final String TITLE_STATUS_OFFLINE = "设备下线";
    private static final String TITLE_MSG_DOWN = "下行报文";
    private static final String TITLE_MSG_UP = "上行报文";

    private final DeviceMapper deviceMapper;
    private final DeviceLogMapper deviceLogMapper;
    private final DeviceLogProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<DeviceLogItem> page(Long userId, Long deviceId, DeviceLogQueryDTO query) {
        checkOwnership(deviceId, userId);

        int pageNum = clamp(query == null ? null : query.getPageNum(),
                MIN_PAGE_NUM, Integer.MAX_VALUE, DeviceLogConstants.DEFAULT_PAGE_NUM);
        int pageSize = clamp(query == null ? null : query.getPageSize(),
                MIN_PAGE_SIZE, maxPageSize(), DeviceLogConstants.DEFAULT_PAGE_SIZE);

        List<String> types = normalizeTypes(query == null ? null : query.getTypes());

        LocalDateTime startTime = parseTime(query == null ? null : query.getStartTime(), "startTime");
        LocalDateTime endTime = parseTime(query == null ? null : query.getEndTime(), "endTime");
        validateRange(startTime, endTime);

        String keyword = normalizeKeyword(query == null ? null : query.getKeyword());

        long total = deviceLogMapper.countQuery(deviceId, types, startTime, endTime, keyword);

        Page<DeviceLogItem> result = new Page<>(pageNum, pageSize);
        result.setTotal(total);
        // total 为 0 时短路：UNION 归并扫描代价不低，空结果没必要再执行一次分页查询
        if (total == 0) {
            result.setRecords(List.of());
            return result;
        }

        long offset = (long) (pageNum - 1) * pageSize;
        List<DeviceLogItem> records = deviceLogMapper.pageQuery(
                deviceId, types, startTime, endTime, keyword, offset, pageSize);
        decorate(records);
        result.setRecords(records);
        return result;
    }

    /**
     * 设备级鉴权：不存在（含已逻辑删除）→ {@code 2002}；非本人设备且非 ADMIN → {@code 2003}。
     * <p>
     * 与 {@code DeviceController#checkOwnership} 保持同一归属口径（「非本人」沿用既有语义，含 ADMIN 放行），
     * 避免同一设备在「详情 / 命令 / 事件」可见、而「日志」不可见的自相矛盾。
     */
    private void checkOwnership(Long deviceId, Long userId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Integer.valueOf(1).equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        boolean isOwner = device.getOwnerId() != null && device.getOwnerId().equals(userId);
        if (!isOwner && !SecurityUtils.isAdmin()) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
    }

    /** 类型白名单校验：去空白 + 大写归一，任一非法即 {@code 6224}；归一后为空（全部为空串）= 不过滤。 */
    private List<String> normalizeTypes(List<String> rawTypes) {
        if (rawTypes == null || rawTypes.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawTypes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String type = raw.trim().toUpperCase(Locale.ROOT);
            if (!DeviceLogConstants.SUPPORTED_TYPES.contains(type)) {
                throw new BusinessException(ResultCode.DEVICE_LOG_TYPE_UNSUPPORTED, "不支持的日志类型：" + raw);
            }
            normalized.add(type);
        }
        return normalized.isEmpty() ? null : new ArrayList<>(normalized);
    }

    /** 解析时间：优先 {@code yyyy-MM-dd HH:mm:ss}，回退 ISO-8601（容忍尾部 {@code Z}）；非法 → {@code 6223}。 */
    private LocalDateTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMAT);
        } catch (DateTimeParseException ignored) {
            // 回退 ISO-8601
        }
        try {
            return LocalDateTime.parse(text.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.DEVICE_LOG_RANGE_INVALID,
                    field + " 时间格式非法，应为 yyyy-MM-dd HH:mm:ss 或 ISO-8601");
        }
    }

    /** 时间窗：半开区间 {@code [start, end)}，须 {@code start < end} 且跨度不超过 {@code max-range-days}。 */
    private void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return;
        }
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ResultCode.DEVICE_LOG_RANGE_INVALID, "startTime 必须早于 endTime");
        }
        int maxRangeDays = maxRangeDays();
        if (endTime.isAfter(startTime.plusDays(maxRangeDays))) {
            throw new BusinessException(ResultCode.DEVICE_LOG_RANGE_INVALID,
                    "查询时间跨度超过上限 " + maxRangeDays + " 天");
        }
    }

    /** 关键字：空白视作不筛选；非空去空白后包裹 {@code %...%}。 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    /** 关联键回填 + 展示标题生成（SQL 不产出的部分）。 */
    private void decorate(List<DeviceLogItem> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (DeviceLogItem item : records) {
            item.setTitle(buildTitle(item));
            if (DeviceLogConstants.TYPE_COMMAND.equals(item.getLogType())) {
                item.setCorrelationId(item.getCommandId());
                item.setCorrelationRole(DeviceLogConstants.ROLE_REQUEST);
            } else if (DeviceLogConstants.TYPE_MESSAGE.equals(item.getLogType()) && isReplyTopic(item.getTopic())) {
                String correlationId = extractReplyId(item.getPayload());
                if (correlationId != null) {
                    item.setCorrelationId(correlationId);
                    item.setCorrelationRole(DeviceLogConstants.ROLE_REPLY);
                }
            }
        }
    }

    /** 展示标题（§5.1）：如「命令：switch」「上行报文：reply」「设备上线」。 */
    private String buildTitle(DeviceLogItem item) {
        String type = item.getLogType();
        if (DeviceLogConstants.TYPE_COMMAND.equals(type)) {
            return joinTitle(TITLE_COMMAND, firstNonBlank(item.getIdentifier(), item.getSubType(), item.getCommandId()));
        }
        if (DeviceLogConstants.TYPE_EVENT.equals(type)) {
            return joinTitle(TITLE_EVENT, firstNonBlank(item.getEventType(), item.getIdentifier()));
        }
        if (DeviceLogConstants.TYPE_STATUS.equals(type)) {
            String status = item.getStatus();
            if (STATUS_ONLINE.equals(status)) {
                return TITLE_STATUS_ONLINE;
            }
            if (STATUS_OFFLINE.equals(status)) {
                return TITLE_STATUS_OFFLINE;
            }
            return joinTitle(TITLE_STATUS_CHANGED, nullToEmpty(status));
        }
        if (DeviceLogConstants.TYPE_MESSAGE.equals(type)) {
            String label = DIRECTION_PUBLISH.equals(item.getDirection()) ? TITLE_MSG_DOWN : TITLE_MSG_UP;
            return joinTitle(label, topicTail(item.getTopic()));
        }
        return type == null ? "日志" : type;
    }

    private String joinTitle(String prefix, String value) {
        return value.isEmpty() ? prefix : prefix + "：" + value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 取主题最后一段作为报文简称（如 {@code device/DEV-001/reply} → {@code reply}）。 */
    private String topicTail(String topic) {
        if (topic == null || topic.isBlank()) {
            return "";
        }
        int slash = topic.lastIndexOf('/');
        return slash >= 0 ? topic.substring(slash + 1) : topic;
    }

    private boolean isReplyTopic(String topic) {
        return topic != null && topic.endsWith(DeviceLogConstants.TOPIC_REPLY_SUFFIX);
    }

    /** 从回执载荷中安全解析 {@code id}；非对象 / 非文本 / 解析失败一律返回 {@code null}（不抛）。 */
    private String extractReplyId(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode id = root.get(DeviceLogConstants.FIELD_ID);
            if (id == null || !id.isTextual() || id.asText().isBlank()) {
                return null;
            }
            return id.asText();
        } catch (Exception e) {
            log.debug("命令回执载荷解析失败，关联键留空: {}", e.getMessage());
            return null;
        }
    }

    private int maxPageSize() {
        int configured = properties.getMaxPageSize();
        return configured > 0 ? configured : DeviceLogConstants.MAX_PAGE_SIZE_FALLBACK;
    }

    private int maxRangeDays() {
        int configured = properties.getMaxRangeDays();
        return configured > 0 ? configured : DeviceLogConstants.DEFAULT_MAX_RANGE_DAYS;
    }

    /** 取值夹取：[min, max]；{@code null} 用 {@code fallback}，且 {@code fallback} 同样受 {@code max} 约束。 */
    private int clamp(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.min(Math.max(resolved, min), max);
    }
}
