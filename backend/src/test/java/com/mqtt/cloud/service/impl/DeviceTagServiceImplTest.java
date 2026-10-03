package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.DeviceTagRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceTag;
import com.mqtt.cloud.mapper.DeviceTagMapper;
import com.mqtt.cloud.mapper.DeviceTagRelationMapper;
import com.mqtt.cloud.service.DeviceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备标签服务单测（T-18 实施计划 P5）。
 * <p>
 * 覆盖创建 / 更新（重名、非法颜色统一 {@code 6215}）、删除自动解除关联、归属越权（{@code 403}）、
 * 打标设备归属校验（{@code 2002} / {@code 2003}）。
 */
@ExtendWith(MockitoExtension.class)
class DeviceTagServiceImplTest {

    private static final Long USER_ID = 10L;

    @Mock
    private DeviceTagMapper tagMapper;

    @Mock
    private DeviceTagRelationMapper tagRelationMapper;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private DeviceTagServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeviceTag.class);
        ReflectionTestUtils.setField(service, "baseMapper", tagMapper);
    }

    @Test
    void create_should_save_tag() {
        when(tagMapper.countByUserAndName(anyLong(), anyString(), any())).thenReturn(0);

        DeviceTag created = service.create(USER_ID, request("重点设备", "#409EFF"));

        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getName()).isEqualTo("重点设备");
        assertThat(created.getColor()).isEqualTo("#409EFF");
        verify(tagMapper).insert(any(DeviceTag.class));
    }

    @Test
    void create_should_reject_duplicate_name() {
        when(tagMapper.countByUserAndName(anyLong(), anyString(), any())).thenReturn(1);

        assertCode(() -> service.create(USER_ID, request("重点设备", null)), ResultCode.DEVICE_TAG_INVALID);
    }

    @Test
    void create_should_reject_invalid_color() {
        assertCode(() -> service.create(USER_ID, request("重点设备", "red")), ResultCode.DEVICE_TAG_INVALID);
    }

    @Test
    void create_should_reject_blank_name() {
        assertCode(() -> service.create(USER_ID, request("  ", null)), ResultCode.DEVICE_TAG_INVALID);
    }

    @Test
    void create_should_allow_null_color() {
        when(tagMapper.countByUserAndName(anyLong(), anyString(), any())).thenReturn(0);

        DeviceTag created = service.create(USER_ID, request("无颜色", null));

        assertThat(created.getColor()).isNull();
    }

    @Test
    void update_should_reject_duplicate_name() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L));
        when(tagMapper.countByUserAndName(anyLong(), anyString(), any())).thenReturn(1);

        assertCode(() -> service.update(USER_ID, 5L, request("重点设备", null)), ResultCode.DEVICE_TAG_INVALID);
        verify(tagMapper, never()).updateById(any(DeviceTag.class));
    }

    @Test
    void update_should_apply_new_values() {
        DeviceTag tag = tag(5L);
        when(tagMapper.selectById(5L)).thenReturn(tag);
        when(tagMapper.countByUserAndName(anyLong(), anyString(), any())).thenReturn(0);

        DeviceTag updated = service.update(USER_ID, 5L, request("关键设备", "#67C23A"));

        assertThat(updated.getName()).isEqualTo("关键设备");
        assertThat(updated.getColor()).isEqualTo("#67C23A");
        verify(tagMapper).updateById(tag);
    }

    @Test
    void delete_should_detach_devices_then_remove() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L));

        service.delete(USER_ID, 5L);

        verify(tagRelationMapper).deleteByTagId(5L);
        verify(tagMapper).deleteById(5L);
    }

    @Test
    void getOwned_should_throw_when_missing() {
        when(tagMapper.selectById(5L)).thenReturn(null);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.DEVICE_TAG_NOT_FOUND);
    }

    @Test
    void getOwned_should_throw_when_not_owned() {
        DeviceTag tag = tag(5L);
        tag.setUserId(999L);
        when(tagMapper.selectById(5L)).thenReturn(tag);

        assertCode(() -> service.getOwned(USER_ID, 5L), ResultCode.FORBIDDEN);
    }

    @Test
    void addDevices_should_insert_ignore_after_ownership_check() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(100L, USER_ID)));

        service.addDevices(USER_ID, 5L, List.of(100L));

        verify(tagRelationMapper).insertIgnoreBatch(any(), eq(5L), any());
    }

    @Test
    void addDevices_should_reject_non_owned_device() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L));
        when(deviceService.listByIds(any())).thenReturn(List.of(device(100L, 999L)));

        assertCode(() -> service.addDevices(USER_ID, 5L, List.of(100L)), ResultCode.DEVICE_NOT_OWNED);
    }

    @Test
    void addDevices_should_reject_missing_device() {
        when(tagMapper.selectById(5L)).thenReturn(tag(5L));
        when(deviceService.listByIds(any())).thenReturn(List.of());

        assertCode(() -> service.addDevices(USER_ID, 5L, List.of(100L)), ResultCode.DEVICE_NOT_FOUND);
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private DeviceTagRequest request(String name, String color) {
        DeviceTagRequest request = new DeviceTagRequest();
        request.setName(name);
        request.setColor(color);
        return request;
    }

    private DeviceTag tag(Long id) {
        DeviceTag tag = new DeviceTag();
        tag.setId(id);
        tag.setUserId(USER_ID);
        tag.setName("tag-" + id);
        return tag;
    }

    private Device device(Long id, Long ownerId) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        return device;
    }
}