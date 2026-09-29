package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import com.mqtt.cloud.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private DeviceStatusHistoryService deviceStatusHistoryService;
    @Mock
    private DeviceCredentialService deviceCredentialService;
    @Mock
    private DeviceSecretService deviceSecretService;
    @Mock
    private ProductService productService;
    @Mock
    private DeviceMapper deviceMapper;

    private DeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceServiceImpl(deviceStatusHistoryService, deviceCredentialService,
                deviceSecretService, productService);
        ReflectionTestUtils.setField(service, "baseMapper", deviceMapper);
    }

    @Test
    void updateDeviceStatus_should_drop_stale_event_and_skip_history() {
        Device device = device(DeviceStatusValue.ONLINE);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        // 守卫返回 0：库内 last_seen 已晚于本次事件时间，属乱序旧事件
        when(deviceMapper.updateStatusGuarded(eq(1L), anyString(), any())).thenReturn(0);

        service.updateDeviceStatus(1L, DeviceStatusValue.OFFLINE, LocalDateTime.now());

        verify(deviceStatusHistoryService, never()).recordStatusChange(any(), anyString());
    }

    @Test
    void updateDeviceStatus_should_record_history_when_status_really_changes() {
        Device device = device(DeviceStatusValue.OFFLINE);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(deviceMapper.updateStatusGuarded(eq(1L), anyString(), any())).thenReturn(1);

        service.updateDeviceStatus(1L, DeviceStatusValue.ONLINE, LocalDateTime.now());

        verify(deviceStatusHistoryService).recordStatusChange(1L, DeviceStatusValue.ONLINE);
    }

    @Test
    void updateDeviceStatus_should_not_record_history_when_status_unchanged() {
        Device device = device(DeviceStatusValue.ONLINE);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(deviceMapper.updateStatusGuarded(eq(1L), anyString(), any())).thenReturn(1);

        service.updateDeviceStatus(1L, DeviceStatusValue.ONLINE, LocalDateTime.now());

        verify(deviceStatusHistoryService, never()).recordStatusChange(any(), anyString());
    }

    @Test
    void updateDeviceStatus_should_noop_when_device_missing() {
        when(deviceMapper.selectById(404L)).thenReturn(null);

        service.updateDeviceStatus(404L, DeviceStatusValue.ONLINE, LocalDateTime.now());

        verifyNoInteractions(deviceStatusHistoryService);
    }

    private Device device(String status) {
        Device device = new Device();
        device.setId(1L);
        device.setStatus(status);
        return device;
    }
}