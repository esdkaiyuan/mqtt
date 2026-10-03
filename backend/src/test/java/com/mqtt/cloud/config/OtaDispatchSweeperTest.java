package com.mqtt.cloud.config;

import com.mqtt.cloud.service.OtaUpgradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OTA 升级巡检注册单测（T-22 实施计划 P6）。
 * <p>
 * 覆盖：总开关关闭 / 间隔非正时不注册；启用时按间隔注册固定延迟任务；单轮巡检先后调用
 * 补投与超时扫描；任一步骤抛异常都被吞掉（只记 WARN），绝不冒泡到调度线程。
 */
class OtaDispatchSweeperTest {

    private OtaUpgradeService otaUpgradeService;
    private OtaProperties properties;
    private OtaDispatchSweeper sweeper;
    private ScheduledTaskRegistrar registrar;

    @BeforeEach
    void setUp() {
        otaUpgradeService = mock(OtaUpgradeService.class);
        properties = new OtaProperties();
        sweeper = new OtaDispatchSweeper(otaUpgradeService, properties);
        registrar = mock(ScheduledTaskRegistrar.class);
    }

    @Test
    void configureTasks_should_skip_when_disabled() {
        properties.setEnabled(false);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
        verify(otaUpgradeService, never()).sweepPending(anyInt());
    }

    @Test
    void configureTasks_should_skip_when_interval_not_positive() {
        properties.setDispatchIntervalMs(0);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void configureTasks_should_register_and_sweep_both_steps() {
        properties.setDispatchIntervalMs(60000L);
        properties.setDispatchBatchSize(200);
        properties.setTaskTimeoutMs(86400000L);
        when(otaUpgradeService.sweepPending(200)).thenReturn(3);
        when(otaUpgradeService.sweepTimeout(86400000L)).thenReturn(1);

        Runnable sweep = capturedSweep();
        sweep.run();

        verify(otaUpgradeService).sweepPending(200);
        verify(otaUpgradeService).sweepTimeout(86400000L);
    }

    @Test
    void sweep_should_isolate_pending_failure() {
        when(otaUpgradeService.sweepPending(anyInt())).thenThrow(new RuntimeException("db down"));

        Runnable sweep = capturedSweep();

        assertThatCode(sweep::run).doesNotThrowAnyException();
        verify(otaUpgradeService, never()).sweepTimeout(anyLong());
    }

    @Test
    void sweep_should_isolate_timeout_failure() {
        when(otaUpgradeService.sweepPending(anyInt())).thenReturn(0);
        when(otaUpgradeService.sweepTimeout(anyLong())).thenThrow(new RuntimeException("db down"));

        Runnable sweep = capturedSweep();

        assertThatCode(sweep::run).doesNotThrowAnyException();
        verify(otaUpgradeService).sweepPending(properties.getDispatchBatchSize());
    }

    private Runnable capturedSweep() {
        sweeper.configureTasks(registrar);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(registrar).addFixedDelayTask(captor.capture(), eq(Duration.ofMillis(properties.getDispatchIntervalMs())));
        return captor.getValue();
    }
}