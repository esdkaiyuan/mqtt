package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.service.HistoryService;
import org.springframework.web.bind.annotation.*;

/**
 * 历史查询控制器
 */
@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public Result<IPage<HistoryRecord>> getHistory(HistoryQueryDTO dto) {
        return Result.success(historyService.getHistoryRecords(dto, SecurityUtils.requireUserId()));
    }

    @GetMapping("/device/{deviceId}")
    public Result<IPage<HistoryRecord>> getDeviceHistory(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        HistoryQueryDTO dto = new HistoryQueryDTO();
        dto.setDeviceId(deviceId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);

        return Result.success(historyService.getHistoryRecords(dto, SecurityUtils.requireUserId()));
    }
}