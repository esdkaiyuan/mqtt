package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.mapper.MessageMapper;
import com.mqtt.cloud.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 历史记录服务实现类：数据源为 {@code message} 表，响应结构保持不变。
 */
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final MessageMapper messageMapper;

    @Override
    public IPage<HistoryRecord> getHistoryRecords(HistoryQueryDTO dto, Long userId) {
        Page<HistoryRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        return messageMapper.pageHistoryQuery(page, dto, userId);
    }
}