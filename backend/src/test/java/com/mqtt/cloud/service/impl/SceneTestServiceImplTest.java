package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.constant.ShadowChangeSide;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.SceneTestRequest;
import com.mqtt.cloud.dto.response.SceneTestResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.entity.SceneStep;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.mapper.SceneStepMapper;
import com.mqtt.cloud.service.DeviceBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景试运行（干跑）上下线触发（T-43）。
 * <p>
 * 状态源只需设备上下文，{@code value} 缺省按场景状态判定；不提供设备或设备越权时明确拒绝。
 */
class SceneTestServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long SCENE_ID = 5L;
    private static final Long DEVICE_ID = 100L;

    private final SceneDefinitionMapper definitionMapper = mock(SceneDefinitionMapper.class);
    private final SceneStepMapper stepMapper = mock(SceneStepMapper.class);
    private final DeviceMapper deviceMapper = mock(DeviceMapper.class);
    private final DeviceBatchService deviceBatchService = mock(DeviceBatchService.class);
    private final SceneProperties properties = new SceneProperties();

    private SceneTestServiceImpl service;

    @BeforeEach
    void setUp() {
        SceneMatcher sceneMatcher = new SceneMatcher(mock(DevicePropertyLatestMapper.class),
                properties, new ObjectMapper());
        service = new SceneTestServiceImpl(definitionMapper, stepMapper, deviceMapper,
                deviceBatchService, sceneMatcher, new SceneCooldownRegistry(), new ObjectMapper());
    }

    @Test
    void status_test_should_match_without_identifier() {
        stubScene(SceneConstants.TRIGGER_ONLINE);

        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.isTriggerMatched()).isTrue();
        assertThat(result.isConditionMatched()).isTrue();
        assertThat(result.getStepSummaries()).isEmpty();
    }

    @Test
    void status_test_should_not_match_opposite_status() {
        stubScene(SceneConstants.TRIGGER_ONLINE);
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);
        request.setValue("OFFLINE");

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.isTriggerMatched()).isFalse();
    }

    @Test
    void status_test_should_require_device() {
        stubScene(SceneConstants.TRIGGER_OFFLINE);

        assertThatThrownBy(() -> service.test(USER_ID, SCENE_ID, new SceneTestRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SCENE_INVALID.getCode());
    }

    @Test
    void status_test_should_reject_foreign_device() {
        stubScene(SceneConstants.TRIGGER_ONLINE);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(999L));
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);

        assertThatThrownBy(() -> service.test(USER_ID, SCENE_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
    }

    @Test
    void status_test_should_summarize_flow_steps_and_multiply_loop_delay() {
        stubScene(SceneConstants.TRIGGER_ONLINE);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(
                step(1, SceneConstants.ACTION_UPDATE_PROPERTY, 10,
                        "{\"identifier\":\"switch\",\"value\":\"on\"}"),
                step(2, SceneConstants.ACTION_UPDATE_PROPERTY, 5,
                        "{\"identifier\":\"switch\",\"value\":\"off\"}"),
                step(3, SceneConstants.STEP_LOOP, 0, "{\"startSeq\":1,\"times\":3}")));
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.getStepSummaries()).hasSize(3);
        assertThat(result.getStepSummaries().get(2).getTargetType())
                .isEqualTo(SceneConstants.TARGET_NONE);
        assertThat(result.getStepSummaries().get(2).getSummary()).contains("循环 3 次");
        // 两步各执行 3 轮：(10 + 5) × 3
        assertThat(result.getEstimatedDurationSeconds()).isEqualTo(45);
    }

    @Test
    void parallel_test_should_summarize_parallel_and_join_steps() {
        stubScene(SceneConstants.TRIGGER_ONLINE);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of(
                step(1, SceneConstants.STEP_PARALLEL, 0, "{\"branches\":2}"),
                step(2, SceneConstants.ACTION_UPDATE_PROPERTY, 0,
                        "{\"identifier\":\"switch\",\"value\":\"on\"}"),
                step(3, SceneConstants.ACTION_UPDATE_PROPERTY, 0,
                        "{\"identifier\":\"switch\",\"value\":\"off\"}"),
                step(4, SceneConstants.STEP_JOIN, 0, "{}")));
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.getStepSummaries()).hasSize(4);
        assertThat(result.getStepSummaries().get(0).getTargetType())
                .isEqualTo(SceneConstants.TARGET_NONE);
        assertThat(result.getStepSummaries().get(0).getSummary()).contains("并行 2 路");
        assertThat(result.getStepSummaries().get(3).getSummary()).contains("并行汇合");
    }

    // ---------- 影子变更（T-45） ----------

    @Test
    void shadow_test_should_match_reported_side() {
        stubShadowScene(ShadowChangeSide.REPORTED, "temperature");
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);
        request.setIdentifier("temperature");
        request.setValue("REPORTED");

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.isTriggerMatched()).isTrue();
    }

    @Test
    void shadow_test_should_not_match_opposite_side() {
        stubShadowScene(ShadowChangeSide.REPORTED, null);
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);
        request.setValue("DESIRED");

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.isTriggerMatched()).isFalse();
    }

    @Test
    void shadow_test_should_apply_identifier_filter() {
        stubShadowScene(ShadowChangeSide.ANY, "temperature");
        SceneTestRequest request = new SceneTestRequest();
        request.setDeviceId(DEVICE_ID);
        request.setIdentifier("humidity");
        request.setValue("REPORTED");

        SceneTestResult result = service.test(USER_ID, SCENE_ID, request);

        assertThat(result.isTriggerMatched()).isFalse();
    }

    @Test
    void shadow_test_should_require_device() {
        stubShadowScene(ShadowChangeSide.REPORTED, null);

        assertThatThrownBy(() -> service.test(USER_ID, SCENE_ID, new SceneTestRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SCENE_INVALID.getCode());
    }

    private void stubScene(String triggerType) {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        scene.setUserId(USER_ID);
        scene.setName("上下线联动");
        scene.setTriggerType(triggerType);
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of());
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(USER_ID));
    }

    private void stubShadowScene(String side, String identifier) {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        scene.setUserId(USER_ID);
        scene.setName("影子联动");
        scene.setTriggerType(SceneConstants.TRIGGER_SHADOW);
        scene.setTriggerShadowSide(side);
        scene.setTriggerIdentifier(identifier);
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene);
        when(stepMapper.selectBySceneId(SCENE_ID)).thenReturn(List.of());
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(USER_ID));
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        return device;
    }

    private SceneStep step(int seq, String actionType, int delaySeconds, String actionConfig) {
        SceneStep step = new SceneStep();
        step.setId(100L + seq);
        step.setSceneId(SCENE_ID);
        step.setSeq(seq);
        step.setActionType(actionType);
        step.setTargetType(SceneConstants.FLOW_STEPS.contains(actionType)
                ? SceneConstants.TARGET_NONE : SceneConstants.TARGET_TRIGGER);
        step.setDelaySeconds(delaySeconds);
        step.setActionConfig(actionConfig);
        step.setEnabled(1);
        return step;
    }
}
