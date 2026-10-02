package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.config.ShadowProperties;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.DeviceShadow;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.DeviceShadowMapper;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.ThingModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 设备影子实现（T-16 设计文档 §8）。
 * <p>
 * 写入统一走「懒创建 → 读 → 应用侧合并 → 版本 CAS」，CAS 冲突时重读重算，最多重试
 * {@code app.shadow.cas-retry} 次；耗尽仅记 WARN 放弃本次合并，不抛出、不阻断主链路。
 * 本类<b>不开启事务</b>，让影子合并与命令迁移各自独立提交，避免并发下长事务持锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceShadowServiceImpl implements DeviceShadowService {

    private final DeviceShadowMapper shadowMapper;
    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final ThingModelService thingModelService;
    private final ShadowProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public DeviceShadowResponse get(Long deviceId, Long productId) {
        boolean modeled = isModeled(productId);
        if (deviceId == null) {
            return new DeviceShadowResponse(modeled, 0L, Map.of(), Map.of(), Map.of(), null);
        }
        DeviceShadow shadow = loadOrCreate(deviceId);
        if (shadow == null) {
            return new DeviceShadowResponse(modeled, 0L, Map.of(), Map.of(), Map.of(), null);
        }
        return new DeviceShadowResponse(modeled, versionOf(shadow),
                readMap(shadow.getDesired()), readMap(shadow.getReported()), readMap(shadow.getDelta()),
                shadow.getUpdatedAt());
    }

    @Override
    public void applyReported(Long deviceId, Collection<String> identifiers) {
        if (deviceId == null || identifiers == null || identifiers.isEmpty()) {
            return;
        }
        Map<String, String> authoritative = loadAuthoritative(deviceId, identifiers);
        if (authoritative.isEmpty()) {
            return;
        }
        merge(deviceId, state -> state.reported.putAll(authoritative));
    }

    @Override
    public void applyDesired(Long deviceId, Map<String, String> desired) {
        if (deviceId == null || desired == null || desired.isEmpty()) {
            return;
        }
        Map<String, String> normalized = new LinkedHashMap<>(desired);
        merge(deviceId, state -> state.desired.putAll(normalized));
    }

    // ---------- 读-改-写 + CAS ----------

    /**
     * 读-改-写 + 版本 CAS。CAS 冲突（受影响行数为 0）时重读重算，最多重试 {@code cas-retry} 次；
     * 耗尽后记 WARN 放弃（下一次上报 / 写入自然覆盖）。
     */
    private void merge(Long deviceId, Consumer<State> mutator) {
        int maxAttempts = Math.max(1, properties.getCasRetry());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DeviceShadow current = loadOrCreate(deviceId);
            if (current == null) {
                log.warn("影子合并放弃：影子行不可用, deviceId={}", deviceId);
                return;
            }
            State state = new State(readMap(current.getDesired()), readMap(current.getReported()));
            mutator.accept(state);
            Map<String, String> delta = computeDelta(state.desired, state.reported);
            int updated = shadowMapper.casUpdate(deviceId, writeJson(state.desired),
                    writeJson(state.reported), writeJson(delta), versionOf(current), LocalDateTime.now());
            if (updated > 0) {
                return;
            }
        }
        log.warn("影子合并 CAS 冲突重试耗尽，放弃本次合并: deviceId={}", deviceId);
    }

    /** 懒创建（幂等）后读取影子行；返回 {@code null} 表示行仍不可用。 */
    private DeviceShadow loadOrCreate(Long deviceId) {
        shadowMapper.insertIfAbsent(deviceId, LocalDateTime.now());
        return shadowMapper.selectOne(Wrappers.<DeviceShadow>lambdaQuery()
                .eq(DeviceShadow::getDeviceId, deviceId));
    }

    /**
     * 回读权威库：给定标识符的最新值取自 {@code device_property_latest}，
     * 乱序旧包已在该表的时间戳守卫处被丢弃，故此处天然为权威值。
     */
    private Map<String, String> loadAuthoritative(Long deviceId, Collection<String> identifiers) {
        List<DevicePropertyLatest> rows = propertyLatestMapper.selectByIdentifiers(deviceId, identifiers);
        Map<String, String> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (DevicePropertyLatest row : rows) {
            if (row.getIdentifier() != null && row.getValueText() != null) {
                result.put(row.getIdentifier(), row.getValueText());
            }
        }
        return result;
    }

    /**
     * delta = { k: desired[k] | k ∈ desired 且 (k ∉ reported 或 desired[k] ≠ reported[k]) }。
     * <p>
     * 两侧均为归一化文本，字符串相等即可判定一致，不会出现 {@code 25} 与 {@code 25.0} 之类的伪差异。
     */
    private Map<String, String> computeDelta(Map<String, String> desired, Map<String, String> reported) {
        Map<String, String> delta = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            String key = entry.getKey();
            String want = entry.getValue();
            String actual = reported.get(key);
            if (actual == null || !actual.equals(want)) {
                delta.put(key, want);
            }
        }
        return delta;
    }

    private boolean isModeled(Long productId) {
        return productId != null && !thingModelService.getForProduct(productId).isEmpty();
    }

    private long versionOf(DeviceShadow shadow) {
        return shadow.getVersion() == null ? 0L : shadow.getVersion();
    }

    /** JSON 文本 → 有序 {@code identifier -> 文本} 映射；空 / 非法一律视为空映射。 */
    private Map<String, String> readMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return map;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                return map;
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                    map.put(entry.getKey(), value.asText());
                }
            }
        } catch (Exception e) {
            log.warn("影子 JSON 解析失败，按空处理: {}", json, e);
        }
        return map;
    }

    private String writeJson(Map<String, String> map) {
        ObjectNode node = objectMapper.createObjectNode();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            node.put(entry.getKey(), entry.getValue());
        }
        return node.toString();
    }

    /** 合并过程中的可变工作副本。 */
    private static final class State {
        private final Map<String, String> desired;
        private final Map<String, String> reported;

        private State(Map<String, String> desired, Map<String, String> reported) {
            this.desired = desired;
            this.reported = reported;
        }
    }
}
