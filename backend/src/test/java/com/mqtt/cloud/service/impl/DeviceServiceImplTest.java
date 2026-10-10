package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.CreateSubDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import com.mqtt.cloud.service.DeviceTopologyService;
import com.mqtt.cloud.service.EmqxClientKicker;
import com.mqtt.cloud.service.ProductService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    private static final Long GATEWAY_ID = 100L;
    private static final Long SUB_ID = 200L;

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
    @Mock
    private DeviceAuthCacheService authCacheService;
    @Mock
    private EmqxClientKicker emqxClientKicker;
    @Mock
    private DeviceTopologyService deviceTopologyService;

    private DeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 需要实体的 TableInfo；纯单测无 MyBatis 上下文，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
        service = new DeviceServiceImpl(deviceStatusHistoryService, deviceCredentialService,
                deviceSecretService, productService, authCacheService, emqxClientKicker,
                deviceTopologyService);
        ReflectionTestUtils.setField(service, "baseMapper", deviceMapper);
    }

    @Test
    void deleteDevice_should_evict_auth_cache() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        Product product = new Product();
        product.setId(10L);
        product.setProductKey("esp32-fall");
        when(productService.requireById(10L)).thenReturn(product);

        service.deleteDevice(1L);

        verify(authCacheService).evict("esp32-fall", "sensor-01");
    }

    @Test
    void disableDevice_should_set_enabled_and_evict_auth_cache() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        device.setEnabled(1);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        Product product = new Product();
        product.setId(10L);
        product.setProductKey("esp32-fall");
        when(productService.requireById(10L)).thenReturn(product);
        when(deviceSecretService.buildUsername("esp32-fall", "sensor-01")).thenReturn("esp32-fall.sensor-01");

        service.disableDevice(1L);

        verify(deviceMapper).update(any(), any());
        verify(authCacheService).evict("esp32-fall", "sensor-01");
        verify(emqxClientKicker).kickByUsername("esp32-fall.sensor-01");
    }

    @Test
    void disableDevice_should_be_idempotent_when_already_disabled() {
        Device device = new Device();
        device.setId(1L);
        device.setEnabled(0);
        when(deviceMapper.selectById(1L)).thenReturn(device);

        service.disableDevice(1L);

        verify(deviceMapper, never()).update(any(), any());
        verifyNoInteractions(authCacheService);
        verifyNoInteractions(emqxClientKicker);
    }

    @Test
    void enableDevice_should_set_enabled_and_evict_auth_cache() {
        Device device = new Device();
        device.setId(1L);
        device.setDeviceKey("sensor-01");
        device.setProductId(10L);
        device.setEnabled(0);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        Product product = new Product();
        product.setId(10L);
        product.setProductKey("esp32-fall");
        when(productService.requireById(10L)).thenReturn(product);

        service.enableDevice(1L);

        verify(deviceMapper).update(any(), any());
        verify(authCacheService).evict("esp32-fall", "sensor-01");
        verifyNoInteractions(emqxClientKicker);
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

    @Test
    void createSubDevice_should_inherit_gateway_owner_and_attach() {
        Device gateway = new Device();
        gateway.setId(GATEWAY_ID);
        gateway.setProductId(10L);
        gateway.setOwnerId(7L);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        Product product = new Product();
        product.setId(10L);
        product.setProductKey("esp32-fall");
        when(productService.requireById(10L)).thenReturn(product);
        when(deviceMapper.selectCount(any())).thenReturn(0L);
        // 纯单测无自增主键回填，插入时手动赋 id 供后续挂载与签发使用
        when(deviceMapper.insert(any(Device.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Device.class).setId(SUB_ID);
            return 1;
        });
        when(deviceCredentialService.issueSecret(SUB_ID)).thenReturn("plain-secret");
        when(deviceSecretService.buildUsername("esp32-fall", "sub-01")).thenReturn("esp32-fall.sub-01");

        CreateSubDeviceDTO dto = new CreateSubDeviceDTO();
        dto.setDeviceName("子设备");
        dto.setDeviceKey("sub-01");
        dto.setDeviceType("sensor");
        dto.setTopic("sensor/sub-01/data");

        DeviceCreatedDTO created = service.createSubDevice(GATEWAY_ID, dto);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).insert(captor.capture());
        // 归属取自网关，防止子设备落到发起人账号下
        assertThat(captor.getValue().getOwnerId()).isEqualTo(7L);
        assertThat(captor.getValue().getProductId()).isEqualTo(10L);
        assertThat(created.getId()).isEqualTo(SUB_ID);
        assertThat(created.getDeviceSecret()).isEqualTo("plain-secret");
        verify(deviceTopologyService).attach(GATEWAY_ID, SUB_ID);
    }

    @Test
    void createSubDevice_should_reject_missing_gateway() {
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(null);
        CreateSubDeviceDTO dto = new CreateSubDeviceDTO();
        dto.setDeviceKey("sub-01");

        assertThatThrownBy(() -> service.createSubDevice(GATEWAY_ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_PARENT_NOT_FOUND.getCode());

        verify(deviceMapper, never()).insert(any(Device.class));
        verifyNoInteractions(deviceTopologyService);
    }

    @Test
    void deleteDevice_should_reject_when_has_subdevices() {
        Device gateway = new Device();
        gateway.setId(1L);
        gateway.setDeviceKey("gw-01");
        gateway.setProductId(10L);
        when(deviceMapper.selectById(1L)).thenReturn(gateway);
        when(deviceMapper.countByParentId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteDevice(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_HAS_SUBDEVICES.getCode());

        verify(deviceMapper, never()).update(any(), any());
        verifyNoInteractions(authCacheService);
    }

    private Device device(String status) {
        Device device = new Device();
        device.setId(1L);
        device.setStatus(status);
        return device;
    }
}