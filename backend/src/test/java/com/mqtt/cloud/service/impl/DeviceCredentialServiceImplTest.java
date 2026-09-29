package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceCredentialResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialServiceImplTest {

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private DeviceCredentialResetService credentialResetService;

    private DeviceCredentialServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialServiceImpl(deviceMapper, credentialResetService);
    }

    @Test
    void issueSecret_should_delegate_to_reset_service() {
        when(credentialResetService.resetSecret(9L)).thenReturn("plaintext");

        assertThat(service.issueSecret(9L)).isEqualTo("plaintext");
    }

    @Test
    void exportCredentials_should_reset_each_page_in_its_own_transaction() {
        Device first = device(1L);
        Device second = device(2L);
        when(deviceMapper.selectPage(any(), any()))
                .thenReturn(pageOf(List.of(first), 501L))
                .thenReturn(pageOf(List.of(second), 501L));
        when(credentialResetService.resetPage(List.of(first)))
                .thenReturn(List.<String[]>of(new String[]{"p", "d1", "p.d1", "s1"}));
        when(credentialResetService.resetPage(List.of(second)))
                .thenReturn(List.<String[]>of(new String[]{"p", "d2", "p.d2", "s2"}));

        List<String[]> rows = service.exportCredentials();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[1]).isEqualTo("d1");
        assertThat(rows.get(1)[1]).isEqualTo("d2");
        // 每页一次独立事务调用，而不是把全部设备塞进一个长事务
        verify(credentialResetService).resetPage(List.of(first));
        verify(credentialResetService).resetPage(List.of(second));
    }

    @Test
    void exportCredentials_should_return_empty_when_no_devices() {
        when(deviceMapper.selectPage(any(), any())).thenReturn(pageOf(List.of(), 0L));

        assertThat(service.exportCredentials()).isEmpty();
    }

    private Device device(Long id) {
        Device device = new Device();
        device.setId(id);
        device.setDeviceKey("sensor-" + id);
        device.setProductId(10L);
        device.setDeleted(0);
        return device;
    }

    private Page<Device> pageOf(List<Device> records, long total) {
        Page<Device> page = new Page<>(1, 500);
        page.setRecords(records);
        page.setTotal(total);
        return page;
    }
}