package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;

/**
 * 历史记录服务接口
 */
public interface HistoryService extends IService<HistoryRecord> {

    void saveHistoryRecord(Long deviceId, String topic, String payload);

    IPage<HistoryRecord> getHistoryRecords(HistoryQueryDTO dto, Long userId);
}
