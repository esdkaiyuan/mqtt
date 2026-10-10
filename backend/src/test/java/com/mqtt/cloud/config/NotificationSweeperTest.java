package com.mqtt.cloud.config;

import com.mqtt.cloud.service.NotificationSweeperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 通知投递巡检注册单测（T-27 实施计划 P3）。
 * <p>
 * 覆盖：总开关关闭 / 间隔非正时不注册；启用时按间隔注册固定延迟任务并执行巡检；
 * 巡检异常被吞掉（只记 WARN），绝不冒泡到调度线程。
 */
class NotificationSweeperTest {

    private NotificationSweeperService sweeperService;
    private NotificationProperties properties;
    private NotificationSweeper sweeper;
    private ScheduledTaskRegistrar registrar;

    @BeforeEach
    void setUp() {
        sweeperService = mock(NotificationSweeperService.class);
        properties = new NotificationProperties();
        sweeper = new NotificationSweeper(sweeperService, properties);
        registrar = mock(ScheduledTaskRegistrar.class);
    }

    @Test
    void configureTasks_should_skip_when_disabled() {
        properties.setEnabled(false);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
        verify(sweeperService, never()).sweep();
    }

    @Test
    void configureTasks_should_skip_when_interval_not_positive() {
        properties.getDelivery().setSweepIntervalMs(0L);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void configureTasks_should_register_and_sweep() {
        properties.getDelivery().setSweepIntervalMs(60000L);

        Runnable sweep = capturedSweep();
        sweep.run();

        verify(sweeperService).sweep();
    }

    @Test
    void sweep_should_isolate_failure() {
        properties.getDelivery().setSweepIntervalMs(60000L);
        doThrow(new RuntimeException("db down")).when(sweeperService).sweep();

        Runnable sweep = capturedSweep();

        assertThatCode(sweep::run).doesNotThrowAnyException();
    }

    private Runnable capturedSweep() {
        sweeper.configureTasks(registrar);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(registrar).addFixedDelayTask(captor.capture(),
                eq(Duration.ofMillis(properties.getDelivery().getSweepIntervalMs())));
        return captor.getValue();
    }
}