package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.BatchAssignRequest;
import com.mqtt.cloud.dto.request.BatchCommandRequest;
import com.mqtt.cloud.dto.request.BatchTargetRequest;
import com.mqtt.cloud.dto.response.BatchOperationResult;
import com.mqtt.cloud.service.DeviceBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备批量操作接口（T-18 设计文档 §10.3）。
 * <p>
 * 目标集合由「手选设备 ID ∪ 分组（含子分组）设备 ∪ 标签设备」去重得到，并在服务端按当前用户二次过滤；
 * 逐台操作相互隔离，单台失败不阻断其余，结果逐台返回。
 */
@Tag(name = "设备批量操作", description = "按设备 / 分组 / 标签并集批量下发命令、启用禁用、关联分组与标签")
@RestController
@RequestMapping("/devices/batch")
public class DeviceBatchController {

    private static final String ACTION_REMOVE = "REMOVE";

    private final DeviceBatchService deviceBatchService;

    public DeviceBatchController(DeviceBatchService deviceBatchService) {
        this.deviceBatchService = deviceBatchService;
    }

    @Operation(summary = "批量下发命令", description = "逐台生成命令记录，强制异步；单台失败只记该台 error；"
            + "目标为空或超 500 返回 6217")
    @PostMapping("/commands")
    public Result<BatchOperationResult> commands(@RequestBody BatchCommandRequest request) {
        return Result.success(deviceBatchService.sendCommands(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "批量启用", description = "逐台恢复连接许可，幂等；目标为空或超 500 返回 6217")
    @PostMapping("/enable")
    public Result<BatchOperationResult> enable(@RequestBody BatchTargetRequest request) {
        return Result.success(deviceBatchService.setEnabled(SecurityUtils.requireUserId(), request, true));
    }

    @Operation(summary = "批量禁用", description = "逐台禁止连接许可并踢线，幂等；目标为空或超 500 返回 6217")
    @PostMapping("/disable")
    public Result<BatchOperationResult> disable(@RequestBody BatchTargetRequest request) {
        return Result.success(deviceBatchService.setEnabled(SecurityUtils.requireUserId(), request, false));
    }

    @Operation(summary = "批量关联分组", description = "action=ADD 加入（唯一键去重幂等）/ REMOVE 移出；"
            + "目标分组不存在返回 6212，目标为空或超 500 返回 6217")
    @PostMapping("/groups")
    public Result<BatchOperationResult> assignGroup(@RequestBody BatchAssignRequest request) {
        boolean add = !isRemove(request);
        return Result.success(deviceBatchService.assignGroup(SecurityUtils.requireUserId(), request, add));
    }

    @Operation(summary = "批量关联标签", description = "action=ADD 打标（唯一键去重幂等）/ REMOVE 去标；"
            + "目标标签不存在返回 6214，目标为空或超 500 返回 6217")
    @PostMapping("/tags")
    public Result<BatchOperationResult> assignTag(@RequestBody BatchAssignRequest request) {
        boolean add = !isRemove(request);
        return Result.success(deviceBatchService.assignTag(SecurityUtils.requireUserId(), request, add));
    }

    private boolean isRemove(BatchAssignRequest request) {
        return request != null && ACTION_REMOVE.equalsIgnoreCase(request.getAction());
    }
}