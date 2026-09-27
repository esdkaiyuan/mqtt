package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "统计分析", description = "仪表盘所需的设备与消息维度统计")
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "总览统计", description = "返回设备总数、在线设备数、消息总数等概览指标（仅统计当前用户数据）")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(analyticsService.getOverviewStats(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "设备状态分布", description = "按 ONLINE/OFFLINE/INACTIVE 分组统计设备数量")
    @GetMapping("/devices/status")
    public Result<List<Map<String, Object>>> getDeviceStatusDistribution() {
        return Result.success(analyticsService.getDeviceStatusDistribution(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "设备类型分布", description = "按 deviceType 分组统计设备数量")
    @GetMapping("/devices/type")
    public Result<List<Map<String, Object>>> getDeviceTypeDistribution() {
        return Result.success(analyticsService.getDeviceTypeDistribution(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "消息趋势统计", description = "按天统计消息量，days 默认 7 天")
    @GetMapping("/messages/trend")
    public Result<List<Map<String, Object>>> getMessageTrend(
            @Parameter(description = "统计天数，默认7") @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(analyticsService.getMessageTrend(days, SecurityUtils.requireUserId()));
    }
}