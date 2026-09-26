package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.mapper.HistoryRecordMapper;
import com.mqtt.cloud.service.HistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 历史记录服务实现类
 */
@Service
public class HistoryServiceImpl extends ServiceImpl<HistoryRecordMapper, HistoryRecord> implements HistoryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveHistoryRecord(Long deviceId, String topic, String payload) {
        HistoryRecord record = new HistoryRecord();
        record.setDeviceId(deviceId);
        record.setTopic(topic);
        record.setPayload(payload);
        record.setTimestamp(LocalDateTime.now());
        this.save(record);
    }

    @Override
    public IPage<HistoryRecord> getHistoryRecords(HistoryQueryDTO dto, Long userId) {
        Page<HistoryRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        return this.baseMapper.pageQuery(page, dto, userId);
    }
}
