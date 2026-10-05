package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
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
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 场景步骤动作执行单测（T-23 设计文档 §14.1 / §8.6 / §8.7 / §8.8）。
 * <p>
 * 覆盖内容：
 * <ul>
 *     <li>抢占短路：{@code claimPending} 返回 0 或步骤记录缺失时零副作用；</li>
 *     <li>四类动作分派：更新属性 / 下发命令 / 转发 MQTT / 转发 HTTP 的入参快照；</li>
 *     <li>目标解析：{@code TRIGGER} 触发设备（执行期二次校验归属）与 {@code FIXED} 复用 T-18 目标解析；</li>
 *     <li>多设备逐台执行：部分失败汇总 {@code M/N 成功} 作为失败原因；</li>
 *     <li>成功收口：推进进度 + 下一步排期（延时 0 立即投递）；队列满置失败并中止整条执行；</li>
 *     <li>失败退避：指数退避锚点、重试耗尽交由 {@link SceneExecutionFinalizer} 中止。</li>
 * </ul>
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneActionExecutorImplTest {

    private static final Long USER_ID = 10L;
    private static final Long SCENE_ID = 5L;
    private static final Long STEP_ID = 50L;
    private static final Long STEP_RUN_ID = 500L;
    private static final Long NEXT_STEP_RUN_ID = 501L;
    private static final Long EXECUTION_ID = 900L;
    private static final Long DEVICE_ID = 100L;
    private static final Long SECOND_DEVICE_ID = 101L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long SECOND_PRODUCT_ID = 8L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    private SceneStepRunMapper stepRunMapper;
    private SceneStepMapper stepMapper;
    private SceneDefinitionMapper definitionMapper;
    private SceneExecutionMapper executionMapper;
    private DeviceMapper deviceMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private DeviceCommandService commandService;
    private ThingModelService thingModelService;
    private DeviceBatchService deviceBatchService;
    private RuleMqttForwarder mqttForwarder;
    private RuleHttpForwarder httpForwarder;
    private SceneExecutionFinalizer finalizer;
    private SceneProperties properties;
    private RuleProperties ruleProperties;
    private ThreadPoolTaskExecutor sceneExecutor;
    private SimpleMeterRegistry meterRegistry;
    private SceneActionExecutorImpl service;

    @BeforeEach
    void setUp() {
        stepRunMapper = mock(SceneStepRunMapper.class);
        stepMapper = mock(SceneStepMapper.class);
        definitionMapper = mock(SceneDefinitionMapper.class);
        executionMapper = mock(SceneExecutionMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        commandService = mock(DeviceCommandService.class);
        thingModelService = mock(ThingModelService.class);
        deviceBatchService = mock(DeviceBatchService.class);
        mqttForwarder = mock(RuleMqttForwarder.class);
        httpForwarder = mock(RuleHttpForwarder.class);
        finalizer = mock(SceneExecutionFinalizer.class);
        properties = new SceneProperties();
        ruleProperties = new RuleProperties();
        sceneExecutor = mock(ThreadPoolTaskExecutor.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new SceneActionExecutorImpl(stepRunMapper, stepMapper, definitionMapper, executionMapper,
                deviceMapper, propertyLatestMapper, commandService, thingModelService, deviceBatchService,
                mqttForwarder, httpForwarder, finalizer, properties, ruleProperties, new ObjectMapper(),
                sceneExecutor, meterRegistry);
    }

    // ---------- 抢占短路 ----------

    @Test
    void executeStep_should_skip_when_claim_fails() {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(0);

        service.executeStep(STEP_RUN_ID);

        verifyNoInteractions(executionMapper, stepMapper, definitionMapper, deviceMapper,
                propertyLatestMapper, commandService, mqttForwarder, httpForwarder, finalizer);
    }

    @Test
    void executeStep_should_noop_when_step_run_missing() {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(1);
        when(stepRunMapper.selectById(STEP_RUN_ID)).thenReturn(null);

        service.executeStep(STEP_RUN_ID);

        verifyNoInteractions(stepMapper, definitionMapper, deviceMapper, propertyLatestMapper);
        verify(executionMapper, never()).markRunning(any(), any());
    }

    @Test
    void executeStep_should_ignore_null_id() {
        service.executeStep(null);

        verifyNoInteractions(stepRunMapper);
    }

    @Test
    void executeStep_should_not_propagate_when_claim_throws() {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenThrow(new RuntimeException("db down"));

        service.executeStep(STEP_RUN_ID);

        verifyNoInteractions(stepMapper, definitionMapper, propertyLatestMapper);
    }

    // ---------- 更新属性 ----------

    @Test
    void update_property_should_upsert_and_advance_execution() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.executeStep(STEP_RUN_ID);

        verify(executionMapper).markRunning(eq(EXECUTION_ID), any());
        verify(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("switch"), eq("bool"), eq("on"), any());
        verify(stepRunMapper).markSuccess(eq(STEP_RUN_ID), any());
        verify(executionMapper).bumpFinishedSteps(eq(EXECUTION_ID), eq(1));
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
        verifyNoInteractions(finalizer);
    }

    @Test
    void update_property_should_retry_when_identifier_unknown() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.executeStep(STEP_RUN_ID);

        verify(propertyLatestMapper, never()).upsertIfNewer(any(), any(), any(), any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("未在物模型中定义"));
    }

    @Test
    void update_property_should_retry_when_target_readonly() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "r"));

        service.executeStep(STEP_RUN_ID);

        verify(propertyLatestMapper, never()).upsertIfNewer(any(), any(), any(), any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("不可写"));
    }

    @Test
    void update_property_should_retry_when_value_blank() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"switch\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.executeStep(STEP_RUN_ID);

        verify(propertyLatestMapper, never()).upsertIfNewer(any(), any(), any(), any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("取值为空"));
    }

    @Test
    void update_property_should_retry_when_identifier_missing() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("缺少目标标识符"));
    }

    // ---------- 下发命令 ----------

    @Test
    void send_command_should_invoke_with_async_and_scene_source() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"service\",\"identifier\":\"reboot\",\"params\":{\"delay\":5}}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(commandService).invoke(captor.capture());
        DeviceCommandService.CommandInvoke invoke = captor.getValue();
        assertThat(invoke.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(invoke.productId()).isEqualTo(PRODUCT_ID);
        assertThat(invoke.commandType()).isEqualTo(DeviceCommandService.TYPE_SERVICE);
        assertThat(invoke.identifier()).isEqualTo("reboot");
        assertThat(invoke.paramsJson()).isEqualTo("{\"delay\":5}");
        assertThat(invoke.callType()).isEqualTo(DeviceCommandService.CALL_TYPE_ASYNC);
        assertThat(invoke.source()).isEqualTo(DeviceCommandService.SOURCE_SCENE);
        assertThat(invoke.operatorId()).isNull();
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void send_command_property_set_should_pass_null_identifier() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"property_set\",\"identifier\":\"switch\",\"params\":{\"switch\":true}}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(commandService).invoke(captor.capture());
        assertThat(captor.getValue().commandType()).isEqualTo(DeviceCommandService.TYPE_PROPERTY_SET);
        assertThat(captor.getValue().identifier()).isNull();
    }

    @Test
    void send_command_should_retry_when_invoke_fails() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"service\",\"identifier\":\"reboot\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(commandService.invoke(any())).thenThrow(new IllegalStateException("device offline"));

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("下发命令失败"));
    }

    @Test
    void send_command_should_retry_when_params_not_json() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"service\",\"identifier\":\"reboot\",\"params\":\"not-json\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));

        service.executeStep(STEP_RUN_ID);

        verify(commandService, never()).invoke(any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("不是合法 JSON"));
    }

    // ---------- 转发 MQTT ----------

    @Test
    void forward_mqtt_should_publish_and_snapshot_payload() throws Exception {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_MQTT,
                "{\"topic\":\"factory/line1\",\"qos\":1,\"payloadTemplate\":\"{\\\"v\\\":1}\"}");

        service.executeStep(STEP_RUN_ID);

        verify(mqttForwarder).publish(eq("factory/line1"), eq("{\"v\":1}"), eq(1));
        verify(stepRunMapper).updateForwardPayload(eq(STEP_RUN_ID), eq("{\"v\":1}"));
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void forward_mqtt_should_render_topic_from_placeholders() throws Exception {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_MQTT,
                "{\"topic\":\"scene/${sceneId}/step/${stepSeq}\"}");

        service.executeStep(STEP_RUN_ID);

        verify(mqttForwarder).publish(eq("scene/5/step/1"), anyString(), anyInt());
        verify(mqttForwarder).publish(anyString(), anyString(), eq(ruleProperties.getMqtt().getQos()));
    }

    @Test
    void forward_mqtt_should_snapshot_default_payload_when_template_absent() {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_MQTT, "{\"topic\":\"factory/line1\"}");

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stepRunMapper).updateForwardPayload(eq(STEP_RUN_ID), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"event\":\"" + SceneConstants.EVENT_SCENE_TRIGGERED + "\"");
        assertThat(payload).contains("\"sceneId\":5");
        assertThat(payload).contains("\"stepSeq\":1");
    }

    @Test
    void forward_mqtt_should_retry_when_topic_missing() throws Exception {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_MQTT, "{\"qos\":1}");

        service.executeStep(STEP_RUN_ID);

        verify(mqttForwarder, never()).publish(anyString(), anyString(), anyInt());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("缺少 Topic"));
    }

    @Test
    void forward_mqtt_should_retry_when_publish_fails() throws Exception {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_MQTT, "{\"topic\":\"factory/line1\"}");
        doThrow(new IllegalStateException("broker unreachable"))
                .when(mqttForwarder).publish(anyString(), anyString(), anyInt());

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).updateForwardPayload(eq(STEP_RUN_ID), anyString());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("MQTT 转发失败"));
    }

    // ---------- 转发 HTTP ----------

    @Test
    void forward_http_should_send_with_scene_headers() {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_HTTP,
                "{\"url\":\"https://example.com/hook\",\"method\":\"PUT\","
                        + "\"headers\":{\"X-Token\":\"abc\"},\"payloadTemplate\":\"{\\\"v\\\":1}\"}");

        service.executeStep(STEP_RUN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpForwarder).send(eq(SceneConstants.EVENT_SCENE_TRIGGERED),
                eq(SceneConstants.HEADER_SCENE_ID), eq(SCENE_ID), eq("PUT"),
                eq("https://example.com/hook"), headersCaptor.capture(), eq("{\"v\":1}"),
                eq(EXECUTION_ID));
        assertThat(headersCaptor.getValue()).containsEntry("X-Token", "abc");
        assertThat(headersCaptor.getValue()).containsEntry("X-Step-Seq", "1");
        verify(stepRunMapper).updateForwardPayload(eq(STEP_RUN_ID), eq("{\"v\":1}"));
    }

    @Test
    void forward_http_should_retry_when_url_not_http() {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_HTTP, "{\"url\":\"ftp://example.com/hook\"}");

        service.executeStep(STEP_RUN_ID);

        verify(httpForwarder, never()).send(any(), any(), any(), any(), any(), any(), any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("http://"));
    }

    @Test
    void forward_http_should_retry_when_payload_not_json() {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_HTTP,
                "{\"url\":\"https://example.com/hook\",\"payloadTemplate\":\"not-json\"}");

        service.executeStep(STEP_RUN_ID);

        verify(httpForwarder, never()).send(any(), any(), any(), any(), any(), any(), any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("不是合法 JSON"));
    }

    @Test
    void forward_http_should_retry_when_target_errors() {
        stubStepRun(pendingStepRun());
        stubForwardContext(SceneConstants.ACTION_FORWARD_HTTP, "{\"url\":\"https://example.com/hook\"}");
        doThrow(new IllegalStateException("转发失败：目标返回状态码 500"))
                .when(httpForwarder).send(any(), any(), any(), any(), any(), any(), any(), any());

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("HTTP 转发失败"));
    }

    // ---------- 目标解析 ----------

    @Test
    void fixed_target_should_resolve_devices_and_apply_action() {
        stubStepRun(pendingStepRun());
        stubFixedContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"deviceIds\":[100]}",
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceBatchService.resolveTarget(eq(USER_ID), any(BatchTargetRequest.class)))
                .thenReturn(List.of(device(DEVICE_ID, PRODUCT_ID, USER_ID)));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.executeStep(STEP_RUN_ID);

        verify(deviceBatchService).resolveTarget(eq(USER_ID), any(BatchTargetRequest.class));
        verify(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("switch"), eq("bool"), eq("on"), any());
    }

    @Test
    void fixed_target_should_retry_when_config_missing() {
        stubStepRun(pendingStepRun());
        stubFixedContext(SceneConstants.ACTION_UPDATE_PROPERTY, null,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");

        service.executeStep(STEP_RUN_ID);

        verify(deviceBatchService, never()).resolveTarget(any(), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("固定目标配置缺失或非法"));
    }

    @Test
    void fixed_target_should_retry_when_resolved_empty() {
        stubStepRun(pendingStepRun());
        stubFixedContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"deviceIds\":[100]}",
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceBatchService.resolveTarget(eq(USER_ID), any(BatchTargetRequest.class)))
                .thenReturn(List.of());

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("目标设备为空"));
    }

    @Test
    void trigger_target_should_retry_when_owner_changed() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, 999L));

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("归属已变更"));
    }

    @Test
    void trigger_target_should_retry_when_device_missing() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("触发设备不存在或已删除"));
    }

    @Test
    void multi_device_should_retry_with_partial_summary_when_one_fails() {
        stubStepRun(pendingStepRun());
        stubFixedContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"deviceIds\":[100,101]}",
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceBatchService.resolveTarget(eq(USER_ID), any(BatchTargetRequest.class)))
                .thenReturn(List.of(device(DEVICE_ID, PRODUCT_ID, USER_ID),
                        device(SECOND_DEVICE_ID, SECOND_PRODUCT_ID, USER_ID)));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));
        when(thingModelService.getForProduct(SECOND_PRODUCT_ID)).thenReturn(emptyModel());

        service.executeStep(STEP_RUN_ID);

        verify(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("switch"), eq("bool"), eq("on"), any());
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("1/2 成功"));
        verify(stepRunMapper, never()).markSuccess(any(), any());
    }

    @Test
    void multi_device_should_succeed_when_all_devices_ok() {
        stubStepRun(pendingStepRun());
        stubFixedContext(SceneConstants.ACTION_UPDATE_PROPERTY, "{\"deviceIds\":[100,101]}",
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceBatchService.resolveTarget(eq(USER_ID), any(BatchTargetRequest.class)))
                .thenReturn(List.of(device(DEVICE_ID, PRODUCT_ID, USER_ID),
                        device(SECOND_DEVICE_ID, PRODUCT_ID, USER_ID)));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.executeStep(STEP_RUN_ID);

        verify(propertyLatestMapper, org.mockito.Mockito.times(2))
                .upsertIfNewer(any(), eq("switch"), eq("bool"), eq("on"), any());
        verify(stepRunMapper).markSuccess(eq(STEP_RUN_ID), any());
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void should_retry_when_action_config_missing() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY, null);

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("动作配置缺失或非法"));
    }

    // ---------- 成功收口与下一步排期 ----------

    @Test
    void should_schedule_next_step_with_delay() {
        stubSuccessfulUpdate();
        SceneStepRun next = nextStepRun(30);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(next));

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<LocalDateTime> scheduledCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).scheduleNext(eq(NEXT_STEP_RUN_ID), scheduledCaptor.capture(),
                scheduledCaptor.capture());
        assertThat(scheduledCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(25));
        verify(sceneExecutor, never()).execute(any(Runnable.class));
        verify(executionMapper, never()).markSuccess(any(), any());
    }

    @Test
    void should_deliver_next_step_immediately_when_delay_zero() {
        stubSuccessfulUpdate();
        SceneStepRun next = nextStepRun(0);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(next));

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).scheduleNext(eq(NEXT_STEP_RUN_ID), any(), any());
        verify(sceneExecutor).execute(any(Runnable.class));
    }

    @Test
    void should_abort_execution_when_executor_rejects_next_step() {
        stubSuccessfulUpdate();
        SceneStepRun next = nextStepRun(0);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(next));
        when(stepRunMapper.selectById(NEXT_STEP_RUN_ID)).thenReturn(next);
        when(stepRunMapper.claimPending(NEXT_STEP_RUN_ID)).thenReturn(1);
        doThrow(new RejectedExecutionException("queue full"))
                .when(sceneExecutor).execute(any(Runnable.class));

        service.executeStep(STEP_RUN_ID);

        verify(finalizer).abort(eq(next), eq(1), eq("执行队列已满"));
        assertThat(meterRegistry.get("scene_rejected_total").counter().count()).isEqualTo(1.0d);
    }

    @Test
    void should_not_propagate_when_executor_throws_unexpected() {
        stubSuccessfulUpdate();
        SceneStepRun next = nextStepRun(0);
        when(stepRunMapper.selectByExecutionId(EXECUTION_ID)).thenReturn(List.of(next));
        doThrow(new IllegalStateException("shutting down"))
                .when(sceneExecutor).execute(any(Runnable.class));

        service.executeStep(STEP_RUN_ID);

        verify(finalizer, never()).abort(any(), anyInt(), any());
    }

    // ---------- 失败退避与终态 ----------

    @Test
    void should_retry_with_backoff_within_limit() {
        stubTriggerContextFailing();
        stubStepRun(pendingStepRun());

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<LocalDateTime> nextCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), nextCaptor.capture(), any());
        assertThat(nextCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(3));
        verify(finalizer, never()).abort(any(SceneStepRun.class), anyInt(), anyString());
    }

    @Test
    void should_double_backoff_on_second_failure() {
        SceneStepRun run = pendingStepRun();
        run.setAttemptCount(1);
        stubStepRun(run);
        stubTriggerContextFailing();

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<LocalDateTime> nextCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(2), nextCaptor.capture(), any());
        assertThat(nextCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(8));
    }

    @Test
    void should_cap_backoff_at_configured_max() {
        properties.setRetryBaseDelayMs(1000);
        properties.setRetryMaxDelayMs(4000);
        SceneStepRun run = pendingStepRun();
        run.setAttemptCount(2);
        stubStepRun(run);
        stubTriggerContextFailing();

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<LocalDateTime> nextCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(3), nextCaptor.capture(), any());
        // 第 3 次退避仍封顶 4s，不会因翻倍溢出
        assertThat(nextCaptor.getValue()).isBefore(LocalDateTime.now().plusSeconds(6));
    }

    @Test
    void should_abort_when_retry_exhausted() {
        properties.setRetryMaxAttempts(1);
        SceneStepRun run = pendingStepRun();
        run.setAttemptCount(1);
        stubStepRun(run);
        stubTriggerContextFailing();

        service.executeStep(STEP_RUN_ID);

        verify(finalizer).abort(eq(run), eq(2), contains("未在物模型中定义"));
        verify(stepRunMapper, never()).markRetry(any(), anyInt(), any(), any());
    }

    @Test
    void should_retry_when_execution_record_missing() {
        stubStepRun(pendingStepRun());
        when(stepMapper.selectById(STEP_ID)).thenReturn(sceneStep(SceneConstants.ACTION_UPDATE_PROPERTY,
                SceneConstants.TARGET_TRIGGER, null, "{\"identifier\":\"switch\",\"value\":\"on\"}"));

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("执行记录不存在"));
    }

    @Test
    void should_retry_when_scene_deleted() {
        stubStepRun(pendingStepRun());
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution());
        when(stepMapper.selectById(STEP_ID)).thenReturn(sceneStep(SceneConstants.ACTION_UPDATE_PROPERTY,
                SceneConstants.TARGET_TRIGGER, null, "{\"identifier\":\"switch\",\"value\":\"on\"}"));
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(null);

        service.executeStep(STEP_RUN_ID);

        verify(stepRunMapper).markRetry(eq(STEP_RUN_ID), eq(1), any(), contains("场景或步骤已不存在"));
    }

    @Test
    void should_truncate_error_message_at_limit() {
        properties.setRetryMaxAttempts(1);
        SceneStepRun run = pendingStepRun();
        run.setAttemptCount(1);
        stubStepRun(run);
        stubTriggerContextFailing();

        service.executeStep(STEP_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(finalizer).abort(eq(run), eq(2), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).hasSizeLessThanOrEqualTo(512);
    }

    // ---------- 辅助 ----------

    private void stubSuccessfulUpdate() {
        stubStepRun(pendingStepRun());
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));
    }

    /** 触发目标 + 属性目标未在物模型中定义（用于稳定走失败分支）。 */
    private void stubTriggerContextFailing() {
        stubTriggerContext(SceneConstants.ACTION_UPDATE_PROPERTY,
                "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(DEVICE_ID, PRODUCT_ID, USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));
    }

    private void stubStepRun(SceneStepRun run) {
        when(stepRunMapper.claimPending(STEP_RUN_ID)).thenReturn(1);
        when(stepRunMapper.selectById(STEP_RUN_ID)).thenReturn(run);
    }

    private void stubTriggerContext(String actionType, String actionConfig) {
        stubContext(actionType, SceneConstants.TARGET_TRIGGER, null, actionConfig);
    }

    private void stubFixedContext(String actionType, String targetConfig, String actionConfig) {
        stubContext(actionType, SceneConstants.TARGET_FIXED, targetConfig, actionConfig);
    }

    private void stubForwardContext(String actionType, String actionConfig) {
        stubContext(actionType, SceneConstants.TARGET_TRIGGER, null, actionConfig);
    }

    private void stubContext(String actionType, String targetType, String targetConfig, String actionConfig) {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution());
        when(stepMapper.selectById(STEP_ID))
                .thenReturn(sceneStep(actionType, targetType, targetConfig, actionConfig));
        when(definitionMapper.selectById(SCENE_ID)).thenReturn(scene());
    }

    private SceneStepRun pendingStepRun() {
        SceneStepRun run = new SceneStepRun();
        run.setId(STEP_RUN_ID);
        run.setExecutionId(EXECUTION_ID);
        run.setSceneId(SCENE_ID);
        run.setStepId(STEP_ID);
        run.setSeq(1);
        run.setActionType(SceneConstants.ACTION_UPDATE_PROPERTY);
        run.setDelaySeconds(0);
        run.setStatus(SceneConstants.STEP_PENDING);
        run.setAttemptCount(0);
        run.setCreatedAt(NOW);
        return run;
    }

    private SceneStepRun nextStepRun(int delaySeconds) {
        SceneStepRun next = new SceneStepRun();
        next.setId(NEXT_STEP_RUN_ID);
        next.setExecutionId(EXECUTION_ID);
        next.setSceneId(SCENE_ID);
        next.setStepId(STEP_ID + 1);
        next.setSeq(2);
        next.setDelaySeconds(delaySeconds);
        next.setStatus(SceneConstants.STEP_PENDING);
        next.setAttemptCount(0);
        return next;
    }

    private SceneStep sceneStep(String actionType, String targetType, String targetConfig,
                                String actionConfig) {
        SceneStep step = new SceneStep();
        step.setId(STEP_ID);
        step.setSceneId(SCENE_ID);
        step.setSeq(1);
        step.setActionType(actionType);
        step.setTargetType(targetType);
        step.setTargetConfig(targetConfig);
        step.setActionConfig(actionConfig);
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
        execution.setTriggerDeviceKey("dev-1");
        execution.setTriggerDeviceName("设备1");
        execution.setTriggerIdentifier("temperature");
        execution.setTriggerValue("41");
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

    private ThingModelDefinition emptyModel() {
        return new ThingModelDefinition(1, Map.of(), Map.of(), Map.of());
    }
}
