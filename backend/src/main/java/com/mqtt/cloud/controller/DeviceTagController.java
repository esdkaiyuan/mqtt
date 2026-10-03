package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DeviceIdsRequest;
import com.mqtt.cloud.dto.request.DeviceTagRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceTag;
import com.mqtt.cloud.service.DeviceTagService;
import com.mqtt.cloud.service.DeviceGroupTagAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备标签控制台接口（T-18 设计文档 §10.2）。
 * <p>
 * 标签按当前登录用户隔离，仅可读写本人数据；标签资源越权统一 {@code 403}。
 * 删除标签会自动解除其全部设备关联。
 */
@Tag(name = "设备标签", description = "设备标签管理与设备打标，仅可操作本人数据")
@RestController
@RequestMapping("/device-tags")
public class DeviceTagController {

    private final DeviceTagService deviceTagService;
    private final DeviceGroupTagAssembler assembler;

    public DeviceTagController(DeviceTagService deviceTagService, DeviceGroupTagAssembler assembler) {
        this.deviceTagService = deviceTagService;
        this.assembler = assembler;
    }

    @Operation(summary = "标签列表", description = "返回本人全部标签")
    @GetMapping
    public Result<List<DeviceTag>> list() {
        return Result.success(deviceTagService.list(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建标签", description = "同一用户下不重名、颜色需为 #RRGGBB；非法返回 6215")
    @PostMapping
    public Result<DeviceTag> create(@RequestBody DeviceTagRequest request) {
        return Result.success(deviceTagService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "标签详情", description = "不存在返回 6214，越权返回 403")
    @GetMapping("/{id}")
    public Result<DeviceTag> get(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id) {
        return Result.success(deviceTagService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新标签", description = "重命名 / 改色；非法返回 6215")
    @PutMapping("/{id}")
    public Result<DeviceTag> update(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceTagRequest request) {
        return Result.success(deviceTagService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除标签", description = "自动解除其全部设备关联；不存在返回 6214")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id) {
        deviceTagService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "标签内设备分页", description = "结果批量装配 groups / tags")
    @GetMapping("/{id}/devices")
    public Result<IPage<Device>> pageDevices(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        IPage<Device> result = deviceTagService.pageDevices(SecurityUtils.requireUserId(), id, page, size);
        assembler.assemble(result.getRecords());
        return Result.success(result);
    }

    @Operation(summary = "打标签", description = "单台 / 批量打标；设备非本人返回 2003，不存在返回 2002；重复打标幂等")
    @PostMapping("/{id}/devices")
    public Result<Void> addDevices(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceIdsRequest request) {
        deviceTagService.addDevices(SecurityUtils.requireUserId(), id, request.getDeviceIds());
        return Result.success();
    }

    @Operation(summary = "去标签", description = "单台 / 批量去标；设备非本人返回 2003")
    @DeleteMapping("/{id}/devices")
    public Result<Void> removeDevices(
            @Parameter(description = "标签 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceIdsRequest request) {
        deviceTagService.removeDevices(SecurityUtils.requireUserId(), id, request.getDeviceIds());
        return Result.success();
    }
}