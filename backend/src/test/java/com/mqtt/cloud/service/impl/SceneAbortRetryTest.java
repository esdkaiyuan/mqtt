package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneExecution;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.entity.SceneStepRun;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneExecutionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.mapper.SceneStepRunMapper;
import com.mqtt.cloud.mqtt.RuleMqttForwarder;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.RuleHttpForwarder;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 场景执行「重试与中止」收口单测（T-23 设计文档 §14.1 / §8.8 / §8.9）。
 * <p>
 * 与 {@code SceneActionExecutorImplTest}（以 mock 断言 {@code finalizer.abort} 入参）互补，
 * 本类聚焦**中止口径本身**与**重试→中止的衔接**两个正交关注点：
 * <ul>
 *     <li><b>中止收口三段式</b>：{@link SceneExecutionFinalizer#abort} 把失败步骤置 {@code FAILED}、
 *         把该执行下仍 {@code PENDING} 的后续步骤置 {@code SKIPPED} 并计入进度、最后把执行置 {@code FAILED}
 *         且失败原因组合为「第 N 步失败：原因」；步骤与执行的终态时刻取同一 {@code now}。</li>
 *     <li><b>异常隔离</b>：任一步骤抛异常都只记 WARN，绝不上抛给调用方（巡检 / 执行线程不应因收口失败而中断）。</li>
 *     <li><b>重试→中止衔接</b>：以**真实** {@link SceneExecutionFinalizer} 注入 {@link SceneActionExecutorImpl}，
 *         断言「尚有余额 → 仅 {@code markRetry}，执行仍在运行」与「余额耗尽 → 步骤失败 + 兄弟跳过 + 执行失败」。</li>
 * </ul>
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneAbortRetryTest {

    private static final Long USER_ID = 10L;
    private static final Long SCENE_ID = 5L;
    private static final Long STEP_ID = 50L;
    private static final Long STEP_RUN_ID = 500L;
    private static final Long PENDING_SIBLING_ID = 501L;
    private static final Long EXECUTION_ID = 900L;
    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    private SceneStepRunMapper stepRunMapper;
    private SceneExecutionMapper executionMapper;
    private SceneStepMapper stepMapper;
    private SceneDefinitionMapper definitionMapper;
    private DeviceMapper deviceMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private DeviceCommandService commandService;
    private ThingModelService thingModelService;
    private DeviceBatchService deviceBatchService;
    private RuleMqttForwarder mqttForwarder;
    private RuleHttpForwarder httpForwarder;
    private SceneProperties properties;
    private SceneExecutionFinalizer finalizer;
    private SceneActionExecutorImpl executor;

    @BeforeEach
    void setUp() {
        stepRunMapper = mock(SceneStepRunMapper.class);
        executionMapper = mock(SceneExecutionMapper.class);
        stepMapper = mock(SceneStepMapper.class);
        definitionMapper = mock(SceneDefinitionMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        commandService = mock(DeviceCommandService.class);
        thingModelService = mock(ThingModelService.class);
        deviceBatchService = mock(DeviceBatchService.class);
        mqttForwarder = mock(RuleMqttForwarder.class);
        httpForwarder = mock(RuleHttpForwarder.class);
        properties = new SceneProperties();
        // 真实中止收口：本类要断言的是收口产生的 mapper 副作用，而非对 finalizer 的调用次数
        finalizer = new SceneExecutionFinalizer(stepRunMapper, executionMapper);
        executor = new SceneActionExecutorImpl(stepRunMapper, stepMapper, definitionMapper, executionMapper,
                deviceMapper, propertyLatestMapper, commandService, thingModelService, deviceBatchService,
                mqttForwarder, httpForwarder, finalizer, properties, new RuleProperties(), new ObjectMapper(),
                mock(ThreadPoolTaskExecutor.class), new SimpleMeterRegistry());
    }

    // ---------- 中止收口三段式 ----------

    @Test
    void abort_should_mark_failed_skip_pending_siblings_and_fail_execution() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 2, 3);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(
                sibling(499L, 1, SceneConstants.STEP_SUCCESS),
                sibling(PENDING_SIBLING_ID, 3, SceneConstants.STEP_PENDING),
                sibling(502L, 4, SceneConstants.STEP_PENDING)));
        when(stepRunMapper.markSkipped(eq(PENDING_SIBLING_ID), any(LocalDateTime.class))).thenReturn(1);
        when(stepRunMapper.markSkipped(eq(502L), any(LocalDateTime.class))).thenReturn(1);

        finalizer.abort(failed, 3, "连续失败");

        ArgumentCaptor<LocalDateTime> stepTs = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).markFailed(eq(STEP_RUN_ID), eq(3), eq("连续失败"), stepTs.capture());
        verify(stepRunMapper).markSkipped(eq(PENDING_SIBLING_ID), any(LocalDateTime.class));
        verify(stepRunMapper).markSkipped(eq(502L), any(LocalDateTime.class));
        verify(executionMapper).bumpFinishedSteps(EXECUTION_ID, 2);

        ArgumentCaptor<LocalDateTime> execTs = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).markFailed(eq(EXECUTION_ID), eq("第 2 步失败：连续失败"), execTs.capture());
        // 步骤与执行的终态时刻同源
        assertThat(stepTs.getValue()).isEqualTo(execTs.getValue());
    }

    @Test
    void abort_should_not_skip_success_siblings_nor_bump_progress() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 3, 2);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(
                sibling(499L, 1, SceneConstants.STEP_SUCCESS),
                sibling(498L, 2, SceneConstants.STEP_FAILED)));

        finalizer.abort(failed, 2, "boom");

        verify(stepRunMapper, never()).markSkipped(any(), any(LocalDateTime.class));
        verify(executionMapper, never()).bumpFinishedSteps(any(), anyInt());
        verify(executionMapper).markFailed(eq(EXECUTION_ID), eq("第 3 步失败：boom"), any(LocalDateTime.class));
    }

    @Test
    void abort_should_count_only_siblings_whose_guard_hits() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 1, 2);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(
                sibling(PENDING_SIBLING_ID, 2, SceneConstants.STEP_PENDING),
                sibling(502L, 3, SceneConstants.STEP_PENDING)));
        // 首条抢到（1），次条已被其它副本处理（0）→ 仅计 1
        when(stepRunMapper.markSkipped(eq(PENDING_SIBLING_ID), any(LocalDateTime.class))).thenReturn(1);
        when(stepRunMapper.markSkipped(eq(502L), any(LocalDateTime.class))).thenReturn(0);

        finalizer.abort(failed, 2, "boom");

        verify(executionMapper).bumpFinishedSteps(EXECUTION_ID, 1);
    }

    @Test
    void abort_should_still_fail_execution_when_step_guard_misses() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 1, 2);
        when(stepRunMapper.markFailed(any(), anyInt(), anyString(), any(LocalDateTime.class))).thenReturn(0);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of());

        finalizer.abort(failed, 2, "boom");

        verify(executionMapper).markFailed(eq(EXECUTION_ID), eq("第 1 步失败：boom"), any(LocalDateTime.class));
    }

    @Test
    void abort_should_swallow_when_step_mark_failed_throws() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 1, 2);
        doThrow(new IllegalStateException("db down"))
                .when(stepRunMapper).markFailed(any(), anyInt(), anyString(), any(LocalDateTime.class));

        assertThatCode(() -> finalizer.abort(failed, 2, "boom")).doesNotThrowAnyException();

        verify(executionMapper, never()).markFailed(any(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void abort_should_swallow_when_sibling_query_throws() {
        SceneStepRun failed = abortedRun(STEP_RUN_ID, 1, 2);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> finalizer.abort(failed, 2, "boom")).doesNotThrowAnyException();

        verify(executionMapper, never()).markFailed(any(), anyString(), any(LocalDateTime.class));
    }

    // ---------- 重试与中止的衔接 ----------

    @Test
    void executeStep_should_retry_with_backoff_and_keep_execution_running_when_balance_remains() {
        stubStepRun(failingStepRun(0));
        stubFailingTriggerContext();

        executor.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(LocalDateTime.class),
                contains("未在物模型中定义"));
        verify(stepRunMapper, never()).markFailed(any(), anyInt(), anyString(), any(LocalDateTime.class));
        verify(executionMapper, never()).markFailed(any(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void executeStep_should_abort_and_compose_failure_when_retry_exhausted() {
        properties.setRetryMaxAttempts(1);
        stubStepRun(failingStepRun(1));
        stubFailingTriggerContext();
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID))
                .thenReturn(List.of(sibling(PENDING_SIBLING_ID, 2, SceneConstants.STEP_PENDING)));
        when(stepRunMapper.markSkipped(eq(PENDING_SIBLING_ID), any(LocalDateTime.class))).thenReturn(1);

        executor.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markFailed(eq(STEP_RUN_ID), eq(2), contains("未在物模型中定义"),
                any(LocalDateTime.class));
        verify(stepRunMapper, never()).markRetry(any(), anyInt(), any(LocalDateTime.class), anyString());
        verify(stepRunMapper).markSkipped(eq(PENDING_SIBLING_ID), any(LocalDateTime.class));
        verify(executionMapper).bumpFinishedSteps(EXECUTION_ID, 1);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(executionMapper).markFailed(eq(EXECUTION_ID), messageCaptor.capture(), any(LocalDateTime.class));
        assertThat(messageCaptor.getValue())
                .startsWith("第 1 步失败：")
                .contains("未在物模型中定义");
    }

    @Test
    void executeStep_should_not_touch_execution_when_claim_lost() {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(0);

        executor.executeStep(STEP_RUN_ID);

        verifyNoInteractions(executionMapper);
        verify(stepRunMapper, never()).markFailed(any(), anyInt(), anyString(), any(LocalDateTime.class));
    }

    // ---------- 辅助 ----------

    private void stubStepRun(SceneStepRun run) {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(1);
        when(stepRunMapper.selectById(STEP_RUN_ID)).thenReturn(run);
    }

    /** 触发目标 + 属性标识符未在物模型中定义，稳定走失败分支。 */
    private void stubFailingTriggerContext() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution());
        when(stepMapper.selectById(STEP_ID)).thenReturn(sceneStep());
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene());
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));
    }

    private SceneStepRun failingStepRun(int attemptCount) {
        SceneStepRun run = new SceneStepRun();
        run.setId(STEP_RUN_ID);
        run.setExecutionId(EXECUTION_ID);
        run.setSceneId(SCENE_ID);
        run.setStepId(STEP_ID);
        run.setSeq(1);
        run.setActionType(SceneConstants.ACTION_UPDATE_PROPERTY);
        run.setDelaySeconds(0);
        run.setStatus(SceneConstants.STEP_PENDING);
        run.setAttemptCount(attemptCount);
        run.setCreatedAt(NOW);
        return run;
    }

    private SceneStepRun abortedRun(Long id, int seq, int attemptCount) {
        SceneStepRun run = new SceneStepRun();
        run.setId(id);
        run.setExecutionId(EXECUTION_ID);
        run.setSceneId(SCENE_ID);
        run.setSeq(seq);
        run.setStatus(SceneConstants.STEP_RUNNING);
        run.setAttemptCount(attemptCount);
        return run;
    }

    private SceneStepRun sibling(Long id, int seq, String status) {
        SceneStepRun run = new SceneStepRun();
        run.setId(id);
        run.setExecutionId(EXECUTION_ID);
        run.setSceneId(SCENE_ID);
        run.setSeq(seq);
        run.setStatus(status);
        return run;
    }

    private SceneStep sceneStep() {
        SceneStep step = new SceneStep();
        step.setId(STEP_ID);
        step.setSceneId(SCENE_ID);
        step.setSeq(1);
        step.setActionType(SceneConstants.ACTION_UPDATE_PROPERTY);
        step.setTargetType(SceneConstants.TARGET_TRIGGER);
        step.setActionConfig("{\"identifier\":\"ghost\",\"value\":\"on\"}");
        step.setEnabled(1);
        return step;
    }

    private SceneDefinition scene() {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        scene.setUserId(USER_ID);
        scene.setName("高温联动");
        return scene;
    }

    private SceneExecution execution() {
        SceneExecution execution = new SceneExecution();
        execution.setId(EXECUTION_ID);
        execution.setUserId(USER_ID);
        execution.setSceneId(SCENE_ID);
        execution.setSceneName("高温联动");
        execution.setTriggerType(SceneConstants.TRIGGER_PROPERTY);
        execution.setTriggerDeviceId(DEVICE_ID);
        execution.setStatus(SceneConstants.EXEC_PENDING);
        execution.setCreatedAt(NOW);
        return execution;
    }

    private Device device(Long id, Long productId, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setProductId(productId);
        device.setDeviceKey("dev-" + id);
        device.setDeviceName("设备" + id);
        device.setDeviceType("sensor");
        device.setOwnerId(ownerId);
        return device;
    }

    private ThingModelDefinition modelWith(String identifier, String accessMode) {
        return new ThingModelDefinition(1, Map.of(
                identifier, new ThingModelDefinition.PropertySpec(
                        identifier, "bool", null, null, false, Set.of(), null, accessMode)),
                Map.of(), Map.of());
    }
}
