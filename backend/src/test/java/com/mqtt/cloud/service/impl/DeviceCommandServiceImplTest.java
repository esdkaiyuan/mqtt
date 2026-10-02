package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.CommandProperties;
import com.mqtt.cloud.dto.response.ThingModelResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceCommandRecordMapper;
import com.mqtt.cloud.mqtt.MqttClientManager;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 命令下发服务单测（T-15 实施计划 P8）。
 * <p>
 * 覆盖：四类参数拒绝（6201/6202/6203/6204）、发布成功置 {@code SENT} 且载荷/主题正确、
 * 发布异常置 {@code FAILED} 并抛 {@code 4001}、同步等待终态与同步超时置 {@code TIMEOUT}、
 * 设备禁用拒绝。回执驱动状态更新的部分见 {@code CommandReplyServiceImplTest}。
 */
class DeviceCommandServiceImplTest {

    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final String DEVICE_KEY = "dev-1";
    private static final String PRODUCT_KEY = "pk-1";

    /** 含一个可写属性 power 与一个带必填入参的服务 setMode。 */
    private static final String TSL = """
            {"schemaVersion":"1.0",
             "properties":[{"identifier":"power","name":"电源","dataType":{"type":"bool"},"accessMode":"rw"},
                           {"identifier":"firmware","name":"固件版本","dataType":{"type":"text","length":32},"accessMode":"r"}],
             "events":[],
             "services":[{"identifier":"setMode","name":"设置模式","callType":"async",
                          "inputData":[{"identifier":"mode","name":"模式","required":true,
                                        "dataType":{"type":"enum","specs":{"auto":"自动","manual":"手动"}}}]}]}
            """;

    private DeviceService deviceService;
    private ProductService productService;
    private DeviceAccessGuard accessGuard;
    private ThingModelService thingModelService;
    private DeviceCommandRecordMapper commandMapper;
    private MqttClientManager mqttClientManager;
    private CommandProperties properties;
    private DeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceService = mock(DeviceService.class);
        productService = mock(ProductService.class);
        accessGuard = mock(DeviceAccessGuard.class);
        thingModelService = mock(ThingModelService.class);
        commandMapper = mock(DeviceCommandRecordMapper.class);
        mqttClientManager = mock(MqttClientManager.class);
        properties = new CommandProperties();
        properties.setPollIntervalMs(1);
        properties.setSyncTimeoutMs(40);
        service = new DeviceCommandServiceImpl(deviceService, productService, accessGuard,
                thingModelService, commandMapper, mqttClientManager, properties, new ObjectMapper());

