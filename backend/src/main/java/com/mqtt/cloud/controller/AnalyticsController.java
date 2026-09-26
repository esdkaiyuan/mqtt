package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.success(analyticsService.getOverviewStats(SecurityUtils.requireUserId()));
    }

    @GetMapping("/devices/status")
    public Result<List<Map<String, Object>>> getDeviceStatusDistribution() {
        return Result.success(analyticsService.getDeviceStatusDistribution(SecurityUtils.requireUserId()));
    }

    @GetMapping("/devices/type")
    public Result<List<Map<String, Object>>> getDeviceTypeDistribution() {
        return Result.success(analyticsService.getDeviceTypeDistribution(SecurityUtils.requireUserId()));
    }

    @GetMapping("/messages/trend")
    public Result<List<Map<String, Object>>> getMessageTrend(
            @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(analyticsService.getMessageTrend(days, SecurityUtils.requireUserId()));
    }
}