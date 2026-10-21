package com.mqtt.cloud.config;

import com.mqtt.cloud.service.ModbusCollectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Modbus 采集巡检注册单测（T-31 P4）。
 */
class ModbusCollectSweeperTest {

    private ModbusCollectService service;
    private ModbusCollectProperties properties;
    private ModbusCollectSweeper sweeper;
    private ScheduledTaskRegistrar registrar;

    @BeforeEach
    void setUp() {
        service = mock(ModbusCollectService.class);
        properties = new ModbusCollectProperties();
        sweeper = new ModbusCollectSweeper(service, properties);
        registrar = mock(ScheduledTaskRegistrar.class);
    }

    @Test
    void configure_tasks_should_skip_when_disabled() {
        properties.setEnabled(false);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
        verify(service, never()).sweepDuePoints();
    }

    @Test
    void configure_tasks_should_skip_when_interval_not_positive() {
        properties.setEnabled(true);
        properties.setSweepIntervalMs(0);

        sweeper.configureTasks(registrar);

        verify(registrar, never()).addFixedDelayTask(any(Runnable.class), any(Duration.class));
    }

    @Test
    void enabled_sweeper_should_register_and_call_service() {
        properties.setEnabled(true);
        properties.setSweepIntervalMs(1000);
        when(service.sweepDuePoints()).thenReturn(2);

        Runnable sweep = capturedSweep();
        sweep.run();

        verify(service).sweepDuePoints();
    }

    @Test
    void sweep_should_swallow_service_exception() {
        properties.setEnabled(true);
        properties.setSweepIntervalMs(1000);
        when(service.sweepDuePoints()).thenThrow(new RuntimeException("db down"));

        Runnable sweep = capturedSweep();

        assertThatCode(sweep::run).doesNotThrowAnyException();
    }

    private Runnable capturedSweep() {
        sweeper.configureTasks(registrar);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(registrar).addFixedDelayTask(captor.capture(),
                eq(Duration.ofMillis(properties.getSweepIntervalMs())));
        return captor.getValue();
    }
}
