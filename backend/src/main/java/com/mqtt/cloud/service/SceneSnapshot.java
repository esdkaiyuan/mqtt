package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景完整快照（T-51）。
 * <p>
 * 只保存可恢复的配置字段：不含主键、时间戳、执行记录等运行时数据。
 * 步骤顺序按传入的 {@code seq} 升序由调用方保证。
 */
public record SceneSnapshot(String name,
                            String description,
                            String triggerType,
                            Long triggerDeviceId,
                            String triggerIdentifier,
                            String triggerOperator,
                            String triggerThreshold,
                            String triggerEventType,
                            String triggerShadowSide,
                            String timerCron,
                            String conditionLogic,
                            String conditionConfig,
                            Integer cooldownSeconds,
                            Integer enabled,
                            List<StepSnapshot> steps) {

    public SceneSnapshot {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static SceneSnapshot of(SceneDefinition scene, List<SceneStep> steps) {
        List<StepSnapshot> stepSnapshots = new ArrayList<>();
        if (steps != null) {
            for (SceneStep step : steps) {
                stepSnapshots.add(new StepSnapshot(
                        step.getSeq(), step.getDelaySeconds(), step.getActionType(), step.getTargetType(),
                        step.getTargetConfig(), step.getActionConfig(), step.getParallelSeq(),
                        step.getBranchIndex(), step.getEnabled()));
            }
        }
        return new SceneSnapshot(
                scene.getName(), scene.getDescription(), scene.getTriggerType(), scene.getTriggerDeviceId(),
                scene.getTriggerIdentifier(), scene.getTriggerOperator(), scene.getTriggerThreshold(),
                scene.getTriggerEventType(), scene.getTriggerShadowSide(), scene.getTimerCron(),
                scene.getConditionLogic(), scene.getConditionConfig(), scene.getCooldownSeconds(),
                scene.getEnabled(), stepSnapshots);
    }

    public SceneDefinition toDefinition(Long sceneId, Long userId) {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(sceneId);
        scene.setUserId(userId);
        scene.setName(name);
        scene.setDescription(description);
        scene.setTriggerType(triggerType);
        scene.setTriggerDeviceId(triggerDeviceId);
        scene.setTriggerIdentifier(triggerIdentifier);
        scene.setTriggerOperator(triggerOperator);
        scene.setTriggerThreshold(triggerThreshold);
        scene.setTriggerEventType(triggerEventType);
        scene.setTriggerShadowSide(triggerShadowSide);
        scene.setTimerCron(timerCron);
        scene.setConditionLogic(conditionLogic);
        scene.setConditionConfig(conditionConfig);
        scene.setCooldownSeconds(cooldownSeconds);
        scene.setEnabled(enabled);
        return scene;
    }

    public List<SceneStep> toSteps(Long sceneId) {
        List<SceneStep> result = new ArrayList<>(steps.size());
        for (StepSnapshot step : steps) {
            SceneStep entity = new SceneStep();
            entity.setSceneId(sceneId);
            entity.setSeq(step.seq());
            entity.setDelaySeconds(step.delaySeconds());
            entity.setActionType(step.actionType());
            entity.setTargetType(step.targetType());
            entity.setTargetConfig(step.targetConfig());
            entity.setActionConfig(step.actionConfig());
            entity.setParallelSeq(step.parallelSeq());
            entity.setBranchIndex(step.branchIndex());
            entity.setEnabled(step.enabled());
            result.add(entity);
        }
        return result;
    }

    public record StepSnapshot(Integer seq,
                               Integer delaySeconds,
                               String actionType,
                               String targetType,
                               String targetConfig,
                               String actionConfig,
                               Integer parallelSeq,
                               Integer branchIndex,
                               Integer enabled) {
    }
}
