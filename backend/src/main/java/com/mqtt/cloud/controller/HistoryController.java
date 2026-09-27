package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 历史查询控制器
 */
@Tag(name = "历史查询", description = "历史消息记录的分页查询，支持按设备、Topic 与时间范围过滤")
@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @Operation(summary = "查询历史记录", description = "分页查询历史消息记录，支持 deviceId、topic 与时间范围过滤")
    @GetMapping
    public Result<IPage<HistoryRecord>> getHistory(HistoryQueryDTO dto) {
        return Result.success(historyService.getHistoryRecords(dto, SecurityUtils.requireUserId()));
    }

    @Operation(summary = "查询设备历史", description = "按设备 ID 分页查询该设备的历史消息记录")
    @GetMapping("/device/{deviceId}")
    public Result<IPage<HistoryRecord>> getDeviceHistory(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "起始时间，格式 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startTime,
            @Parameter(description = "结束时间，格式 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endTime) {

        HistoryQueryDTO dto = new HistoryQueryDTO();
        dto.setDeviceId(deviceId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);

        return Result.success(historyService.getHistoryRecords(dto, SecurityUtils.requireUserId()));
    }
}