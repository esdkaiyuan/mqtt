package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.mapper.DeviceEventRecordMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备物模型数据只读查询单测（T-14 实施计划 P5）。
 * <p>
 * 聚焦分页边界：页码 / 每页非法值被夹取到安全区间，避免 {@code size} 被放大成全表扫描。
 */
@ExtendWith(MockitoExtension.class)
class DeviceDataServiceImplTest {

    @Mock
    private DevicePropertyLatestMapper propertyLatestMapper;

    @Mock
    private DeviceEventRecordMapper eventRecordMapper;

    @InjectMocks
    private DeviceDataServiceImpl service;

    @Test
    void getPropertyLatest_should_query_by_device() {
        DevicePropertyLatest property = new DevicePropertyLatest();
        when(propertyLatestMapper.selectList(any())).thenReturn(List.of(property));

        assertThat(service.getPropertyLatest(100L)).containsExactly(property);
        verify(propertyLatestMapper).selectList(any());
    }

    @Test
    void getEvents_should_clamp_page_and_size_to_safe_range() {
        when(eventRecordMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.getEvents(100L, 0L, 500L);

        Page<DeviceEventRecord> page = capturePage();
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(DeviceDataServiceImpl.MAX_PAGE_SIZE);
    }

    @Test
    void getEvents_should_keep_valid_pagination() {
        when(eventRecordMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.getEvents(100L, 3L, 25L);

        Page<DeviceEventRecord> page = capturePage();
        assertThat(page.getCurrent()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(25);
    }

    @SuppressWarnings("unchecked")
    private Page<DeviceEventRecord> capturePage() {
        ArgumentCaptor<Page<DeviceEventRecord>> captor = ArgumentCaptor.forClass(Page.class);
        verify(eventRecordMapper).selectPage(captor.capture(), any(Wrapper.class));
        return captor.getValue();
    }
}