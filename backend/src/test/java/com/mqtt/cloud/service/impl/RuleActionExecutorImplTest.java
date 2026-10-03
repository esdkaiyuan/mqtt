package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.RuleDefinitionMapper;
import com.mqtt.cloud.mapper.RuleExecutionMapper;
import com.mqtt.cloud.mqtt.RuleMqttForwarder;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.RuleHttpForwarder;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
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
 * 规则动作执行单测（T-19 实施计划 P7）。
 * <p>
 * 覆盖四类动作（更新属性 / 下发命令 / 转发 MQTT / 转发 HTTP）、失败转重试与退避封顶、
 * 重试耗尽置终态、载荷模板非法 JSON、非 {@code PENDING} 记录跳过，以及设备 / 规则失效兜底。
 */
class RuleActionExecutorImplTest {

    private static final Long USER_ID = 10L;
    private static final Long RULE_ID = 5L;
    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long EXECUTION_ID = 900L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    private RuleExecutionMapper executionMapper;
    private RuleDefinitionMapper definitionMapper;
    private DeviceMapper deviceMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private DeviceCommandService commandService;
    private ThingModelService thingModelService;
    private RuleMqttForwarder mqttForwarder;
    private RuleHttpForwarder httpForwarder;
    private RuleProperties properties;
    private RuleActionExecutorImpl service;

    @BeforeEach
    void setUp() {
        executionMapper = mock(RuleExecutionMapper.class);
        definitionMapper = mock(RuleDefinitionMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        commandService = mock(DeviceCommandService.class);
        thingModelService = mock(ThingModelService.class);
        mqttForwarder = mock(RuleMqttForwarder.class);
        httpForwarder = mock(RuleHttpForwarder.class);
        properties = new RuleProperties();
        service = new RuleActionExecutorImpl(executionMapper, definitionMapper, deviceMapper,
                propertyLatestMapper, commandService, thingModelService, mqttForwarder, httpForwarder,
                properties, new ObjectMapper());
    }

    // ---------- 记录加载 ----------

    @Test
    void execute_should_skip_when_record_not_pending() {
        RuleExecution execution = pendingExecution();
        execution.setStatus(RuleConstants.STATUS_SUCCESS);
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution);

        service.execute(EXECUTION_ID);

        verifyNoInteractions(definitionMapper, deviceMapper, propertyLatestMapper,
                commandService, mqttForwarder, httpForwarder);
        verify(executionMapper, never()).markSuccess(any(), any());
    }

    @Test
    void execute_should_noop_when_record_missing() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(null);

        service.execute(EXECUTION_ID);

