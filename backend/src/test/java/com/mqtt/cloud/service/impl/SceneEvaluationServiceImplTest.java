package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import com.mqtt.cloud.service.SceneEvaluationService.EventSample;
import com.mqtt.cloud.service.SceneEvaluationService.PropertySample;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 场景触发评估单测（T-23 设计文档 §14.1 / §8.2 / §8.3 / §8.4）。
 * <p>
 * 使用真实 {@link SceneMatcher} 与 {@link SceneCooldownRegistry}（条件组口径与冷却窗口为其职责），
 * 仅 mock 落库 Mapper 与 {@link SceneExecutionLauncher}；覆盖触发源匹配（{@code PROPERTY} /
 * {@code EVENT} / 作用域 / 事件类型）、触发条件判定（数值阈值与不可解析）、条件组 {@code AND} / {@code OR}、
 * 条件值缺失 → 不满足、冷却窗口、缓存 TTL，以及主链路异常隔离与落库失败不冒泡。
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneEvaluationServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long DEVICE_ID = 100L;
    private static final Long OTHER_DEVICE_ID = 200L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long SCENE_ID = 5L;
    private static final Long EXECUTION_ID = 900L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    private SceneDefinitionMapper sceneDefinitionMapper;
    private DeviceMapper deviceMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private SceneProperties properties;
    private SceneMatcher sceneMatcher;
    private SceneCooldownRegistry cooldownRegistry;
    private SceneExecutionLauncher launcher;
    private SceneEvaluationServiceImpl service;

    @BeforeEach
    void setUp() {
        sceneDefinitionMapper = mock(SceneDefinitionMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        properties = new SceneProperties();
        // 默认关闭缓存，使各用例的场景桩可独立生效；缓存行为由专门用例覆盖
        properties.setCacheTtlSeconds(0);
        sceneMatcher = new SceneMatcher(propertyLatestMapper, properties, new ObjectMapper());
        cooldownRegistry = new SceneCooldownRegistry();
        launcher = mock(SceneExecutionLauncher.class);
        when(launcher.launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString()))
                .thenReturn(EXECUTION_ID);
        service = new SceneEvaluationServiceImpl(sceneDefinitionMapper, deviceMapper, sceneMatcher,
                cooldownRegistry, launcher, properties, new SimpleMeterRegistry());
    }

    // ---------- 属性触发 ----------

    @Test
    void onProperties_should_trigger_when_property_matches_threshold() {
        SceneDefinition scene = propertyTrigger();
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        ArgumentCaptor<SceneExecutionLauncher.TriggerContext> captor =
                ArgumentCaptor.forClass(SceneExecutionLauncher.TriggerContext.class);
        verify(launcher).launch(eq(scene), captor.capture(), eq(SceneConstants.SOURCE_AUTO));
        assertThat(captor.getValue().triggerDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(captor.getValue().triggerDeviceKey()).isEqualTo("dev-1");
        assertThat(captor.getValue().triggerIdentifier()).isEqualTo("temperature");
        assertThat(captor.getValue().triggerValue()).isEqualTo("41");
        assertThat(captor.getValue().triggerEventType()).isNull();
    }

    @Test
    void onProperties_should_skip_when_value_below_threshold() {
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "30", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_skip_when_value_unparseable() {
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "hot", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_trigger_any_report_when_operator_absent() {
        SceneDefinition scene = propertyTrigger();
        scene.setTriggerOperator(null);
        scene.setTriggerThreshold(null);
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "0", NOW)));

        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void onProperties_should_skip_when_identifier_mismatch() {
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("humidity", "99", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_skip_when_trigger_source_is_event() {
        stubDeviceAndScenes(eventTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_skip_when_scene_out_of_scope() {
        SceneDefinition scene = propertyTrigger();
        scene.setTriggerDeviceId(OTHER_DEVICE_ID);
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_match_when_scope_absent() {
        SceneDefinition scene = propertyTrigger();
        scene.setTriggerDeviceId(null);
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void onProperties_should_skip_when_disabled() {
        properties.setEnabled(false);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verifyNoInteractions(deviceMapper, sceneDefinitionMapper, launcher);
    }

    @Test
    void onProperties_should_skip_when_device_unknown() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verifyNoInteractions(sceneDefinitionMapper, launcher);
    }

    // ---------- 事件触发 ----------

    @Test
    void onEvents_should_trigger_when_identifier_and_type_match() {
        SceneDefinition scene = eventTrigger();
        stubDeviceAndScenes(scene);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "alert", "{\"level\":3}", NOW)));

        ArgumentCaptor<SceneExecutionLauncher.TriggerContext> captor =
                ArgumentCaptor.forClass(SceneExecutionLauncher.TriggerContext.class);
        verify(launcher).launch(eq(scene), captor.capture(), eq(SceneConstants.SOURCE_AUTO));
        assertThat(captor.getValue().triggerIdentifier()).isEqualTo("fall");
        assertThat(captor.getValue().triggerEventType()).isEqualTo("alert");
        assertThat(captor.getValue().triggerValue()).isEqualTo("{\"level\":3}");
    }

    @Test
    void onEvents_should_skip_when_event_type_mismatch() {
        stubDeviceAndScenes(eventTrigger());

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "info", "{}", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onEvents_should_match_any_type_when_scene_type_absent() {
        SceneDefinition scene = eventTrigger();
        scene.setTriggerEventType(null);
        stubDeviceAndScenes(scene);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "info", "{}", NOW)));

        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void onEvents_should_skip_when_scene_out_of_scope() {
        SceneDefinition scene = eventTrigger();
        scene.setTriggerDeviceId(OTHER_DEVICE_ID);
        stubDeviceAndScenes(scene);

        service.onEvents(DEVICE_ID, List.of(new EventSample("fall", "alert", "{}", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    // ---------- 条件组 ----------

    @Test
    void onProperties_should_skip_when_and_condition_partly_false() {
        SceneDefinition scene = propertyTrigger();
        scene.setConditionConfig(conditionsJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        stubDeviceAndScenes(scene);
        // 仅回填 temperature=41（满足），humidity 缺失 → 不满足，AND 组整体不满足
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any()))
                .thenReturn(List.of(latest("temperature", "41")));

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_trigger_when_or_condition_partly_true() {
        SceneDefinition scene = propertyTrigger();
        scene.setConditionConfig(conditionsJson());
        scene.setConditionLogic(SceneConstants.LOGIC_OR);
        stubDeviceAndScenes(scene);
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any()))
                .thenReturn(List.of(latest("temperature", "41")));

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void onProperties_should_skip_when_and_condition_value_missing() {
        SceneDefinition scene = propertyTrigger();
        scene.setConditionConfig(conditionsJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        stubDeviceAndScenes(scene);
        // 属性最新值全部缺失 → 每项均不满足 → AND 组不满足
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any())).thenReturn(List.of());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void onProperties_should_trigger_when_condition_group_empty() {
        SceneDefinition scene = propertyTrigger();
        scene.setConditionConfig(null);
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    // ---------- 冷却窗口 ----------

    @Test
    void cooldown_should_suppress_repeat_within_window() {
        SceneDefinition scene = propertyTrigger();
        scene.setCooldownSeconds(60);
        stubDeviceAndScenes(scene);

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "42", NOW)));

        verify(launcher, times(1)).launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString());
    }

    @Test
    void cooldown_zero_should_allow_repeat() {
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "42", NOW)));

        verify(launcher, times(2)).launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString());
    }

    // ---------- 缓存 TTL ----------

    @Test
    void cache_should_reuse_scenes_within_ttl() {
        properties.setCacheTtlSeconds(60);
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(sceneDefinitionMapper, times(1)).selectEnabledByUser(USER_ID);
    }

    @Test
    void evictCache_should_force_reload() {
        properties.setCacheTtlSeconds(60);
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.evictCache(USER_ID);
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(sceneDefinitionMapper, times(2)).selectEnabledByUser(USER_ID);
    }

    @Test
    void ttl_zero_should_disable_cache() {
        properties.setCacheTtlSeconds(0);
        stubDeviceAndScenes(propertyTrigger());

        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));
        service.onProperties(DEVICE_ID, PRODUCT_ID, List.of(new PropertySample("temperature", "41", NOW)));

        verify(sceneDefinitionMapper, times(2)).selectEnabledByUser(USER_ID);
    }

    @Test
    void evictCache_should_ignore_null_user() {
        assertThatCode(() -> service.evictCache(null)).doesNotThrowAnyException();
    }

    // ---------- 主链路隔离 ----------

    @Test
    void should_not_propagate_when_scene_load_fails() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
        when(sceneDefinitionMapper.selectEnabledByUser(USER_ID)).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "41", NOW)))).doesNotThrowAnyException();
    }

    @Test
    void should_not_propagate_when_launch_fails() {
        stubDeviceAndScenes(propertyTrigger());
        when(launcher.launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "41", NOW)))).doesNotThrowAnyException();
    }

    @Test
    void should_not_propagate_when_condition_query_fails() {
        SceneDefinition scene = propertyTrigger();
        scene.setConditionConfig(conditionsJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        stubDeviceAndScenes(scene);
        when(propertyLatestMapper.selectByIdentifiers(eq(DEVICE_ID), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.onProperties(DEVICE_ID, PRODUCT_ID,
                List.of(new PropertySample("temperature", "41", NOW)))).doesNotThrowAnyException();
    }

    // ---------- 辅助 ----------

    private void stubDeviceAndScenes(SceneDefinition... scenes) {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
        when(sceneDefinitionMapper.selectEnabledByUser(USER_ID)).thenReturn(List.of(scenes));
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey("dev-1");
        device.setDeviceName("设备1");
        device.setOwnerId(USER_ID);
        return device;
    }

    private SceneDefinition propertyTrigger() {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        scene.setUserId(USER_ID);
        scene.setName("高温联动");
        scene.setTriggerType(SceneConstants.TRIGGER_PROPERTY);
        scene.setTriggerIdentifier("temperature");
        scene.setTriggerOperator(RuleConstants.OPERATOR_GT);
        scene.setTriggerThreshold("40");
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        return scene;
    }

    private SceneDefinition eventTrigger() {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(SCENE_ID);
        scene.setUserId(USER_ID);
        scene.setName("跌倒联动");
        scene.setTriggerType(SceneConstants.TRIGGER_EVENT);
        scene.setTriggerIdentifier("fall");
        scene.setTriggerEventType("alert");
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        return scene;
    }

    private static String conditionsJson() {
        return "[{\"identifier\":\"temperature\",\"operator\":\"GT\",\"threshold\":\"40\"},"
                + "{\"identifier\":\"humidity\",\"operator\":\"GT\",\"threshold\":\"50\"}]";
    }

    private static DevicePropertyLatest latest(String identifier, String valueText) {
        DevicePropertyLatest row = new DevicePropertyLatest();
        row.setDeviceId(DEVICE_ID);
        row.setIdentifier(identifier);
        row.setValueText(valueText);
        return row;
    }
}