        when(deviceService.getById(DEVICE_ID)).thenReturn(device(1));
        when(productService.requireById(PRODUCT_ID)).thenReturn(product());
        when(accessGuard.resolve(PRODUCT_KEY, DEVICE_KEY))
                .thenReturn(new DeviceAuthCacheService.AuthMeta(PRODUCT_ID, "ENABLED", 1, "hash"));
        when(accessGuard.isPermitted(any())).thenReturn(true);
        when(thingModelService.get(PRODUCT_ID))
                .thenReturn(new ThingModelResponse(TSL, 3, LocalDateTime.of(2026, 10, 2, 12, 0)));
        when(commandMapper.insert(any(DeviceCommandRecord.class))).thenReturn(1);
    }

    // ---------- 四类拒绝 ----------

    @Test
    void invoke_should_reject_when_model_missing() {
        when(thingModelService.get(PRODUCT_ID)).thenReturn(new ThingModelResponse(null, 0, null));

        assertCode(ResultCode.COMMAND_MODEL_MISSING, propertySet("{\"power\":true}"));
        verify(commandMapper, never()).insert(any(DeviceCommandRecord.class));
    }

    @Test
    void invoke_should_reject_readonly_property() {
        assertCode(ResultCode.COMMAND_PROPERTY_READONLY, propertySet("{\"firmware\":\"1.0\"}"));
    }

    @Test
    void invoke_should_reject_unknown_property_identifier() {
        assertCode(ResultCode.COMMAND_IDENTIFIER_UNKNOWN, propertySet("{\"unknown\":true}"));
    }

    @Test
    void invoke_should_reject_property_value_type_mismatch() {
        assertCode(ResultCode.COMMAND_PARAM_INVALID, propertySet("{\"power\":\"yes\"}"));
    }

    @Test
    void invoke_should_reject_property_set_with_identifier() {
        DeviceCommandService.CommandInvoke command = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "property_set", "power", "{\"power\":true}", "async", "CONSOLE", 1L);

        assertCode(ResultCode.COMMAND_PARAM_INVALID, command);
    }

    @Test
    void invoke_should_reject_unknown_service() {
        DeviceCommandService.CommandInvoke command = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "service", "noSuchService", "{\"mode\":\"auto\"}", "async", "CONSOLE", 1L);

        assertCode(ResultCode.COMMAND_IDENTIFIER_UNKNOWN, command);
    }

    @Test
    void invoke_should_reject_service_missing_required_param() {
        DeviceCommandService.CommandInvoke command = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "service", "setMode", "{\"other\":1}", "async", "CONSOLE", 1L);

        assertCode(ResultCode.COMMAND_PARAM_INVALID, command);
    }

    @Test
    void invoke_should_reject_invalid_type_and_call_type() {
        DeviceCommandService.CommandInvoke badType = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "fire", null, "{\"power\":true}", "async", "CONSOLE", 1L);
        DeviceCommandService.CommandInvoke badCall = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "property_set", null, "{\"power\":true}", "later", "CONSOLE", 1L);

        assertCode(ResultCode.COMMAND_PARAM_INVALID, badType);
        assertCode(ResultCode.COMMAND_PARAM_INVALID, badCall);
    }

    @Test
    void invoke_should_reject_empty_or_non_object_params() {
        assertCode(ResultCode.COMMAND_PARAM_INVALID, propertySet("{}"));
        assertCode(ResultCode.COMMAND_PARAM_INVALID, propertySet("[1,2]"));
        assertCode(ResultCode.COMMAND_PARAM_INVALID, propertySet("not-json"));
    }

    @Test
    void invoke_should_reject_when_device_disabled() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(0));
        when(accessGuard.isPermitted(any())).thenReturn(false);

        assertCode(ResultCode.DEVICE_DISABLED, propertySet("{\"power\":true}"));
    }

    // ---------- 发布成功 / 失败 ----------

    @Test
    void invoke_async_should_publish_and_mark_sent() throws Exception {
        DeviceCommandRecord result = service.invoke(propertySet("{\"power\":true}"));

        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getCommandId()).isNotBlank();
        assertThat(result.getSentAt()).isNotNull();

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mqttClientManager).publish(topic.capture(), payload.capture(), eq(1));
        assertThat(topic.getValue()).isEqualTo("device/" + DEVICE_KEY + "/cmd/down");

        JsonNode body = new ObjectMapper().readTree(payload.getValue());
        assertThat(body.get("id").asText()).isEqualTo(result.getCommandId());
        assertThat(body.get("version").asText()).isEqualTo("1.0");
        assertThat(body.get("method").asText()).isEqualTo("thing.service.property.set");
        assertThat(body.get("params").get("power").asBoolean()).isTrue();

        verify(commandMapper).markSent(eq(result.getCommandId()), any());
    }

    @Test
    void invoke_service_should_use_service_method_name() throws Exception {
        DeviceCommandService.CommandInvoke command = new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "service", "setMode", "{\"mode\":\"auto\"}", "async", "OPEN_API", null);

        DeviceCommandRecord result = service.invoke(command);

        assertThat(result.getIdentifier()).isEqualTo("setMode");
        assertThat(result.getSource()).isEqualTo("OPEN_API");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mqttClientManager).publish(anyString(), payload.capture(), eq(1));
        JsonNode body = new ObjectMapper().readTree(payload.getValue());
        assertThat(body.get("method").asText()).isEqualTo("thing.service.setMode");
        assertThat(body.get("params").get("mode").asText()).isEqualTo("auto");
    }

    @Test
    void invoke_should_mark_failed_and_throw_when_publish_fails() throws Exception {
        doThrow(new RuntimeException("broker down"))
                .when(mqttClientManager).publish(anyString(), anyString(), anyInt());

        assertCode(ResultCode.MQTT_PUBLISH_FAILED, propertySet("{\"power\":true}"));

        verify(commandMapper).markFailed(anyString(), anyString(), any());
        verify(commandMapper, never()).markSent(anyString(), any());
    }

    // ---------- 同步等待 ----------

    @Test
    void invoke_sync_should_return_terminal_record_when_acked() {
        DeviceCommandRecord acked = new DeviceCommandRecord();
        acked.setId(1L);
        acked.setCommandId("cmd");
        acked.setStatus("ACKED");
        when(commandMapper.selectById(any())).thenReturn(acked);

        DeviceCommandRecord result = service.invoke(syncPropertySet());

        assertThat(result.getStatus()).isEqualTo("ACKED");
        verify(commandMapper, never()).markTimeout(anyString(), anyString(), any());
    }

    @Test
    void invoke_sync_should_mark_timeout_when_no_terminal() {
        AtomicReference<DeviceCommandRecord> stored = new AtomicReference<>(pendingRecord());
        when(commandMapper.selectById(any())).thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            stored.get().setStatus("TIMEOUT");
            return 1;
        }).when(commandMapper).markTimeout(anyString(), anyString(), any());

        DeviceCommandRecord result = service.invoke(syncPropertySet());

        assertThat(result.getStatus()).isEqualTo("TIMEOUT");
        verify(commandMapper).markTimeout(anyString(), anyString(), any());
    }

    // ---------- 能力查询 ----------

    @Test
    void getCapability_should_expose_writable_properties_and_services() {
        DeviceCommandService.CommandCapability capability = service.getCapability(PRODUCT_ID);

        assertThat(capability.modeled()).isTrue();
        assertThat(capability.version()).isEqualTo(3);
        assertThat(capability.properties()).extracting("identifier").containsExactly("power");
        assertThat(capability.services()).extracting("identifier").containsExactly("setMode");
    }

    @Test
    void getCapability_should_return_empty_when_unmodeled() {
        when(thingModelService.get(PRODUCT_ID)).thenReturn(new ThingModelResponse(null, 0, null));

        DeviceCommandService.CommandCapability capability = service.getCapability(PRODUCT_ID);

        assertThat(capability.modeled()).isFalse();
        assertThat(capability.properties()).isEmpty();
        assertThat(capability.services()).isEmpty();
    }

    // ---------- 超时巡检 ----------

    @Test
    void sweepTimeouts_should_sweep_sync_and_async_separately() {
        when(commandMapper.sweepTimeout(anyString(), any(), anyString(), anyInt())).thenReturn(2, 3);

        int swept = service.sweepTimeouts();

        assertThat(swept).isEqualTo(5);
        verify(commandMapper).sweepTimeout(eq("sync"), any(), anyString(), anyInt());
        verify(commandMapper).sweepTimeout(eq("async"), any(), anyString(), anyInt());
    }

    // ---------- 辅助 ----------

    private DeviceCommandService.CommandInvoke propertySet(String paramsJson) {
        return new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "property_set", null, paramsJson, "async", "CONSOLE", 1L);
    }

    private DeviceCommandService.CommandInvoke syncPropertySet() {
        return new DeviceCommandService.CommandInvoke(
                DEVICE_ID, PRODUCT_ID, "property_set", null, "{\"power\":true}", "sync", "CONSOLE", 1L);
    }

    private DeviceCommandRecord pendingRecord() {
        DeviceCommandRecord record = new DeviceCommandRecord();
        record.setId(1L);
        record.setCommandId("cmd");
        record.setStatus("SENT");
        return record;
    }

    private void assertCode(ResultCode expected, DeviceCommandService.CommandInvoke command) {
        assertThatThrownBy(() -> service.invoke(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private Device device(int enabled) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey(DEVICE_KEY);
        device.setEnabled(enabled);
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductKey(PRODUCT_KEY);
        product.setStatus("ENABLED");
        return product;
    }
}