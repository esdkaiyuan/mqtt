package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DebugProperties;
import com.mqtt.cloud.dto.response.DebugCodeResponse;
import com.mqtt.cloud.dto.response.DebugContextResponse;
import com.mqtt.cloud.dto.response.DebugSimulateResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.TopicPrefixResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备调试服务单测（T-37）。
 * <p>
 * 重点守三件事：Topic 必须走产品模板、模拟上报必须与真机同形进入 {@link IngestPipeline}、
 * 代码生成不得包含任何密钥明文。
 */
class DeviceDebugServiceImplTest {

    private static final Long DEVICE_ID = 11L;
    private static final String DEVICE_KEY = "sensor-001";
    private static final String PRODUCT_KEY = "env-sensor";

    private DebugProperties debugProperties;
    private ThingModelService thingModelService;
    private DeviceDataService deviceDataService;
    private IngestPipeline ingestPipeline;
    private IngestProperties ingestProperties;
    private DeviceDebugServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        debugProperties = new DebugProperties();
        debugProperties.setPublicBrokerHost("iot.example.com");
        debugProperties.setMaxPayloadBytes(1024);
        thingModelService = mock(ThingModelService.class);
        deviceDataService = mock(DeviceDataService.class);
        ingestPipeline = mock(IngestPipeline.class);
        ingestProperties = new IngestProperties();
        service = new DeviceDebugServiceImpl(debugProperties, thingModelService, deviceDataService,
                ingestPipeline, ingestProperties, new TopicPrefixResolver());
        when(thingModelService.getForProduct(20L)).thenReturn(model());
    }

    @Test
    void context_should_project_topics_model_and_latest_values() {
        DevicePropertyLatest latest = new DevicePropertyLatest();
        latest.setIdentifier("temperature");
        latest.setValueText("26.5");
        latest.setReportedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
        when(deviceDataService.getPropertyLatest(DEVICE_ID)).thenReturn(List.of(latest));

        DebugContextResponse context = service.context(device(), product());

        assertThat(context.deviceKey()).isEqualTo(DEVICE_KEY);
        assertThat(context.modeled()).isTrue();
        assertThat(context.thingModelVersion()).isEqualTo(3);
        assertThat(context.broker().tcp()).isEqualTo("tcp://iot.example.com:1883");
        assertThat(context.broker().ws()).isEqualTo("ws://iot.example.com:8083/mqtt");
        assertThat(context.broker().username()).isEqualTo("env-sensor.sensor-001");
        assertThat(context.topics().data()).isEqualTo("iot/env-sensor/sensor-001/data");
        assertThat(context.topics().commandDown()).isEqualTo("device/sensor-001/cmd/down");
        assertThat(context.properties()).extracting(DebugContextResponse.PropertyView::identifier)
                .containsExactly("mode", "power", "temperature");
        assertThat(context.properties().get(2).value()).isEqualTo("26.5");
        assertThat(context.writableProperties()).extracting(DebugContextResponse.PropertyView::identifier)
                .containsExactly("mode", "power");
        assertThat(context.services()).extracting(DebugContextResponse.ServiceView::identifier)
                .containsExactly("reboot");
    }

    @Test
    void simulate_should_submit_ingest_record_with_product_template_topic() {
        DebugSimulateResponse result = service.simulate(10L, device(), product(), "data",
                objectMapper.readTree("{\"temperature\": 26.5}"));

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline).submit(captor.capture());
        IngestRecord record = captor.getValue();
        assertThat(record.deviceKey()).isEqualTo(DEVICE_KEY);
        assertThat(record.topic()).isEqualTo("iot/env-sensor/sensor-001/data");
        assertThat(record.messageType()).isEqualTo("data");
        assertThat(record.payload()).isEqualTo("{\"temperature\":26.5}");
        assertThat(record.qos()).isEqualTo(1);
        assertThat(result.submitted()).isTrue();
        assertThat(result.topic()).isEqualTo("iot/env-sensor/sensor-001/data");
    }

    @Test
    void simulate_heartbeat_should_allow_empty_payload_and_qos_zero() {
        DebugSimulateResponse result = service.simulate(10L, device(), product(), "heartbeat", null);

        ArgumentCaptor<IngestRecord> captor = ArgumentCaptor.forClass(IngestRecord.class);
        verify(ingestPipeline).submit(captor.capture());
        assertThat(captor.getValue().payload()).isEmpty();
        assertThat(captor.getValue().qos()).isZero();
        assertThat(result.messageType()).isEqualTo("heartbeat");
    }

    @Test
    void simulate_should_reject_unknown_type() {
        assertCode(() -> service.simulate(10L, device(), product(), "command", null),
                ResultCode.DEBUG_MESSAGE_TYPE_UNSUPPORTED);
        verifyNoInteractions(ingestPipeline);
    }

    @Test
    void simulate_should_reject_non_object_payload_for_data() {
        assertCode(() -> service.simulate(10L, device(), product(), "data",
                objectMapper.readTree("\"text\"")), ResultCode.DEBUG_PAYLOAD_INVALID);
        verifyNoInteractions(ingestPipeline);
    }

    @Test
    void simulate_should_reject_oversized_payload() {
        debugProperties.setMaxPayloadBytes(5);
        assertCode(() -> service.simulate(10L, device(), product(), "data",
                objectMapper.readTree("{\"temperature\": 26.5}")), ResultCode.DEBUG_PAYLOAD_INVALID);
        verifyNoInteractions(ingestPipeline);
    }

    @Test
    void simulate_should_report_not_submitted_when_ingest_disabled() {
        ingestProperties.setEnabled(false);

        DebugSimulateResponse result = service.simulate(10L, device(), product(), "data",
                objectMapper.readTree("{\"temperature\": 1}"));

        assertThat(result.submitted()).isFalse();
        verify(ingestPipeline, never()).submit(any());
    }

    @Test
    void code_device_should_contain_topics_and_no_secret() {
        DebugCodeResponse code = service.code(device(), product(), "device", "javascript");

        assertThat(code.filename()).isEqualTo("device-sensor-001.js");
        assertThat(code.content()).contains("iot/env-sensor/sensor-001/data");
        assertThat(code.content()).contains("device/sensor-001/cmd/down");
        assertThat(code.content()).contains("temperature");
        assertThat(code.content()).contains("process.env.DEVICE_SECRET");
        assertThat(code.content()).doesNotContain("deviceSecret");
    }

    @Test
    void code_device_java_should_escape_payload_quotes_and_keep_alink_envelope() {
        DebugCodeResponse code = service.code(device(), product(), "device", "java");

        // 生成的 Java 源码里 JSON 必须作为合法字符串字面量出现（引号已转义），
        // 且外层必须是物模型解析认的 Alink 信封
        assertThat(code.content()).contains("\\\"method\\\":\\\"thing.event.property.post\\\"");
        assertThat(code.content()).contains("\\\"temperature\\\": -40");
        assertThat(code.content()).contains("\\\"params\\\":{\\\"mode\\\": \\\"auto\\\"");
    }

    @Test
    void code_host_should_generate_open_api_samples() {
        DebugCodeResponse code = service.code(device(), product(), "host", "python");

        assertThat(code.filename()).isEqualTo("host-sensor-001.py");
        assertThat(code.content()).contains("/devices/sensor-001/properties");
        assertThat(code.content()).contains("/devices/sensor-001/shadow/desired");
        assertThat(code.content()).contains("/devices/sensor-001/command");
        assertThat(code.content()).contains("X-API-Key");
    }

    @Test
    void code_should_reject_unsupported_target_or_language() {
        assertCode(() -> service.code(device(), product(), "server", "java"),
                ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED);
        assertCode(() -> service.code(device(), product(), "device", "rust"),
                ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED);
        assertCode(() -> service.code(device(), product(), "host", "arduino"),
                ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ResultCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey(DEVICE_KEY);
        device.setDeviceName("温湿度传感器");
        device.setProductId(20L);
        device.setStatus("ONLINE");
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(20L);
        product.setProductKey(PRODUCT_KEY);
        product.setProductName("环境传感器");
        product.setTopicPrefix("iot/{productKey}/{deviceKey}");
        product.setProtocolType("MQTT");
        product.setAuthMode("SECRET");
        return product;
    }

    private ThingModelDefinition model() {
        ThingModelDefinition.PropertySpec temperature = new ThingModelDefinition.PropertySpec(
                "temperature", "float", new BigDecimal("-40"), new BigDecimal("85"), false,
                Set.of(), null, "r");
        ThingModelDefinition.PropertySpec power = new ThingModelDefinition.PropertySpec(
                "power", "bool", null, null, false, Set.of(), null, "rw");
        ThingModelDefinition.PropertySpec mode = new ThingModelDefinition.PropertySpec(
                "mode", "enum", null, null, false, Set.of("auto", "manual"), null, "rw");
        ThingModelDefinition.EventSpec event = new ThingModelDefinition.EventSpec("overheat", "alert");
        ThingModelDefinition.ServiceSpec service = new ThingModelDefinition.ServiceSpec(
                "reboot", "async", Map.of(), Map.of());
        return new ThingModelDefinition(3,
                Map.of("temperature", temperature, "power", power, "mode", mode),
                Map.of("overheat", event),
                Map.of("reboot", service));
    }
}
