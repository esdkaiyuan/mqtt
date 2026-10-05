package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.SceneConstants;
import com.mqtt.cloud.config.SceneProperties;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.SceneDefinition;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.mapper.SceneDefinitionMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * 场景定时触发巡检单测（T-23 设计文档 §14.1 / §8.4）。
 * <p>
 * 使用真实 {@link SceneMatcher} 与 {@link SceneCooldownRegistry}（cron 命中口径、条件组与冷却窗口
 * 为其职责），仅 mock {@link SceneDefinitionMapper} 与 {@link SceneExecutionLauncher}，覆盖：
 * <ul>
 *     <li>cron 命中判定：命中当前分钟 → 触发；不命中 / 空 / 非法 cron → 跳过；</li>
 *     <li>分钟去重：{@code touchLastTriggeredOnce} 返回 {@code 0}（本分钟已被触发）→ 跳过且不投递；</li>
 *     <li>时区处理：分钟锚点与触发判定同区、非法时区回退 {@code Asia/Shanghai}；</li>
 *     <li>冷却窗口、条件组取值缺失、单场景异常隔离与落库失败不冒泡。</li>
 * </ul>
 * <p>
 * 方法命名遵循 {@code x_should_y_when_z} 约定。
 */
class SceneTimerSweeperServiceImplTest {

    private static final Long SCENE_ID = 5L;
    private static final Long OTHER_SCENE_ID = 6L;
    private static final Long CONDITION_DEVICE_ID = 100L;
    private static final Long EXECUTION_ID = 900L;

