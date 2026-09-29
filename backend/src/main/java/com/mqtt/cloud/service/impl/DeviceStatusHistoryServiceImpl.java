package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.entity.DeviceStatus;
import com.mqtt.cloud.mapper.DeviceStatusHistoryMapper;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 设备状态历史服务实现类
 */
@Service
public class DeviceStatusHistoryServiceImpl extends ServiceImpl<DeviceStatusHistoryMapper, DeviceStatus> implements DeviceStatusHistoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordStatusChange(Long deviceId, String newStatus) {
        DeviceStatus history = new DeviceStatus();
        history.setDeviceId(deviceId);
        history.setStatus(newStatus);
        history.setTimestamp(LocalDateTime.now());
        this.save(history);
    }

    @Override
    public java.util.List<DeviceStatus> getHistoryByDeviceId(Long deviceId) {
        return this.lambdaQuery()
                .eq(DeviceStatus::getDeviceId, deviceId)
                .orderByDesc(DeviceStatus::getTimestamp)
                .list();
    }
}
