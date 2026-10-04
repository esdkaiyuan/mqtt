package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.SceneTestRequest;
import com.mqtt.cloud.dto.response.SceneTestResult;

/**
 * 场景试运行服务（T-23 设计文档 §7.5 / §10.2）。
 * <p>
 * 用给定的设备 / 标识符 / 取值**干跑**判定触发与条件组是否命中、是否被冷却拦截，并产出步骤摘要；
 * **不落库、不投递任何动作、不占用冷却锚点**。匹配口径复用 {@code SceneMatcher}，与自动 / 定时触发同一份逻辑。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
public interface SceneTestService {

    /**
     * 试运行：场景须属当前用户（{@code 6240} / {@code 403}），非 {@code TIMER} 源须提供
     * {@code deviceId} + {@code identifier}（{@code 6241}），设备须属当前用户（{@code 2003}）。
     */
    SceneTestResult test(Long userId, Long sceneId, SceneTestRequest request);
}