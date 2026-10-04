package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.request.SceneTestRequest;
import com.mqtt.cloud.dto.response.SceneTestResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.SceneTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 场景试运行实现（T-23 设计文档 §7.5 / §10.2）。
 * <p>
 * 干跑**无副作用**：只读场景 / 步骤 / 属性最新值，判定触发 → 条件组 → 冷却，并统计步骤摘要与预计耗时，
 * 全程不落库、不投递、不占用冷却锚点。匹配复用 {@link SceneMatcher}。
 * <p>
 * {@code EVENT} 源在试运行中把请求 {@code value} 视为事件类型（场景未配置类型过滤时忽略），
 * 以便对带事件类型过滤的场景做干跑验证；{@code TIMER} 源用当前分钟判定 cron 是否命中。
 */
@Slf4j
@Service
public class SceneTestServiceImpl implements SceneTestService {

    private final SceneDefinitionMapper definitionMapper;
    private final SceneStepMapper stepMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceBatchService deviceBatchService;
    private final SceneMatcher sceneMatcher;
    private final SceneCooldownRegistry cooldownRegistry;
    private final ObjectMapper objectMapper;

    public SceneTestServiceImpl(SceneDefinitionMapper definitionMapper,
                                SceneStepMapper stepMapper,
                                DeviceMapper deviceMapper,
                                DeviceBatchService deviceBatchService,
                                SceneMatcher sceneMatcher,
                                SceneCooldownRegistry cooldownRegistry,
                                ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.stepMapper = stepMapper;
        this.deviceMapper = deviceMapper;
        this.deviceBatchService = deviceBatchService;
        this.sceneMatcher = sceneMatcher;
        this.cooldownRegistry = cooldownRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public SceneTestResult test(Long userId, Long sceneId, SceneTestRequest request) {
        SceneDefinition scene = getOwned(userId, sceneId);
        SceneTestRequest effective = request == null ? new SceneTestRequest() : request;
        checkDeviceOwned(userId, effective.getDeviceId());

        boolean triggerMatched = matchTrigger(scene, effective);
        if (!triggerMatched) {
            return new SceneTestResult(false, false, false, List.of(), List.of(), 0);
        }

        List<SceneTestResult.ConditionEvaluation> conditions =
                sceneMatcher.evaluateConditions(scene, effective.getDeviceId());
        boolean conditionMatched = sceneMatcher.conditionsSatisfied(conditions, scene.getConditionLogic());
        boolean cooldownBlocked = cooldownRegistry.isBlocked(scene.getId(),
                scene.getCooldownSeconds() == null ? 0 : scene.getCooldownSeconds());

        List<SceneStep> steps = stepMapper.selectBySceneId(sceneId).stream()
                .filter(step -> step.getEnabled() == null || step.getEnabled() == 1)
                .toList();
        List<SceneTestResult.StepSummary> summaries = new ArrayList<>(steps.size());
        int estimated = 0;
        for (SceneStep step : steps) {
            int delay = step.getDelaySeconds() == null ? 0 : step.getDelaySeconds();
            estimated += delay;
            summaries.add(summarize(userId, step, effective.getDeviceId(), delay));
        }
        return new SceneTestResult(true, conditionMatched, cooldownBlocked, conditions, summaries, estimated);
    }

    /** 触发匹配：PROPERTY / EVENT 需设备 + 标识符；TIMER 用当前分钟判定 cron。 */
    private boolean matchTrigger(SceneDefinition scene, SceneTestRequest request) {
        String triggerType = scene.getTriggerType();
        if (SceneConstants.TRIGGER_TIMER.equals(triggerType)) {
            return sceneMatcher.matchesTimerTrigger(scene, LocalDateTime.now(sceneMatcher.timerZone()));
        }
        if (request.getDeviceId() == null || isBlank(request.getIdentifier())) {
            throw new BusinessException(ResultCode.SCENE_INVALID, "非定时场景试运行需提供 deviceId 与 identifier");
        }
        if (SceneConstants.TRIGGER_PROPERTY.equals(triggerType)) {
            return sceneMatcher.matchesPropertyTrigger(scene, request.getDeviceId(),
                    request.getIdentifier(), request.getValue());
        }
        if (SceneConstants.TRIGGER_EVENT.equals(triggerType)) {
            return sceneMatcher.matchesEventTrigger(scene, request.getDeviceId(),
                    request.getIdentifier(), request.getValue());
        }
        throw new BusinessException(ResultCode.SCENE_TRIGGER_UNSUPPORTED);
    }

    /** 步骤摘要：目标设备数（转发类恒 0）+ 可读说明。 */
    private SceneTestResult.StepSummary summarize(Long userId, SceneStep step, Long triggerDeviceId, int delay) {
        String actionType = step.getActionType();
        int targetCount = targetCount(userId, step, triggerDeviceId);
        JsonNode config = readConfig(step.getActionConfig());
        String summary = describe(actionType, config, targetCount);
        return new SceneTestResult.StepSummary(step.getSeq(), actionType, step.getTargetType(),
                targetCount, delay, summary);
    }

    /** 目标设备数：转发类单次出站恒 0；{@code TRIGGER} 有触发设备为 1；{@code FIXED} 解析目标集合（失败记 0）。 */
    private int targetCount(Long userId, SceneStep step, Long triggerDeviceId) {
        if (SceneConstants.FORWARD_ACTIONS.contains(step.getActionType())) {
            return 0;
        }
        if (SceneConstants.TARGET_TRIGGER.equals(step.getTargetType())) {
            return triggerDeviceId == null ? 0 : 1;
        }
        BatchTargetRequest target = readTarget(step.getTargetConfig());
        if (target == null) {
            return 0;
        }
        try {
            List<Device> devices = deviceBatchService.resolveTarget(userId, target);
            return devices == null ? 0 : devices.size();
        } catch (Exception e) {
            log.debug("试运行目标解析失败，按 0 台展示: sceneStepId={}", step.getId(), e);
            return 0;
        }
    }

    private String describe(String actionType, JsonNode config, int targetCount) {
        return switch (actionType) {
            case SceneConstants.ACTION_UPDATE_PROPERTY ->
                    "更新属性 " + nullToEmpty(text(config, "identifier")) + " → " + targetCount + " 台设备";
            case SceneConstants.ACTION_SEND_COMMAND ->
                    nullToEmpty(text(config, "commandType")) + " " + nullToEmpty(text(config, "identifier"))
                            + " → " + targetCount + " 台设备";
            case SceneConstants.ACTION_FORWARD_MQTT ->
                    "转发到 topic " + nullToEmpty(text(config, "topic"));
            case SceneConstants.ACTION_FORWARD_HTTP ->
                    "转发到 " + nullToEmpty(text(config, "method")) + " " + nullToEmpty(text(config, "url"));
            default -> actionType;
        };
    }

    private SceneDefinition getOwned(Long userId, Long sceneId) {
        if (sceneId == null) {
            throw new BusinessException(ResultCode.SCENE_NOT_FOUND);
        }
        SceneDefinition scene = definitionMapper.selectById(sceneId);
        if (scene == null) {
            throw new BusinessException(ResultCode.SCENE_NOT_FOUND);
        }
        if (!userId.equals(scene.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return scene;
    }

    private void checkDeviceOwned(Long userId, Long deviceId) {
        if (deviceId == null) {
            return;
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || !userId.equals(device.getOwnerId())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
    }

    private JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            log.debug("试运行动作配置解析失败: {}", e.getMessage());
            return null;
        }
    }

    private BatchTargetRequest readTarget(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BatchTargetRequest.class);
        } catch (Exception e) {
            log.debug("试运行目标配置解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return null;
        }
        return value.asText();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}