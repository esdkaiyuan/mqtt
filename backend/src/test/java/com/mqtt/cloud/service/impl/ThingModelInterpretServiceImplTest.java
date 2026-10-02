package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ThingModelProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.ingest.ResolvedEvent;
import com.mqtt.cloud.mapper.DeviceEventRecordMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 上行解析链路单测（T-14 实施计划 P4）。
 * <p>
 * 覆盖属性 / 事件 / 未建模 / 非 Alink / 未知标识符 / 类型与范围拒绝 / 乱序时间戳透传 /
 * 逐条异常隔离，以及解析指标计数。DB 层的时间戳守卫由 Mapper SQL 保证，本层只断言
 * {@code receivedAt} 被原样透传（乱序旧包的最终裁决在 {@code upsertIfNewer}）。
 */
class ThingModelInterpretServiceImplTest {

    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 10, 2, 12, 0, 0);
    private static final String METRIC = "thing_model_interpret";

    private ThingModelService thingModelService;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private DeviceEventRecordMapper eventRecordMapper;
    private DeviceShadowService deviceShadowService;
    private ThingModelProperties properties;
    private SimpleMeterRegistry registry;
    private ThingModelInterpretServiceImpl service;

    @BeforeEach
    void setUp() {
        thingModelService = mock(ThingModelService.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        eventRecordMapper = mock(DeviceEventRecordMapper.class);
        deviceShadowService = mock(DeviceShadowService.class);
        properties = new ThingModelProperties();
        registry = new SimpleMeterRegistry();
        service = new ThingModelInterpretServiceImpl(thingModelService, propertyLatestMapper,
                eventRecordMapper, deviceShadowService, new ThingModelInterpretMetrics(registry),
                properties, new ObjectMapper());
    }

    @Test
    void interpret_should_skip_all_when_disabled() {
        properties.setInterpretEnabled(false);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.5}}")));

        verifyNoInteractions(thingModelService, propertyLatestMapper, eventRecordMapper);
        assertThat(count("property")).isZero();
    }

    @Test
    void interpret_should_noop_when_events_empty_or_null() {
        service.interpret(List.of());
        service.interpret(null);

        verifyNoInteractions(thingModelService, propertyLatestMapper, eventRecordMapper);
    }

    @Test
    void interpret_should_ignore_non_data_message_type() {
        ResolvedEvent heartbeat = new ResolvedEvent(device(),
                new IngestRecord("dev-1", "t", "heartbeat", "{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.5}}", 0, RECEIVED_AT));

        service.interpret(List.of(heartbeat));

        verifyNoInteractions(thingModelService, propertyLatestMapper, eventRecordMapper);
    }

    @Test
    void interpret_should_count_unmodeled_when_definition_empty() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(ThingModelDefinition.EMPTY);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.5}}")));

        assertThat(count("unmodeled")).isEqualTo(1);
        verifyNoInteractions(propertyLatestMapper, eventRecordMapper);
    }

    @Test
    void interpret_should_count_unparsed_when_payload_not_json_object() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("not-json")));
        service.interpret(List.of(dataEvent("[]")));

        assertThat(count("unparsed")).isEqualTo(2);
        verifyNoInteractions(propertyLatestMapper, eventRecordMapper);
    }

    @Test
    void interpret_should_count_unparsed_when_method_missing() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("{\"params\":{\"temperature\":25.5}}")));

        assertThat(count("unparsed")).isEqualTo(1);
        verifyNoInteractions(propertyLatestMapper, eventRecordMapper);
    }

    @Test
    void interpret_should_upsert_normalized_property_value() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.50}}")));

        verify(propertyLatestMapper).upsertIfNewer(DEVICE_ID, "temperature", "double", "25.5", RECEIVED_AT);
        assertThat(count("property")).isEqualTo(1);
    }

    @Test
    void interpret_should_count_unknown_identifier_for_undefined_property() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"humidity\":60}}")));

        assertThat(count("unknown_identifier")).isEqualTo(1);
        verifyNoInteractions(propertyLatestMapper);
    }

    @Test
    void interpret_should_reject_out_of_range_numeric_value() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(
                "battery", new ThingModelDefinition.PropertySpec("battery", "int",
                        BigDecimal.ZERO, BigDecimal.valueOf(100), true, Set.of(), null, "r")), Map.of(), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"battery\":200}}")));

        assertThat(count("unknown_identifier")).isEqualTo(1);
        verifyNoInteractions(propertyLatestMapper);
    }

    @Test
    void interpret_should_reject_wrong_type_value() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":\"warm\"}}")));

        assertThat(count("unknown_identifier")).isEqualTo(1);
        verifyNoInteractions(propertyLatestMapper);
    }

    @Test
    void interpret_should_normalize_bool_enum_and_struct() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(
                "power", new ThingModelDefinition.PropertySpec("power", "bool", null, null, false, Set.of(), null, "rw"),
                "mode", new ThingModelDefinition.PropertySpec("mode", "enum", null, null, false, Set.of("auto", "manual"), null, "rw"),
                "cfg", new ThingModelDefinition.PropertySpec("cfg", "struct", null, null, false, Set.of(), null, "r")), Map.of(), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\","
                + "\"params\":{\"power\":1,\"mode\":\"auto\",\"cfg\":{\"a\":1}}}")));

        verify(propertyLatestMapper).upsertIfNewer(DEVICE_ID, "power", "bool", "true", RECEIVED_AT);
        verify(propertyLatestMapper).upsertIfNewer(DEVICE_ID, "mode", "enum", "auto", RECEIVED_AT);
        verify(propertyLatestMapper).upsertIfNewer(DEVICE_ID, "cfg", "struct", "{\"a\":1}", RECEIVED_AT);
        assertThat(count("property")).isEqualTo(3);
    }

    @Test
    void interpret_should_insert_event_record_with_model_event_type() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(),
                Map.of("fall", new ThingModelDefinition.EventSpec("fall", "alert")), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.post\","
                + "\"params\":{\"eventId\":\"fall\",\"value\":{\"level\":3}}}")));

        ArgumentCaptor<List<DeviceEventRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRecordMapper).insertBatch(captor.capture());
        DeviceEventRecord saved = captor.getValue().get(0);
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getIdentifier()).isEqualTo("fall");
        assertThat(saved.getEventType()).isEqualTo("alert");
        assertThat(saved.getOutputData()).isEqualTo("{\"level\":3}");
        assertThat(saved.getReportedAt()).isEqualTo(RECEIVED_AT);
        assertThat(count("event")).isEqualTo(1);
    }

    @Test
    void interpret_should_count_unknown_identifier_for_undefined_event() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(),
                Map.of("fall", new ThingModelDefinition.EventSpec("fall", "alert")), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.post\",\"params\":{\"eventId\":\"unknown\"}}")));

        assertThat(count("unknown_identifier")).isEqualTo(1);
        verifyNoInteractions(eventRecordMapper);
    }

    @Test
    void interpret_should_isolate_failure_per_record() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(
                "boom", new ThingModelDefinition.PropertySpec("boom", "int", null, null, true, Set.of(), null, "r"),
                "temperature", new ThingModelDefinition.PropertySpec("temperature", "double", null, null, false, Set.of(), null, "r")),
                Map.of(), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);
        doThrow(new RuntimeException("db down"))
                .when(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("boom"), anyString(), anyString(), any());

        service.interpret(List.of(
                dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"boom\":1}}"),
                dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.5}}")));

        assertThat(count("error")).isEqualTo(1);
        assertThat(count("property")).isEqualTo(1);
        verify(propertyLatestMapper).upsertIfNewer(DEVICE_ID, "temperature", "double", "25.5", RECEIVED_AT);
    }

    @Test
    void interpret_should_count_error_when_event_batch_insert_fails() {
        ThingModelDefinition definition = new ThingModelDefinition(1, Map.of(),
                Map.of("fall", new ThingModelDefinition.EventSpec("fall", "alert")), Map.of());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition);
        doThrow(new RuntimeException("batch failed")).when(eventRecordMapper).insertBatch(any());

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.post\",\"params\":{\"eventId\":\"fall\"}}")));

        assertThat(count("error")).isEqualTo(1);
        assertThat(count("event")).isZero();
    }

    @Test
    void interpret_should_pass_received_at_through_for_out_of_order_guard() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithTemperature());

        service.interpret(List.of(dataEvent("{\"method\":\"thing.event.property.post\",\"params\":{\"temperature\":25.5}}")));

        verify(propertyLatestMapper).upsertIfNewer(eq(DEVICE_ID), eq("temperature"), eq("double"), eq("25.5"), eq(RECEIVED_AT));
    }

    private ThingModelDefinition modelWithTemperature() {
        return new ThingModelDefinition(1, Map.of(
                "temperature", new ThingModelDefinition.PropertySpec("temperature", "double", null, null, false, Set.of(), null, "r")),
                Map.of(), Map.of());
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey("dev-1");
        return device;
    }

    private ResolvedEvent dataEvent(String payload) {
        return new ResolvedEvent(device(),
                new IngestRecord("dev-1", "mqtt/data", "data", payload, 0, RECEIVED_AT));
    }

    private double count(String result) {
        Counter counter = registry.find(METRIC).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }
}