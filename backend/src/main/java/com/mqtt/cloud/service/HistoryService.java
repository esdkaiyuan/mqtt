package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;

/**
 * 历史记录服务接口。
 * <p>
 * 历史数据已改由 {@code message} 表承载（{@code direction=SUBSCRIBE} 且 {@code topic} 以 {@code /data} 结尾），
 * 原 {@code history_record} 表停止写入，仅保留只读。
 */
public interface HistoryService {

    IPage<HistoryRecord> getHistoryRecords(HistoryQueryDTO dto, Long userId);
}