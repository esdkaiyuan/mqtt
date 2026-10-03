package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.dto.request.DeviceIdsRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.service.DeviceGroupService;
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
 * 设备分组控制台接口（T-18 设计文档 §10.1）。
 * <p>
 * 分组按当前登录用户隔离，仅可读写本人数据；分组资源越权统一 {@code 403}。
 * 「按分组筛选」与「分组内设备分页」均**包含所有后代分组**。
 */
@Tag(name = "设备分组", description = "设备分组树管理、分组内设备与设备关联，仅可操作本人数据")
@RestController
@RequestMapping("/device-groups")
public class DeviceGroupController {

    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupTagAssembler assembler;

    public DeviceGroupController(DeviceGroupService deviceGroupService, DeviceGroupTagAssembler assembler) {
        this.deviceGroupService = deviceGroupService;
        this.assembler = assembler;
    }

    @Operation(summary = "分组树", description = "返回本人分组树（含 children 与直接关联设备数 deviceCount）")
    @GetMapping("/tree")
    public Result<List<DeviceGroup>> tree() {
        return Result.success(deviceGroupService.tree(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "分组平铺列表", description = "返回本人全部分组（下拉 / 选择器用）")
    @GetMapping
    public Result<List<DeviceGroup>> list() {
        return Result.success(deviceGroupService.list(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建分组", description = "同级不重名、父分组须为本人分组、层级不超过 5；非法返回 6213，父分组不存在返回 6212")
    @PostMapping
    public Result<DeviceGroup> create(@RequestBody DeviceGroupRequest request) {
        return Result.success(deviceGroupService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "分组详情", description = "不存在返回 6212，越权返回 403")
    @GetMapping("/{id}")
    public Result<DeviceGroup> get(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id) {
        return Result.success(deviceGroupService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新分组", description = "重命名 / 改描述 / 改排序 / 移动父节点（成环或超深返回 6213）")
    @PutMapping("/{id}")
    public Result<DeviceGroup> update(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceGroupRequest request) {
        return Result.success(deviceGroupService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除分组", description = "仅空分组（无子分组且无关联设备）可删，否则返回 6216")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id) {
        deviceGroupService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "分组内设备分页", description = "含所有后代分组的设备，结果批量装配 groups / tags")
    @GetMapping("/{id}/devices")
    public Result<IPage<Device>> pageDevices(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        IPage<Device> result = deviceGroupService.pageDevices(SecurityUtils.requireUserId(), id, page, size);
        assembler.assemble(result.getRecords());
        return Result.success(result);
    }

    @Operation(summary = "加入分组", description = "单台 / 批量加入；设备非本人返回 2003，不存在返回 2002；重复关联幂等")
    @PostMapping("/{id}/devices")
    public Result<Void> addDevices(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceIdsRequest request) {
        deviceGroupService.addDevices(SecurityUtils.requireUserId(), id, request.getDeviceIds());
        return Result.success();
    }

    @Operation(summary = "移出分组", description = "单台 / 批量移出；设备非本人返回 2003")
    @DeleteMapping("/{id}/devices")
    public Result<Void> removeDevices(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceIdsRequest request) {
        deviceGroupService.removeDevices(SecurityUtils.requireUserId(), id, request.getDeviceIds());
        return Result.success();
    }
}