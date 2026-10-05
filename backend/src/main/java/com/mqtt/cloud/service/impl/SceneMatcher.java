package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.response.SceneTestResult;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.util.SceneCronSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 场景触发匹配与条件组判定（T-23 设计文档 §8.2 / §8.3）。
 * <p>
 * 供 {@code SceneEvaluationServiceImpl}（上行匹配）、{@code SceneTimerSweeperServiceImpl}（定时匹配）
 * 与 {@code SceneTestServiceImpl}（试运行干跑）共用，避免同一份口径在三处各自实现而产生漂移。
 * <p>
 * 比较口径与 T-17 / T-19 **完全一致**（复用 {@link RuleConditionMatcher}）：数值比较符按
 * {@code BigDecimal.compareTo}，{@code EQ}/{@code NE} 按 {@code trim()} 文本；任一侧不可解析为数值
 * → 该条件项判为**不满足**（跳过，不报错）。
 */
@Slf4j
@Component
public class SceneMatcher {

    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final SceneProperties properties;
    private final ObjectMapper objectMapper;

    public SceneMatcher(DevicePropertyLatestMapper propertyLatestMapper,
                        SceneProperties properties,
                        ObjectMapper objectMapper) {
        this.propertyLatestMapper = propertyLatestMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ---------- 触发匹配（§8.2） ----------

    /**
     * 属性触发匹配：作用域 → 标识符 → 触发条件判定。
     *
     * @return 命中返回 {@code true}
     */
    public boolean matchesPropertyTrigger(SceneDefinition scene, Long deviceId,
                                          String identifier, String valueText) {
        if (!inScope(scene, deviceId)) {
            return false;
        }
        if (scene.getTriggerIdentifier() == null || !scene.getTriggerIdentifier().equals(identifier)) {
            return false;
        }
        if (scene.getTriggerOperator() == null) {
            return true;
        }
        return Boolean.TRUE.equals(RuleConditionMatcher.matchesProperty(
                valueText, scene.getTriggerOperator(), scene.getTriggerThreshold()));
    }

    /** 事件触发匹配：作用域 → 标识符 → 事件类型过滤（场景为空则不限）。 */
    public boolean matchesEventTrigger(SceneDefinition scene, Long deviceId,
                                       String identifier, String eventType) {
        if (!inScope(scene, deviceId)) {
            return false;
        }
        if (scene.getTriggerIdentifier() == null || !scene.getTriggerIdentifier().equals(identifier)) {
            return false;
        }
        return scene.getTriggerEventType() == null || scene.getTriggerEventType().equals(eventType);
    }

    /** 定时触发匹配：cron 的下一次触发时刻落在当前分钟（按 {@code app.scene.timer-zone}）。 */
    public boolean matchesTimerTrigger(SceneDefinition scene, LocalDateTime now) {
        String cron = scene.getTimerCron();
        if (cron == null || cron.isBlank()) {
            return false;
        }
        try {
            CronExpression expression = SceneCronSupport.parse(cron);
            ZonedDateTime minuteFloor = now.atZone(resolveZone()).truncatedTo(ChronoUnit.MINUTES);
            ZonedDateTime next = expression.next(minuteFloor.minusMinutes(1));
            return next != null && next.truncatedTo(ChronoUnit.MINUTES).equals(minuteFloor);
        } catch (Exception e) {
            log.warn("定时场景 cron 判定失败，跳过: sceneId={}, cron={}", scene.getId(), cron, e);
            return false;
        }
    }

    private boolean inScope(SceneDefinition scene, Long deviceId) {
        return scene.getTriggerDeviceId() == null || scene.getTriggerDeviceId().equals(deviceId);
    }

    /** 定时触发使用的时区（{@code app.scene.timer-zone}，非法值回退 {@code Asia/Shanghai}）。 */
    public ZoneId timerZone() {
        return resolveZone();
    }

    // ---------- 条件组判定（§8.3） ----------

    /**
     * 逐项判定条件组，返回每项的取值与满足情况（供试运行诊断复用）。
     * <p>
     * 条件项 {@code deviceId} 为空时取触发设备；触发设备也为空（不应出现）时该条件项不满足。
     * 值不存在（设备无该属性最新值）→ 该项不满足。
     */
    public List<SceneTestResult.ConditionEvaluation> evaluateConditions(SceneDefinition scene, Long triggerDeviceId) {
        List<ConditionSpec> specs = parseConditions(scene.getConditionConfig());
        if (specs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> identifiersByDevice = new LinkedHashMap<>();
        for (ConditionSpec spec : specs) {
            Long deviceId = spec.deviceId() != null ? spec.deviceId() : triggerDeviceId;
            if (deviceId == null) {
                continue;
            }
            identifiersByDevice.computeIfAbsent(deviceId, key -> new ArrayList<>()).add(spec.identifier());
        }

        Map<String, String> latestByKey = new LinkedHashMap<>();
        for (Map.Entry<Long, List<String>> entry : identifiersByDevice.entrySet()) {
            List<String> identifiers = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            try {
                List<DevicePropertyLatest> rows =
                        propertyLatestMapper.selectByIdentifiers(entry.getKey(), identifiers);
                if (rows != null) {
                    for (DevicePropertyLatest row : rows) {
                        latestByKey.put(key(entry.getKey(), row.getIdentifier()), row.getValueText());
                    }
                }
            } catch (Exception e) {
                log.warn("场景条件组批量取值失败，按不满足处理: deviceId={}", entry.getKey(), e);
            }
        }

        List<SceneTestResult.ConditionEvaluation> result = new ArrayList<>(specs.size());
        for (ConditionSpec spec : specs) {
            Long deviceId = spec.deviceId() != null ? spec.deviceId() : triggerDeviceId;
            String actualValue = deviceId == null ? null : latestByKey.get(key(deviceId, spec.identifier()));
            boolean satisfied = Boolean.TRUE.equals(RuleConditionMatcher.matchesProperty(
                    actualValue, spec.operator(), spec.threshold()));
            result.add(new SceneTestResult.ConditionEvaluation(
                    spec.identifier(), spec.operator(), spec.threshold(), actualValue, satisfied));
        }
        return result;
    }

    /** 条件组求值：空组视为满足；{@code AND} 全满足；{@code OR} 任一满足。 */
    public boolean conditionsSatisfied(List<SceneTestResult.ConditionEvaluation> evaluations, String logic) {
        if (evaluations == null || evaluations.isEmpty()) {
            return true;
        }
        boolean or = SceneConstants.LOGIC_OR.equals(logic);
        for (SceneTestResult.ConditionEvaluation evaluation : evaluations) {
            if (or && evaluation.isSatisfied()) {
                return true;
            }
            if (!or && !evaluation.isSatisfied()) {
                return false;
            }
        }
        return !or;
    }

    /** 解析条件组 JSON（库中文本）为内部规格；非法内容按空组处理（保存期已校验）。 */
    public List<ConditionSpec> parseConditions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<ConditionSpec> specs = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String identifier = text(node, "identifier");
                String operator = text(node, "operator");
                String threshold = text(node, "threshold");
                if (identifier == null || operator == null || threshold == null) {
                    continue;
                }
                Long deviceId = node.get("deviceId") != null && node.get("deviceId").isNumber()
                        ? node.get("deviceId").asLong() : null;
                specs.add(new ConditionSpec(deviceId, identifier, operator, threshold));
            }
            return specs;
        } catch (Exception e) {
            log.warn("场景条件组解析失败，按无附加条件处理: {}", e.getMessage());
            return List.of();
        }
    }

    private ZoneId resolveZone() {
        String zone = properties.getTimerZone();
        try {
            return (zone == null || zone.isBlank()) ? ZoneId.of("Asia/Shanghai") : ZoneId.of(zone);
        } catch (Exception e) {
            log.warn("场景定时时区非法，回退 Asia/Shanghai: {}", zone);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private static String key(Long deviceId, String identifier) {
        return deviceId + "|" + identifier;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    /** 条件项内部规格。 */
    public record ConditionSpec(Long deviceId, String identifier, String operator, String threshold) {
    }
}