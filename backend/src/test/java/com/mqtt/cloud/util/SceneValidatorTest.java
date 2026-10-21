package com.mqtt.cloud.util;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.SceneRequest;
import com.mqtt.cloud.dto.request.SceneStepRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.DeviceBatchService;
import com.mqtt.cloud.service.DeviceService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景保存期校验：上下线触发源（T-43）。
 * <p>
 * 状态源只允许可选的触发设备，标识符 / 比较条件 / 事件类型 / cron 一律拒绝；其余字段
 * 与既有触发源共用同一套步骤与条件组校验。
 */
class SceneValidatorTest {

    private static final Long USER_ID = 10L;
    private static final Long DEVICE_ID = 100L;

    private final DeviceService deviceService = mock(DeviceService.class);
    private final DeviceBatchService deviceBatchService = mock(DeviceBatchService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SceneValidator validator = new SceneValidator(deviceService, deviceBatchService,
            new SceneProperties(), new RuleProperties(), objectMapper);

    @Test
    void online_trigger_should_pass_without_identifier_or_device() {
        SceneValidator.ValidatedScene validated =
                validator.validate(USER_ID, statusRequest(SceneConstants.TRIGGER_ONLINE));

        assertThat(validated.definition().getTriggerType()).isEqualTo(SceneConstants.TRIGGER_ONLINE);
        assertThat(validated.definition().getTriggerDeviceId()).isNull();
        assertThat(validated.definition().getTriggerIdentifier()).isNull();
        assertThat(validated.definition().getTriggerOperator()).isNull();
        assertThat(validated.definition().getTriggerThreshold()).isNull();
        assertThat(validated.definition().getTriggerEventType()).isNull();
        assertThat(validated.definition().getTimerCron()).isNull();
        assertThat(validated.steps()).hasSize(1);
    }

    @Test
    void offline_trigger_should_accept_owned_device_scope() {
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(USER_ID));
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_OFFLINE);
        request.setTriggerDeviceId(DEVICE_ID);

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.definition().getTriggerType()).isEqualTo(SceneConstants.TRIGGER_OFFLINE);
        assertThat(validated.definition().getTriggerDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void status_trigger_should_reject_identifier() {
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_ONLINE);
        request.setTriggerIdentifier("temperature");

        assertInvalid(request, "上下线触发不允许配置标识符 / 比较条件 / 事件类型");
    }

    @Test
    void status_trigger_should_reject_threshold() {
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_ONLINE);
        request.setTriggerOperator("GT");
        request.setTriggerThreshold("1");

        assertInvalid(request, "上下线触发不允许配置标识符 / 比较条件 / 事件类型");
    }

    @Test
    void status_trigger_should_reject_event_type() {
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_OFFLINE);
        request.setTriggerEventType("alert");

        assertInvalid(request, "上下线触发不允许配置标识符 / 比较条件 / 事件类型");
    }

    @Test
    void status_trigger_should_reject_cron() {
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_ONLINE);
        request.setTimerCron("*/5 * * * *");

        assertInvalid(request, "cron 表达式仅在定时触发时允许配置");
    }

    @Test
    void status_trigger_should_reject_foreign_device() {
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(999L));
        SceneRequest request = statusRequest(SceneConstants.TRIGGER_ONLINE);
        request.setTriggerDeviceId(DEVICE_ID);

        assertThatThrownBy(() -> validator.validate(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
    }

    // ---------- 影子变更（T-45） ----------

    @Test
    void shadow_trigger_should_default_reported_and_allow_optional_identifier() {
        SceneRequest request = shadowRequest();

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.definition().getTriggerType()).isEqualTo(SceneConstants.TRIGGER_SHADOW);
        assertThat(validated.definition().getTriggerShadowSide()).isEqualTo("REPORTED");
        assertThat(validated.definition().getTriggerIdentifier()).isNull();
        assertThat(validated.definition().getTriggerOperator()).isNull();
    }

    @Test
    void shadow_trigger_should_accept_any_side_and_identifier() {
        SceneRequest request = shadowRequest();
        request.setTriggerShadowSide("any");
        request.setTriggerIdentifier("temperature");

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.definition().getTriggerShadowSide()).isEqualTo("ANY");
        assertThat(validated.definition().getTriggerIdentifier()).isEqualTo("temperature");
    }

    @Test
    void shadow_trigger_should_reject_invalid_side() {
        SceneRequest request = shadowRequest();
        request.setTriggerShadowSide("BOTH");

        assertInvalid(request, "影子变更侧必须是 REPORTED / DESIRED / ANY");
    }

    @Test
    void shadow_trigger_should_reject_operator_and_event_type() {
        SceneRequest request = shadowRequest();
        request.setTriggerOperator("GT");
        request.setTriggerThreshold("1");

        assertInvalid(request, "影子变更触发不支持比较符 / 阈值 / 事件类型");
    }

    @Test
    void non_shadow_trigger_should_reject_shadow_side() {
        SceneRequest request = propertyRequest(actionStep(1));
        request.setTriggerShadowSide("REPORTED");

        assertInvalid(request, "影子变更侧仅用于 SHADOW 触发源");
    }

    // ---------- 分支 / 循环（T-44） ----------

    @Test
    void branch_should_pass_with_forward_targets() {
        SceneRequest request = propertyRequest(
                actionStep(1),
                branchStep(2, 3, null),
                actionStep(3));

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.steps()).hasSize(3);
        assertThat(validated.steps().get(1).getActionType()).isEqualTo(SceneConstants.STEP_BRANCH);
        assertThat(validated.steps().get(1).getTargetType()).isEqualTo(SceneConstants.TARGET_NONE);
        assertThat(validated.steps().get(1).getActionConfig()).contains("\"trueSeq\":3");
    }

    @Test
    void branch_should_reject_backward_target() {
        SceneRequest request = propertyRequest(
                actionStep(1),
                branchStep(2, 1, null),
                actionStep(3));

        assertInvalid(request, "只能向前跳转");
    }

    @Test
    void branch_should_reject_jump_into_loop() {
        SceneRequest request = propertyRequest(
                branchStep(1, 3, null),
                actionStep(2),
                actionStep(3),
                loopStep(4, 2, 2));

        assertInvalid(request, "不能从循环外跳入");
    }

    @Test
    void branch_should_reject_disabled_target() {
        SceneStepRequest disabled = actionStep(3);
        disabled.setEnabled(false);
        SceneRequest request = propertyRequest(
                actionStep(1),
                branchStep(2, 3, null),
                disabled);

        assertInvalid(request, "必须启用");
    }

    @Test
    void loop_should_pass_with_nested_block() {
        SceneRequest request = propertyRequest(
                actionStep(1),
                loopStep(2, 1, 2),
                actionStep(3));

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.steps().get(1).getActionType()).isEqualTo(SceneConstants.STEP_LOOP);
        assertThat(validated.steps().get(1).getTargetType()).isEqualTo(SceneConstants.TARGET_NONE);
        assertThat(validated.steps().get(1).getActionConfig()).contains("\"times\":2");
    }

    @Test
    void loop_should_reject_partial_overlap() {
        SceneRequest request = propertyRequest(
                actionStep(1),
                actionStep(2),
                actionStep(3),
                loopStep(4, 2, 2),
                actionStep(5),
                loopStep(6, 3, 2));

        assertInvalid(request, "循环范围不能部分重叠");
    }

    @Test
    void loop_should_reject_execution_limit() {
        SceneStepRequest[] steps = new SceneStepRequest[11];
        for (int i = 0; i < 10; i++) {
            steps[i] = actionStep(i + 1);
        }
        steps[10] = loopStep(11, 1, 20);

        SceneRequest request = propertyRequest(steps);

        assertThatThrownBy(() -> validator.validate(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SCENE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void timer_branch_should_require_condition_device() {
        SceneRequest request = new SceneRequest();
        request.setName("定时分支");
        request.setTriggerType(SceneConstants.TRIGGER_TIMER);
        request.setTimerCron("*/5 * * * *");
        request.setSteps(List.of(branchStep(1, 2, null), forwardStep(2)));

        assertInvalid(request, "定时触发的分支条件必须指定取值设备");
    }

    // ---------- 并行（T-49） ----------

    @Test
    void parallel_should_pass_with_two_branches() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 0),
                blockActionStep(3, 1, 1),
                joinStep(4),
                actionStep(5));

        SceneValidator.ValidatedScene validated = validator.validate(USER_ID, request);

        assertThat(validated.steps()).hasSize(5);
        assertThat(validated.steps().get(0).getActionType()).isEqualTo(SceneConstants.STEP_PARALLEL);
        assertThat(validated.steps().get(0).getTargetType()).isEqualTo(SceneConstants.TARGET_NONE);
        assertThat(validated.steps().get(1).getParallelSeq()).isEqualTo(1);
        assertThat(validated.steps().get(1).getBranchIndex()).isZero();
        assertThat(validated.steps().get(2).getBranchIndex()).isEqualTo(1);
        assertThat(validated.steps().get(3).getActionType()).isEqualTo(SceneConstants.STEP_JOIN);
        assertThat(validated.steps().get(4).getParallelSeq()).isNull();
        assertThat(validated.steps().get(4).getBranchIndex()).isNull();
    }

    @Test
    void parallel_should_reject_missing_join() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 0),
                blockActionStep(3, 1, 1));

        assertInvalid(request, "缺少对应的并行汇合");
    }

    @Test
    void parallel_should_reject_nested_block() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 0),
                parallelStep(3, 2),
                blockActionStep(4, 3, 0),
                blockActionStep(5, 3, 1),
                joinStep(6),
                blockActionStep(7, 1, 1),
                joinStep(8));

        assertInvalid(request, "并行块不能嵌套");
    }

    @Test
    void parallel_should_reject_empty_branch() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 0),
                joinStep(3));

        assertInvalid(request, "分支 2 至少需要一个启用步骤");
    }

    @Test
    void parallel_should_reject_flow_step_inside_block() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 0),
                branchStep(3, 5, null),
                blockActionStep(4, 1, 1),
                joinStep(5));

        assertInvalid(request, "并行块内只允许普通动作步骤");
    }

    @Test
    void parallel_should_reject_branch_jump_into_block() {
        SceneRequest request = propertyRequest(
                branchStep(1, 3, null),
                parallelStep(2, 2),
                blockActionStep(3, 2, 0),
                blockActionStep(4, 2, 1),
                joinStep(5));

        assertInvalid(request, "不能跳入并行块");
    }

    @Test
    void parallel_should_reject_overlap_with_loop() {
        SceneRequest request = propertyRequest(
                actionStep(1),
                actionStep(2),
                parallelStep(3, 2),
                blockActionStep(4, 3, 0),
                blockActionStep(5, 3, 1),
                joinStep(6),
                loopStep(7, 2, 2));

        assertInvalid(request, "不能与循环体");
    }

    @Test
    void parallel_should_reject_membership_outside_block() {
        SceneStepRequest step = actionStep(2);
        step.setParallelSeq(1);
        step.setBranchIndex(0);
        SceneRequest request = propertyRequest(actionStep(1), step);

        assertInvalid(request, "不能设置 parallelSeq / branchIndex");
    }

    @Test
    void parallel_should_reject_branch_index_out_of_range() {
        SceneRequest request = propertyRequest(
                parallelStep(1, 2),
                blockActionStep(2, 1, 2),
                blockActionStep(3, 1, 1),
                joinStep(4));

        assertInvalid(request, "branchIndex 需在 [0, 1]");
    }

    private void assertInvalid(SceneRequest request, String message) {
        assertThatThrownBy(() -> validator.validate(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.SCENE_INVALID.getCode());
    }

    private SceneRequest statusRequest(String triggerType) {
        SceneRequest request = new SceneRequest();
        request.setName("上下线联动");
        request.setTriggerType(triggerType);
        request.setSteps(List.of(step()));
        return request;
    }

    private SceneRequest propertyRequest(SceneStepRequest... steps) {
        SceneRequest request = new SceneRequest();
        request.setName("流程场景");
        request.setTriggerType(SceneConstants.TRIGGER_PROPERTY);
        request.setTriggerIdentifier("temperature");
        request.setSteps(List.of(steps));
        return request;
    }

    private SceneRequest shadowRequest() {
        SceneRequest request = new SceneRequest();
        request.setName("影子联动");
        request.setTriggerType(SceneConstants.TRIGGER_SHADOW);
        request.setSteps(List.of(step()));
        return request;
    }

    private SceneStepRequest step() {
        return actionStep(1);
    }

    private SceneStepRequest actionStep(int seq) {
        SceneStepRequest step = baseStep(seq, SceneConstants.ACTION_UPDATE_PROPERTY,
                SceneConstants.TARGET_TRIGGER);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("identifier", "switch");
        config.put("value", "on");
        step.setActionConfig(config);
        return step;
    }

    private SceneStepRequest branchStep(int seq, Integer trueSeq, Integer falseSeq) {
        SceneStepRequest step = baseStep(seq, SceneConstants.STEP_BRANCH,
                SceneConstants.TARGET_NONE);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("identifier", "temperature");
        config.put("operator", "GT");
        config.put("threshold", "30");
        if (trueSeq != null) {
            config.put("trueSeq", trueSeq);
        }
        if (falseSeq != null) {
            config.put("falseSeq", falseSeq);
        }
        step.setActionConfig(config);
        return step;
    }

    private SceneStepRequest loopStep(int seq, int startSeq, int times) {
        SceneStepRequest step = baseStep(seq, SceneConstants.STEP_LOOP,
                SceneConstants.TARGET_NONE);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("startSeq", startSeq);
        config.put("times", times);
        step.setActionConfig(config);
        return step;
    }

    private SceneStepRequest parallelStep(int seq, int branches) {
        SceneStepRequest step = baseStep(seq, SceneConstants.STEP_PARALLEL,
                SceneConstants.TARGET_NONE);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("branches", branches);
        step.setActionConfig(config);
        return step;
    }

    private SceneStepRequest joinStep(int seq) {
        SceneStepRequest step = baseStep(seq, SceneConstants.STEP_JOIN,
                SceneConstants.TARGET_NONE);
        step.setActionConfig(objectMapper.createObjectNode());
        return step;
    }

    private SceneStepRequest blockActionStep(int seq, int parallelSeq, int branchIndex) {
        SceneStepRequest step = actionStep(seq);
        step.setParallelSeq(parallelSeq);
        step.setBranchIndex(branchIndex);
        return step;
    }

    private SceneStepRequest forwardStep(int seq) {
        SceneStepRequest step = baseStep(seq, SceneConstants.ACTION_FORWARD_MQTT,
                SceneConstants.TARGET_TRIGGER);
        ObjectNode config = objectMapper.createObjectNode();
        config.put("topic", "home/event");
        step.setActionConfig(config);
        return step;
    }

    private SceneStepRequest baseStep(int seq, String actionType, String targetType) {
        SceneStepRequest step = new SceneStepRequest();
        step.setSeq(seq);
        step.setActionType(actionType);
        step.setTargetType(targetType);
        return step;
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        return device;
    }
}
