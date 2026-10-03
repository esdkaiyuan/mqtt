package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.PropertyHistoryConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.PropertyHistoryProperties;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.response.PropertyHistoryAggregate;
import com.mqtt.cloud.dto.response.PropertyHistoryPoint;
import com.mqtt.cloud.dto.response.PropertySeriesVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyHistory;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyHistoryMapper;
import com.mqtt.cloud.service.PropertyHistoryService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 属性时序实现（T-21 设计文档 §4.4 / §7.3 / §7.4）。
 * <p>
 * 写入链路（由解析链路的第 4 条旁路调用）：<b>总开关短路 → 组行 → 单条 {@code INSERT ... VALUES (...),(...)}</b>；
 * 历史为**纯追加**（无时间戳守卫、不去重），异常由调用方 {@code appendHistory} 的 {@code try/catch} 兜底。
 * <p>
 * 只读链路：<b>参数归一化 → 设备归属校验 → 标识符物模型校验 → 时间窗 / 桶校验 → 按 {@code numeric} 分流聚合
 * → 组装序列</b>。是否生成 {@code min/max/avg} 由物模型类型（{@code §4.4} 口径）判定后以 {@code numeric}
 * 传入 SQL，非数值类型以 {@code <otherwise>} 输出 {@code NULL}，避免对文本列做误导性 {@code CAST}。
 * <p>
 * 类型口径（{@code §7.3}）：同一请求内 {@code identifiers} 可能混合数值 / 非数值（看板面板通常单一属性，
 * 但接口允许混用），因此按 {@code numeric} 拆成两次 Mapper 调用后合并；类型以物模型为准，同标识符多设备
 * 取首个定义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyHistoryServiceImpl implements PropertyHistoryService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceMapper deviceMapper;
    private final DevicePropertyHistoryMapper historyMapper;
    private final ThingModelService thingModelService;
    private final PropertyHistoryProperties properties;

    @Override
    public void append(Long deviceId, List<Sample> samples) {
        if (!properties.isEnabled() || deviceId == null || samples == null || samples.isEmpty()) {
            return;
        }
        List<DevicePropertyHistory> rows = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            DevicePropertyHistory row = new DevicePropertyHistory();
            row.setDeviceId(deviceId);
            row.setIdentifier(sample.identifier());
            row.setDataType(sample.dataType());
            row.setValueText(sample.valueText());
            row.setReportedAt(sample.reportedAt());
            rows.add(row);
        }
        historyMapper.insertBatch(rows);
    }

    @Override
    public List<PropertySeriesVO> query(Long userId, PropertyHistoryQueryDTO query) {
        List<Long> deviceIds = normalizeDeviceIds(query);
        List<String> identifiers = normalizeIdentifiers(query);

        int maxSeries = maxSeries();
        if ((long) deviceIds.size() * identifiers.size() > maxSeries) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID,
                    "序列数（设备 × 属性）超过上限 " + maxSeries);
        }

        // 设备归属校验：不存在（含逻辑删除）→ 2002；非本人且非 ADMIN → 2003
        List<Device> devices = new ArrayList<>(deviceIds.size());
        for (Long deviceId : deviceIds) {
            devices.add(checkOwnership(deviceId, userId));
        }

        // 标识符物模型校验：任一设备缺该标识符即 6226；类型按标识符粒度取首个定义
        Map<Long, ThingModelDefinition> definitions = new LinkedHashMap<>();
        for (Device device : devices) {
            definitions.put(device.getId(), thingModelService.getForProduct(device.getProductId()));
        }
        Map<String, String> identifierTypes = new LinkedHashMap<>();
        for (String identifier : identifiers) {
            String type = null;
            for (Device device : devices) {
                ThingModelDefinition.PropertySpec spec = definitions.get(device.getId())
                        .properties().get(identifier);
                if (spec == null) {
                    throw new BusinessException(ResultCode.PROPERTY_HISTORY_IDENTIFIER_UNSUPPORTED,
                            "属性标识符未在设备的物模型中定义：" + identifier);
                }
                if (type == null) {
                    type = spec.type();
                }
            }
            identifierTypes.put(identifier, type);
        }

        LocalDateTime startTime = parseTime(query.getStartTime(), "startTime");
        LocalDateTime endTime = parseTime(query.getEndTime(), "endTime");
        validateRange(startTime, endTime);

        String bucket = normalizeBucket(query.getBucket());
        long bucketSeconds = PropertyHistoryConstants.BUCKET_SECONDS.get(bucket);
        validatePoints(startTime, endTime, bucketSeconds);

        // 按 numeric 分流：数值型 / 非数值型各一次，避免 <choose> 在同一条 SQL 内兼容混合类型
        List<String> numericIdentifiers = new ArrayList<>();
        List<String> nonNumericIdentifiers = new ArrayList<>();
        for (String identifier : identifiers) {
            if (isNumeric(identifierTypes.get(identifier))) {
                numericIdentifiers.add(identifier);
            } else {
                nonNumericIdentifiers.add(identifier);
            }
        }
        List<PropertyHistoryAggregate> aggregates = new ArrayList<>();
        if (!numericIdentifiers.isEmpty()) {
            aggregates.addAll(historyMapper.aggregateByBucket(
                    deviceIds, numericIdentifiers, startTime, endTime, bucketSeconds, true));
        }
        if (!nonNumericIdentifiers.isEmpty()) {
            aggregates.addAll(historyMapper.aggregateByBucket(
                    deviceIds, nonNumericIdentifiers, startTime, endTime, bucketSeconds, false));
        }

        return assemble(deviceIds, identifiers, identifierTypes, aggregates);
    }

    /** 组装序列：先按 {@code deviceId ASC, identifier ASC} 建全部组合（含无数据者），再挂载聚合点。 */
    private List<PropertySeriesVO> assemble(List<Long> deviceIds, List<String> identifiers,
                                            Map<String, String> identifierTypes,
                                            List<PropertyHistoryAggregate> aggregates) {
        Map<String, PropertySeriesVO> series = new LinkedHashMap<>();
        for (Long deviceId : deviceIds) {
            for (String identifier : identifiers) {
                PropertySeriesVO vo = new PropertySeriesVO();
                vo.setDeviceId(deviceId);
                vo.setIdentifier(identifier);
                String type = identifierTypes.get(identifier);
                vo.setDataType(type);
                vo.setNumeric(isNumeric(type));
                series.put(key(deviceId, identifier), vo);
            }
        }
        for (PropertyHistoryAggregate row : aggregates) {
            PropertySeriesVO vo = series.get(key(row.getDeviceId(), row.getIdentifier()));
            if (vo == null) {
                continue;
            }
            // SQL 已按 deviceId / identifier / time 升序，逐行追加即可保持点的时间升序
            vo.getPoints().add(toPoint(row));
        }
        return new ArrayList<>(series.values());
    }

    /** 投影 → 对外模型：{@code sampleCount→count}、{@code minValue→min} 等，隔离 SQL 命名与接口契约。 */
    private PropertyHistoryPoint toPoint(PropertyHistoryAggregate row) {
        PropertyHistoryPoint point = new PropertyHistoryPoint();
        point.setTime(row.getTime() == null ? null : DATE_TIME_FORMAT.format(row.getTime()));
        point.setCount(row.getSampleCount());
        point.setMin(toDouble(row.getMinValue()));
        point.setMax(toDouble(row.getMaxValue()));
        point.setAvg(toDouble(row.getAvgValue()));
        return point;
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String key(Long deviceId, String identifier) {
        return deviceId + "\u0000" + identifier;
    }

    /**
     * 设备级鉴权：不存在（含已逻辑删除）→ {@code 2002}；非本人设备且非 ADMIN → {@code 2003}。
     * 与 {@code DeviceLogServiceImpl#checkOwnership} 保持同一归属口径。
     */
    private Device checkOwnership(Long deviceId, Long userId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Integer.valueOf(1).equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        boolean isOwner = device.getOwnerId() != null && device.getOwnerId().equals(userId);
        if (!isOwner && !SecurityUtils.isAdmin()) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
        return device;
    }

    /** 设备列表：去重、去空；为空 → {@code 6225}。 */
    private List<Long> normalizeDeviceIds(PropertyHistoryQueryDTO query) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (query != null && query.getDeviceIds() != null) {
            for (Long deviceId : query.getDeviceIds()) {
                if (deviceId != null) {
                    distinct.add(deviceId);
                }
            }
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID, "deviceIds 不能为空");
        }
        return new ArrayList<>(distinct);
    }

    /** 标识符列表：去空白、去重；为空 → {@code 6226}。 */
    private List<String> normalizeIdentifiers(PropertyHistoryQueryDTO query) {
        Set<String> distinct = new LinkedHashSet<>();
        if (query != null && query.getIdentifiers() != null) {
            for (String identifier : query.getIdentifiers()) {
                if (identifier != null && !identifier.isBlank()) {
                    distinct.add(identifier.trim());
                }
            }
        }
        if (distinct.isEmpty()) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_IDENTIFIER_UNSUPPORTED, "identifiers 不能为空");
        }
        return new ArrayList<>(distinct);
    }

    /** 桶白名单：空白取默认桶；非白名单 → {@code 6227}。 */
    private String normalizeBucket(String rawBucket) {
        if (rawBucket == null || rawBucket.isBlank()) {
            return PropertyHistoryConstants.DEFAULT_BUCKET;
        }
        String bucket = rawBucket.trim().toLowerCase(Locale.ROOT);
        if (!PropertyHistoryConstants.SUPPORTED_BUCKETS.contains(bucket)) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_BUCKET_UNSUPPORTED, "不支持的时间桶：" + rawBucket);
        }
        return bucket;
    }

    /** 解析时间：优先 {@code yyyy-MM-dd HH:mm:ss}，回退 ISO-8601（容忍尾部 {@code Z}）；缺失 / 非法 → {@code 6225}。 */
    private LocalDateTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID, field + " 不能为空");
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
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID,
                    field + " 时间格式非法，应为 yyyy-MM-dd HH:mm:ss 或 ISO-8601");
        }
    }

    /** 时间窗：半开区间 {@code [start, end)}，须 {@code start < end} 且跨度不超过 {@code max-range-days}。 */
    private void validateRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID, "startTime 必须早于 endTime");
        }
        int maxRangeDays = maxRangeDays();
        if (endTime.isAfter(startTime.plusDays(maxRangeDays))) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_RANGE_INVALID,
                    "查询时间跨度超过上限 " + maxRangeDays + " 天");
        }
    }

    /** 桶数校验：{@code ceil(跨度秒 / 桶秒)} 超 {@code max-points} → {@code 6227}。 */
    private void validatePoints(LocalDateTime startTime, LocalDateTime endTime, long bucketSeconds) {
        long spanSeconds = Duration.between(startTime, endTime).getSeconds();
        long buckets = spanSeconds / bucketSeconds + (spanSeconds % bucketSeconds == 0 ? 0 : 1);
        int maxPoints = maxPoints();
        if (buckets > maxPoints) {
            throw new BusinessException(ResultCode.PROPERTY_HISTORY_BUCKET_UNSUPPORTED,
                    "数据点过多（" + buckets + " > " + maxPoints + "），请增大时间桶或缩小时间范围");
        }
    }

    private boolean isNumeric(String type) {
        return type != null && PropertyHistoryConstants.NUMERIC_TYPES.contains(type.toLowerCase(Locale.ROOT));
    }

    private int maxRangeDays() {
        int configured = properties.getMaxRangeDays();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_RANGE_DAYS;
    }

    private int maxSeries() {
        int configured = properties.getMaxSeries();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_SERIES;
    }

    private int maxPoints() {
        int configured = properties.getMaxPoints();
        return configured > 0 ? configured : PropertyHistoryConstants.DEFAULT_MAX_POINTS;
    }
}
