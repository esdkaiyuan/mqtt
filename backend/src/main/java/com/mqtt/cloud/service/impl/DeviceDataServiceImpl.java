package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.mapper.DeviceEventRecordMapper;
import com.mqtt.cloud.mapper.DevicePropertyLatestMapper;
import com.mqtt.cloud.service.DeviceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备物模型数据只读查询实现。
 */
@Service
@RequiredArgsConstructor
public class DeviceDataServiceImpl implements DeviceDataService {

    /** 单页上限：防止 pageSize 被放大成全表扫描。 */
    static final long MAX_PAGE_SIZE = 100L;

    private final DevicePropertyLatestMapper propertyLatestMapper;
    private final DeviceEventRecordMapper eventRecordMapper;

    @Override
    public List<DevicePropertyLatest> getPropertyLatest(Long deviceId) {
        return propertyLatestMapper.selectList(Wrappers.<DevicePropertyLatest>lambdaQuery()
                .eq(DevicePropertyLatest::getDeviceId, deviceId)
                .orderByAsc(DevicePropertyLatest::getIdentifier));
    }

    @Override
    public IPage<DeviceEventRecord> getEvents(Long deviceId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return eventRecordMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<DeviceEventRecord>lambdaQuery()
                        .eq(DeviceEventRecord::getDeviceId, deviceId)
                        .orderByDesc(DeviceEventRecord::getReportedAt)
                        .orderByDesc(DeviceEventRecord::getId));
    }
}