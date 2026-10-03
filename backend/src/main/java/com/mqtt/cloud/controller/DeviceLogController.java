package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;
import com.mqtt.cloud.service.DeviceLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备日志控制器（T-20 设计文档 §6.2）。
 * <p>
 * 只读接口：把 {@code message / device_command_record / device_event_record / device_status_history}
 * 四张存量表在查询时聚合成一条按时间倒序的统一时间线，不新增表、不改写入链路。
 */
@Tag(name = "设备日志", description = "设备统一日志时间线：聚合报文、命令、事件、状态变更，供设备详情页诊断排查")
@RestController
@RequestMapping("/devices/{deviceId}/logs")
public class DeviceLogController {

    private final DeviceLogService deviceLogService;

    public DeviceLogController(DeviceLogService deviceLogService) {
        this.deviceLogService = deviceLogService;
    }

    @Operation(summary = "查询设备日志时间线", description = "按设备聚合四类日志（MESSAGE / COMMAND / EVENT / STATUS），按发生时间倒序分页返回。"
            + "types 可多值过滤（缺省=全部）；startTime / endTime 为半开区间，格式 yyyy-MM-dd HH:mm:ss 或 ISO-8601；"
            + "keyword 模糊匹配 topic / identifier / 错误信息 / 载荷。时间格式非法或跨度超上限返回 6223，"
            + "类型不在白名单返回 6224，设备不存在返回 2002，非归属用户访问返回 2003")
    @GetMapping
    public Result<IPage<DeviceLogItem>> getDeviceLogs(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId,
            DeviceLogQueryDTO query) {
        return Result.success(deviceLogService.page(SecurityUtils.requireUserId(), deviceId, query));
    }
}
