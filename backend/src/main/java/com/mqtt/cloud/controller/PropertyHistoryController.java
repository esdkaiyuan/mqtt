package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.response.PropertySeriesVO;
import com.mqtt.cloud.service.PropertyHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 属性时序控制器（T-21 设计文档 §6.2）。
 * <p>
 * 只读接口：把 {@code device_property_history} 在查询时按时间桶聚合，返回逐「设备 × 属性」的序列，
 * 供设备详情页趋势图与可保存看板消费；用户 ID 由登录态注入，设备归属逐一校验。
 */
@Tag(name = "属性时序", description = "属性历史分桶聚合查询：按设备、属性与时间窗返回趋势序列")
@RestController
@RequestMapping("/properties")
public class PropertyHistoryController {

    private final PropertyHistoryService propertyHistoryService;

    public PropertyHistoryController(PropertyHistoryService propertyHistoryService) {
        this.propertyHistoryService = propertyHistoryService;
    }

    @Operation(summary = "查询属性时序", description = "按设备集合与属性标识符集合，在给定时间窗与桶粒度下返回聚合序列。"
            + "startTime / endTime 为半开区间，格式 yyyy-MM-dd HH:mm:ss 或 ISO-8601；"
            + "bucket 白名单 1m / 5m / 15m / 30m / 1h / 6h / 1d（缺省 5m）。"
            + "时间格式非法、跨度超上限或序列数超限返回 6225，属性未建模返回 6226，"
            + "桶非法或桶数超限返回 6227，设备不存在返回 2002，非归属用户访问返回 2003")
    @GetMapping("/history")
    public Result<List<PropertySeriesVO>> getPropertyHistory(PropertyHistoryQueryDTO query) {
        return Result.success(propertyHistoryService.query(SecurityUtils.requireUserId(), query));
    }
}
