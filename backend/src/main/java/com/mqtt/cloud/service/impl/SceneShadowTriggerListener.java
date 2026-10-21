package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.event.DeviceShadowChangedEvent;
import com.mqtt.cloud.service.SceneEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 设备影子变更 → 场景触发桥接（T-45）。
 * <p>
 * 与上下线触发同一模式：事务内发布的变更在提交后消费，回滚不触发；无事务（摄取上报后的影子收敛）
 * 通过 {@code fallbackExecution} 立即执行。场景评估在独立事务中运行，异常不冒泡到写入线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneShadowTriggerListener {

    private final SceneEvaluationService sceneEvaluationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onShadowChanged(DeviceShadowChangedEvent event) {
        if (SceneEvaluationServiceImpl.internalDesiredSource(event.side(), event.source())) {
            log.debug("影子 desired 变更来自内部自动化，跳过场景触发: deviceId={}, source={}",
                    event.deviceId(), event.source());
            return;
        }
        try {
            sceneEvaluationService.onShadow(event.deviceId(), event.side(), event.changes(),
                    event.source(), event.occurredAt());
        } catch (Exception e) {
            log.warn("场景影子变更触发处理异常，跳过: deviceId={}, side={}",
                    event.deviceId(), event.side(), e);
        }
    }
}