    private SceneDefinitionMapper definitionMapper;
    private DevicePropertyLatestMapper propertyLatestMapper;
    private SceneProperties properties;
    private SceneMatcher sceneMatcher;
    private SceneCooldownRegistry cooldownRegistry;
    private SceneExecutionLauncher launcher;
    private SceneTimerSweeperServiceImpl service;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(SceneDefinitionMapper.class);
        propertyLatestMapper = mock(DevicePropertyLatestMapper.class);
        properties = new SceneProperties();
        sceneMatcher = new SceneMatcher(propertyLatestMapper, properties, new ObjectMapper());
        cooldownRegistry = new SceneCooldownRegistry();
        launcher = mock(SceneExecutionLauncher.class);
        when(launcher.launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString()))
                .thenReturn(EXECUTION_ID);
        service = new SceneTimerSweeperServiceImpl(definitionMapper, sceneMatcher, cooldownRegistry,
                launcher, properties, new SimpleMeterRegistry());
    }

    // ---------- 开关与空集 ----------

    @Test
    void sweepTimers_should_return_zero_when_disabled() {
        properties.setEnabled(false);

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verifyNoInteractions(definitionMapper, launcher);
    }

    @Test
    void sweepTimers_should_return_zero_when_no_enabled_timers() {
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of());

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verifyNoInteractions(launcher);
    }

    @Test
    void sweepTimers_should_return_zero_when_scene_list_null() {
        when(definitionMapper.selectEnabledTimers()).thenReturn(null);

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verifyNoInteractions(launcher);
    }

    // ---------- cron 命中判定 ----------

    @Test
    void sweepTimers_should_fire_when_cron_hits_current_minute() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        // 命中 cron 后，分钟去重返回 1 表示本轮抢到本分钟
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);

        int fired = service.sweepTimers();

        assertThat(fired).isEqualTo(1);
        verify(definitionMapper).touchLastTriggeredOnce(eq(SCENE_ID), any(), any());
        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void sweepTimers_should_pass_minute_floor_to_dedup_when_cron_hits() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);

        service.sweepTimers();

        ArgumentCaptor<LocalDateTime> floorCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(definitionMapper).touchLastTriggeredOnce(eq(SCENE_ID), any(LocalDateTime.class),
                floorCaptor.capture());
        LocalDateTime minuteFloor = floorCaptor.getValue();
        assertThat(minuteFloor.getSecond()).isZero();
        assertThat(minuteFloor.getNano()).isZero();
    }

    @Test
    void sweepTimers_should_skip_when_cron_does_not_hit_current_minute() {
        // 2 月 29 日 00:00 触发：下一个命中时刻远在当前分钟之后 → 不命中
        SceneDefinition scene = timerScene(SCENE_ID, "0 0 29 2 *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(definitionMapper, never()).touchLastTriggeredOnce(any(), any(), any());
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void sweepTimers_should_skip_when_cron_blank() {
        SceneDefinition scene = timerScene(SCENE_ID, "   ");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(definitionMapper, never()).touchLastTriggeredOnce(any(), any(), any());
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void sweepTimers_should_skip_when_cron_invalid() {
        SceneDefinition scene = timerScene(SCENE_ID, "not-a-cron");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(definitionMapper, never()).touchLastTriggeredOnce(any(), any(), any());
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    // ---------- 分钟去重 ----------

    @Test
    void sweepTimers_should_skip_when_minute_already_touched() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        // 返回 0：本分钟已被其他副本 / 其他巡检轮次触发
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(0);

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void sweepTimers_should_skip_remaining_when_dedup_rejects_one_scene() {
        SceneDefinition touched = timerScene(SCENE_ID, "* * * * *");
        SceneDefinition fresh = timerScene(OTHER_SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(touched, fresh));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(0);
        when(definitionMapper.touchLastTriggeredOnce(eq(OTHER_SCENE_ID), any(), any())).thenReturn(1);

        int fired = service.sweepTimers();

        assertThat(fired).isEqualTo(1);
        verify(launcher).launch(eq(fresh), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
        verify(launcher, never()).launch(eq(touched), any(), anyString());
    }

    // ---------- 冷却窗口 ----------

    @Test
    void sweepTimers_should_skip_when_cooldown_blocked() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        scene.setCooldownSeconds(60);
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        // 预占冷却锚点，模拟同一窗口内的重复巡检
        assertThat(cooldownRegistry.acquire(scene)).isTrue();

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    // ---------- 条件组 ----------

    @Test
    void sweepTimers_should_skip_when_condition_value_missing() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        scene.setConditionConfig(conditionJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        // 条件属性最新值缺失 → 该项不满足 → AND 组整体不满足
        when(propertyLatestMapper.selectByIdentifiers(eq(CONDITION_DEVICE_ID), any()))
                .thenReturn(List.of());

        int fired = service.sweepTimers();

        assertThat(fired).isZero();
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    @Test
    void sweepTimers_should_fire_when_condition_satisfied() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        scene.setConditionConfig(conditionJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        when(propertyLatestMapper.selectByIdentifiers(eq(CONDITION_DEVICE_ID), any()))
                .thenReturn(List.of(latest(CONDITION_DEVICE_ID, "temperature", "50")));

        int fired = service.sweepTimers();

        assertThat(fired).isEqualTo(1);
        verify(launcher).launch(eq(scene), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void sweepTimers_should_skip_and_not_propagate_when_condition_query_fails() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        scene.setConditionConfig(conditionJson());
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        when(propertyLatestMapper.selectByIdentifiers(eq(CONDITION_DEVICE_ID), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.sweepTimers()).doesNotThrowAnyException();
        verify(launcher, never()).launch(any(), any(), anyString());
    }

    // ---------- 异常隔离 ----------

    @Test
    void sweepTimers_should_isolate_single_scene_failure() {
        SceneDefinition broken = timerScene(SCENE_ID, "* * * * *");
        SceneDefinition healthy = timerScene(OTHER_SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(broken, healthy));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any()))
                .thenThrow(new RuntimeException("db down"));
        when(definitionMapper.touchLastTriggeredOnce(eq(OTHER_SCENE_ID), any(), any())).thenReturn(1);

        int fired = service.sweepTimers();

        assertThat(fired).isEqualTo(1);
        verify(launcher).launch(eq(healthy), any(SceneExecutionLauncher.TriggerContext.class),
                eq(SceneConstants.SOURCE_AUTO));
    }

    @Test
    void sweepTimers_should_not_propagate_when_launch_fails() {
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        doThrow(new RuntimeException("db down")).when(launcher).launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), anyString());

        assertThatCode(() -> service.sweepTimers()).doesNotThrowAnyException();
    }

    @Test
    void sweepTimers_should_fire_all_scenes_when_all_hit() {
        SceneDefinition first = timerScene(SCENE_ID, "* * * * *");
        SceneDefinition second = timerScene(OTHER_SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(first, second));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);
        when(definitionMapper.touchLastTriggeredOnce(eq(OTHER_SCENE_ID), any(), any())).thenReturn(1);

        int fired = service.sweepTimers();

        assertThat(fired).isEqualTo(2);
        verify(launcher, times(2)).launch(any(SceneDefinition.class),
                any(SceneExecutionLauncher.TriggerContext.class), eq(SceneConstants.SOURCE_AUTO));
    }

    // ---------- 时区处理 ----------

    @Test
    void timerZone_should_return_configured_zone() {
        properties.setTimerZone("America/New_York");

        assertThat(sceneMatcher.timerZone()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void timerZone_should_fallback_to_shanghai_when_zone_invalid() {
        properties.setTimerZone("Mars/Phobos");

        assertThat(sceneMatcher.timerZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void sweepTimers_should_keep_minute_anchor_consistent_with_trigger_zone() {
        properties.setTimerZone("America/New_York");
        SceneDefinition scene = timerScene(SCENE_ID, "* * * * *");
        when(definitionMapper.selectEnabledTimers()).thenReturn(List.of(scene));
        when(definitionMapper.touchLastTriggeredOnce(eq(SCENE_ID), any(), any())).thenReturn(1);

        service.sweepTimers();

        ArgumentCaptor<LocalDateTime> triggeredCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> floorCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(definitionMapper).touchLastTriggeredOnce(eq(SCENE_ID), triggeredCaptor.capture(),
                floorCaptor.capture());
        // 触发时刻与分钟锚点必须同区且锚点截断到整分钟，避免跨时区去重口径与触发口径漂移
        assertThat(floorCaptor.getValue()).isEqualTo(triggeredCaptor.getValue().truncatedTo(ChronoUnit.MINUTES));
    }

    // ---------- 辅助 ----------

    private SceneDefinition timerScene(Long id, String cron) {
        SceneDefinition scene = new SceneDefinition();
        scene.setId(id);
        scene.setUserId(10L);
        scene.setName("定时场景-" + id);
        scene.setTriggerType(SceneConstants.TRIGGER_TIMER);
        scene.setTimerCron(cron);
        scene.setConditionLogic(SceneConstants.LOGIC_AND);
        scene.setCooldownSeconds(0);
        scene.setEnabled(1);
        return scene;
    }

    private static String conditionJson() {
        return "[{\"deviceId\":100,\"identifier\":\"temperature\",\"operator\":\"GT\",\"threshold\":\"40\"}]";
    }

    private static DevicePropertyLatest latest(Long deviceId, String identifier, String valueText) {
        DevicePropertyLatest row = new DevicePropertyLatest();
        row.setDeviceId(deviceId);
        row.setIdentifier(identifier);
        row.setValueText(valueText);
        return row;
    }
}