        verifyNoInteractions(definitionMapper, deviceMapper, propertyLatestMapper);
    }

    // ---------- 更新属性 ----------

    @Test
    void update_property_should_upsert_and_mark_success() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"switch\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.execute(EXECUTION_ID);

        verify(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("switch"), eq("bool"), eq("on"), any());
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
        verify(definitionMapper).touchLastTriggered(eq(RULE_ID), any());
    }

    @Test
    void update_property_should_retry_when_identifier_unknown() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.execute(EXECUTION_ID);

        verify(propertyLatestMapper, never()).upsertIfNewer(any(), any(), any(), any(), any());
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("未在物模型中定义"));
    }

    @Test
    void update_property_should_retry_when_target_readonly() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"switch\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "r"));

        service.execute(EXECUTION_ID);

        verify(propertyLatestMapper, never()).upsertIfNewer(any(), any(), any(), any(), any());
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("不可写"));
    }

    // ---------- 下发命令 ----------

    @Test
    void send_command_should_invoke_with_async_and_rule_source() {
        stubExecution();
        stubRule(RuleConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"service\",\"identifier\":\"reboot\",\"params\":{\"delay\":5}}");
        stubDevice();

        service.execute(EXECUTION_ID);

        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(commandService).invoke(captor.capture());
        DeviceCommandService.CommandInvoke invoke = captor.getValue();
        assertThat(invoke.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(invoke.commandType()).isEqualTo(DeviceCommandService.TYPE_SERVICE);
        assertThat(invoke.identifier()).isEqualTo("reboot");
        assertThat(invoke.paramsJson()).isEqualTo("{\"delay\":5}");
        assertThat(invoke.callType()).isEqualTo(DeviceCommandService.CALL_TYPE_ASYNC);
        assertThat(invoke.source()).isEqualTo(DeviceCommandService.SOURCE_RULE);
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void send_command_property_set_should_pass_null_identifier() {
        stubExecution();
        stubRule(RuleConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"property_set\",\"identifier\":\"switch\",\"params\":{\"switch\":true}}");
        stubDevice();

        service.execute(EXECUTION_ID);

        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(commandService).invoke(captor.capture());
        assertThat(captor.getValue().commandType()).isEqualTo(DeviceCommandService.TYPE_PROPERTY_SET);
        assertThat(captor.getValue().identifier()).isNull();
    }

    @Test
    void send_command_should_retry_when_invoke_fails() {
        stubExecution();
        stubRule(RuleConstants.ACTION_SEND_COMMAND,
                "{\"commandType\":\"service\",\"identifier\":\"reboot\"}");
        stubDevice();
        when(commandService.invoke(any())).thenThrow(new BusinessException(ResultCode.COMMAND_NOT_FOUND));

        service.execute(EXECUTION_ID);

        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("下发命令失败"));
    }

    // ---------- 转发 MQTT ----------

    @Test
    void forward_mqtt_should_publish_and_snapshot_payload() throws MqttException {
        properties.getMqtt().setEnabled(true);
        stubExecution();
        stubRule(RuleConstants.ACTION_FORWARD_MQTT,
                "{\"topic\":\"factory/line1\",\"qos\":1,\"payloadTemplate\":\"{\\\"v\\\":${value}}\"}");
        stubDevice();

        service.execute(EXECUTION_ID);

        verify(mqttForwarder).publish(eq("factory/line1"), eq("{\"v\":41}"), eq(1));
        verify(executionMapper).updateForwardPayload(eq(EXECUTION_ID), eq("{\"v\":41}"));
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void forward_mqtt_should_retry_when_publish_fails() throws MqttException {
        properties.getMqtt().setEnabled(true);
        stubExecution();
        stubRule(RuleConstants.ACTION_FORWARD_MQTT, "{\"topic\":\"factory/line1\"}");
        stubDevice();
        doThrow(new BusinessException(ResultCode.RULE_INVALID, "外部 MQTT 转发未启用"))
                .when(mqttForwarder).publish(anyString(), anyString(), anyInt());

        service.execute(EXECUTION_ID);

        verify(executionMapper).updateForwardPayload(eq(EXECUTION_ID), anyString());
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("MQTT 转发失败"));
    }

    // ---------- 转发 HTTP ----------

    @Test
    void forward_http_should_send_with_ids_and_snapshot() {
        stubExecution();
        stubRule(RuleConstants.ACTION_FORWARD_HTTP,
                "{\"url\":\"https://example.com/hook\",\"method\":\"POST\","
                        + "\"payloadTemplate\":\"{\\\"v\\\":${value}}\"}");
        stubDevice();

        service.execute(EXECUTION_ID);

        verify(httpForwarder).send(eq("POST"), eq("https://example.com/hook"), anyMap(),
                eq("{\"v\":41}"), eq(RULE_ID), eq(EXECUTION_ID));
        verify(executionMapper).updateForwardPayload(eq(EXECUTION_ID), eq("{\"v\":41}"));
        verify(executionMapper).markSuccess(eq(EXECUTION_ID), any());
    }

    @Test
    void forward_http_should_retry_when_target_errors() {
        stubExecution();
        stubRule(RuleConstants.ACTION_FORWARD_HTTP, "{\"url\":\"https://example.com/hook\"}");
        stubDevice();
        doThrow(new IllegalStateException("转发失败：目标返回状态码 500"))
                .when(httpForwarder).send(any(), any(), any(), any(), any(), any());

        service.execute(EXECUTION_ID);

        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("HTTP 转发失败"));
    }

    @Test
    void forward_http_should_retry_when_payload_not_json() {
        stubExecution();
        stubRule(RuleConstants.ACTION_FORWARD_HTTP,
                "{\"url\":\"https://example.com/hook\",\"payloadTemplate\":\"not-json-${value}\"}");
        stubDevice();

        service.execute(EXECUTION_ID);

        verify(httpForwarder, never()).send(any(), any(), any(), any(), any(), any());
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("不是合法 JSON"));
    }

    // ---------- 重试退避与终态 ----------

    @Test
    void should_retry_with_backoff_within_limit() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.execute(EXECUTION_ID);

        ArgumentCaptor<LocalDateTime> nextCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), nextCaptor.capture(), any());
        assertThat(nextCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(3));
        verify(executionMapper, never()).markFailed(any(), anyInt(), any(), any());
    }

    @Test
    void should_double_backoff_on_second_failure() {
        RuleExecution execution = pendingExecution();
        execution.setAttemptCount(1);
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(execution);
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.execute(EXECUTION_ID);

        ArgumentCaptor<LocalDateTime> nextCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(2), nextCaptor.capture(), any());
        assertThat(nextCaptor.getValue()).isAfter(LocalDateTime.now().plusSeconds(8));
    }

    @Test
    void should_mark_failed_when_retry_exhausted() {
        properties.setRetryMaxAttempts(1);
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"ghost\",\"value\":\"on\"}");
        stubDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWith("switch", "rw"));

        service.execute(EXECUTION_ID);

        verify(executionMapper).markFailed(eq(EXECUTION_ID), eq(1), any(), any());
        verify(executionMapper, never()).markRetry(any(), anyInt(), any(), any());
    }

    // ---------- 失效兜底 ----------

    @Test
    void should_retry_when_rule_deleted() {
        stubExecution();
        when(definitionMapper.selectById(RULE_ID)).thenReturn(null);

        service.execute(EXECUTION_ID);

        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("规则已删除"));
    }

    @Test
    void should_retry_when_device_owner_changed() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(999L));

        service.execute(EXECUTION_ID);

        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("归属已变更"));
    }

    @Test
    void should_retry_when_device_missing() {
        stubExecution();
        stubRule(RuleConstants.ACTION_UPDATE_PROPERTY, "{\"identifier\":\"switch\",\"value\":\"on\"}");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        service.execute(EXECUTION_ID);

        verify(executionMapper).markRetry(eq(EXECUTION_ID), eq(1), any(), contains("设备不存在"));
    }

    // ---------- 辅助 ----------

    private void stubExecution() {
        when(executionMapper.selectById(EXECUTION_ID)).thenReturn(pendingExecution());
    }

    private void stubRule(String actionType, String actionConfig) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("高温联动");
        rule.setActionType(actionType);
        rule.setActionConfig(actionConfig);
        when(definitionMapper.selectById(RULE_ID)).thenReturn(rule);
    }

    private void stubDevice() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(USER_ID));
    }

    private RuleExecution pendingExecution() {
        RuleExecution execution = new RuleExecution();
        execution.setId(EXECUTION_ID);
        execution.setUserId(USER_ID);
        execution.setRuleId(RULE_ID);
        execution.setRuleName("高温联动");
        execution.setDeviceId(DEVICE_ID);
        execution.setDeviceKey("dev-1");
        execution.setDeviceName("设备1");
        execution.setSourceType(RuleConstants.SOURCE_PROPERTY);
        execution.setIdentifier("temperature");
        execution.setTriggerValue("41");
        execution.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        execution.setStatus(RuleConstants.STATUS_PENDING);
        execution.setAttemptCount(0);
        execution.setCreatedAt(NOW);
        return execution;
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey("dev-1");
        device.setDeviceName("设备1");
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