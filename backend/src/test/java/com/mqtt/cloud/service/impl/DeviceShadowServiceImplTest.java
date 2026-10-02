package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.config.ShadowProperties;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.DeviceShadow;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.DeviceShadowMapper;
import com.mqtt.cloud.service.DeviceShadowService.DeviceShadowResponse;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备影子服务单测（T-16 实施计划）。
 * <p>
 * 覆盖：懒创建与读取、{@code modeled} 判定、{@code applyDesired} 合并与 delta 重算、
 * {@code applyReported} 回读权威值合并、CAS 冲突重试 / 耗尽放弃 / 影子行不可用放弃。
 * <p>
 * 写路径统一走「读-改-写 + 版本 CAS」，故断言集中在 {@code shadowMapper.casUpdate} 的入参上。
 */
class DeviceShadowServiceImplTest {

    private static final Long DEVICE_ID = 42L;
    private static final Long PRODUCT_ID = 7L;

    private DeviceShadowMapper shadowMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private ThingModelService thingModelService;
    private ShadowProperties properties;
    private DeviceShadowServiceImpl service;

    @BeforeEach
    void setUp() {
        shadowMapper = mock(DeviceShadowMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        thingModelService = mock(ThingModelService.class);
        properties = new ShadowProperties();
        service = new DeviceShadowServiceImpl(shadowMapper, propertyLatestMapper,
                thingModelService, properties, new ObjectMapper());
    }

    private DeviceShadow shadow(String desired, String reported, Long version) {
        DeviceShadow row = new DeviceShadow();
        row.setDeviceId(DEVICE_ID);
        row.setDesired(desired);
        row.setReported(reported);
        row.setDelta(null);
        row.setVersion(version);
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private ThingModelDefinition modeled() {
        ThingModelDefinition.PropertySpec spec = new ThingModelDefinition.PropertySpec(
                "power", "bool", null, null, false, Set.of(), null, "rw");
        return new ThingModelDefinition(1, Map.of("power", spec), Map.of(), Map.of());
    }

    // ---------- get ----------

    @Test
    void get_should_return_empty_when_deviceId_is_null() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modeled());

        DeviceShadowResponse response = service.get(null, PRODUCT_ID);

        assertThat(response.modeled()).isTrue();
        assertThat(response.version()).isZero();
        assertThat(response.desired()).isEmpty();
        assertThat(response.reported()).isEmpty();
        assertThat(response.delta()).isEmpty();
        assertThat(response.updatedAt()).isNull();
        verifyNoInteractions(shadowMapper);
    }

    @Test
    void get_should_lazy_create_and_project_stored_state() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(ThingModelDefinition.EMPTY);
        when(shadowMapper.selectOne(any()))
                .thenReturn(shadow("{\"power\":\"true\"}", "{\"power\":\"false\"}", 3L));

        DeviceShadowResponse response = service.get(DEVICE_ID, PRODUCT_ID);

        verify(shadowMapper).insertIfAbsent(eq(DEVICE_ID), any(LocalDateTime.class));
        assertThat(response.modeled()).isFalse();
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.desired()).containsEntry("power", "true");
        assertThat(response.reported()).containsEntry("power", "false");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void get_should_fall_back_to_empty_when_row_unavailable() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(ThingModelDefinition.EMPTY);
        when(shadowMapper.selectOne(any())).thenReturn(null);

        DeviceShadowResponse response = service.get(DEVICE_ID, PRODUCT_ID);

        assertThat(response.version()).isZero();
        assertThat(response.desired()).isEmpty();
    }

    // ---------- applyDesired ----------

    @Test
    void applyDesired_should_push_delta_when_reported_missing() {
        when(shadowMapper.selectOne(any())).thenReturn(shadow("{}", "{}", 0L));
        when(shadowMapper.casUpdate(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        service.applyDesired(DEVICE_ID, Map.of("power", "true"));

        ArgumentCaptor<String> desired = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> delta = ArgumentCaptor.forClass(String.class);
        verify(shadowMapper).casUpdate(eq(DEVICE_ID), desired.capture(), anyString(),
                delta.capture(), eq(0L), any(LocalDateTime.class));
        assertThat(desired.getValue()).contains("\"power\":\"true\"");
        assertThat(delta.getValue()).contains("\"power\":\"true\"");
    }

    @Test
    void applyDesired_should_clear_delta_when_reported_already_converged() {
        when(shadowMapper.selectOne(any())).thenReturn(shadow("{}", "{\"power\":\"true\"}", 2L));
        when(shadowMapper.casUpdate(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        service.applyDesired(DEVICE_ID, Map.of("power", "true"));

        ArgumentCaptor<String> delta = ArgumentCaptor.forClass(String.class);
        verify(shadowMapper).casUpdate(eq(DEVICE_ID), anyString(), anyString(),
                delta.capture(), eq(2L), any(LocalDateTime.class));
        assertThat(delta.getValue()).isEqualTo("{}");
    }

    @Test
    void applyDesired_should_ignore_empty_input() {
        service.applyDesired(DEVICE_ID, Map.of());
        service.applyDesired(DEVICE_ID, null);

        verifyNoInteractions(shadowMapper);
    }

    // ---------- applyReported ----------

    @Test
    void applyReported_should_merge_authoritative_values() {
        when(shadowMapper.selectOne(any())).thenReturn(shadow("{}", "{}", 0L));
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any()))
                .thenReturn(List.of(latest("temperature", "25")));
        when(shadowMapper.casUpdate(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        service.applyReported(DEVICE_ID, List.of("temperature"));

        ArgumentCaptor<String> reported = ArgumentCaptor.forClass(String.class);
        verify(shadowMapper).casUpdate(eq(DEVICE_ID), anyString(), reported.capture(),
                anyString(), eq(0L), any(LocalDateTime.class));
        assertThat(reported.getValue()).contains("\"temperature\":\"25\"");
    }

    @Test
    void applyReported_should_ignore_when_no_authoritative_row() {
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any())).thenReturn(List.of());

        service.applyReported(DEVICE_ID, List.of("temperature"));

        verify(shadowMapper, never()).casUpdate(any(), anyString(), anyString(), anyString(), any(), any());
    }

    // ---------- merge / CAS ----------

    @Test
    void merge_should_retry_on_cas_conflict_then_succeed() {
        when(shadowMapper.selectOne(any())).thenReturn(shadow("{}", "{}", 5L));
        when(shadowMapper.casUpdate(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(0, 1);

        service.applyDesired(DEVICE_ID, Map.of("power", "true"));

        verify(shadowMapper, times(2))
                .casUpdate(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void merge_should_give_up_after_retries_exhausted_without_throwing() {
        properties.setCasRetry(2);
        when(shadowMapper.selectOne(any())).thenReturn(shadow("{}", "{}", 0L));
        when(shadowMapper.casUpdate(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(0);

        service.applyDesired(DEVICE_ID, Map.of("power", "true"));

        verify(shadowMapper, times(2))
                .casUpdate(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void merge_should_give_up_when_shadow_row_unavailable() {
        when(shadowMapper.selectOne(any())).thenReturn(null);

        service.applyDesired(DEVICE_ID, Map.of("power", "true"));

        verify(shadowMapper, never()).casUpdate(any(), anyString(), anyString(), anyString(), any(), any());
    }

    private DevicePropertyLatest latest(String identifier, String valueText) {
        DevicePropertyLatest row = new DevicePropertyLatest();
        row.setDeviceId(DEVICE_ID);
        row.setIdentifier(identifier);
        row.setValueText(valueText);
        return row;
    }
}
