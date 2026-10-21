package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.event.DeviceStatusChangedEvent;
import com.mqtt.cloud.service.SceneEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 设备上下线 → 场景触发桥接（T-43）。
 * <p>
 * 用事务提交后事件而非直接注入 {@link SceneEvaluationService}，避免设备状态写入路径与场景引擎
 * 形成同步耦合：状态落库回滚时不会触发场景，场景评估的耗时也不占用状态更新事务。
 * 无事务调用（如单测或非事务入口）通过 {@code fallbackExecution} 立即执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneStatusTriggerListener {

    private final SceneEvaluationService sceneEvaluationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceStatusChanged(DeviceStatusChangedEvent event) {
        try {
            sceneEvaluationService.onStatus(event.deviceId(), event.status(), event.occurredAt());
        } catch (Exception e) {
            // 事件监听器运行在状态写入线程上，异常不得冒泡影响设备在线状态链路
            log.warn("场景上下线触发处理异常，跳过: deviceId={}, status={}",
                    event.deviceId(), event.status(), e);
        }
    }
}
