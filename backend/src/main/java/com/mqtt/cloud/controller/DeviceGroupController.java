package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DeviceGroupMemberRequest;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.dto.request.DeviceIdsRequest;
import com.mqtt.cloud.dto.response.DeviceGroupMemberVO;
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
 * 设备分组控制台接口（T-18 设计文档 §10.1，T-48 成员共享）。
 * <p>
 * 分组默认按当前登录用户隔离；所有者可把分组共享给其他用户（VIEW / OPERATE），
 * 共享成员可见分组子树与组内设备。分组结构、设备关联与成员管理仍仅限所有者。
 */
@Tag(name = "设备分组", description = "设备分组树管理、成员共享、分组内设备与设备关联")
@RestController
@RequestMapping("/device-groups")
public class DeviceGroupController {

    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupTagAssembler assembler;

    public DeviceGroupController(DeviceGroupService deviceGroupService, DeviceGroupTagAssembler assembler) {
        this.deviceGroupService = deviceGroupService;
        this.assembler = assembler;
    }

    @Operation(summary = "分组树", description = "默认返回本人分组树；includeShared=true 时合并被共享的分组子树（分组管理页用）")
    @GetMapping("/tree")
    public Result<List<DeviceGroup>> tree(
            @Parameter(description = "是否包含被共享分组") @RequestParam(defaultValue = "false") boolean includeShared) {
        return Result.success(deviceGroupService.tree(SecurityUtils.requireUserId(), includeShared));
    }

    @Operation(summary = "分组平铺列表", description = "默认返回本人全部分组；includeShared=true 时包含被共享分组")
    @GetMapping
    public Result<List<DeviceGroup>> list(
            @Parameter(description = "是否包含被共享分组") @RequestParam(defaultValue = "false") boolean includeShared) {
        return Result.success(deviceGroupService.list(SecurityUtils.requireUserId(), includeShared));
    }

    @Operation(summary = "创建分组", description = "同级不重名、父分组须为本人分组、层级不超过 5；非法返回 6213，父分组不存在返回 6212")
    @PostMapping
    public Result<DeviceGroup> create(@RequestBody DeviceGroupRequest request) {
        return Result.success(deviceGroupService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "分组详情", description = "所有者或共享成员可读；不存在返回 6212，无权返回 6310")
    @GetMapping("/{id}")
    public Result<DeviceGroup> get(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id) {
        return Result.success(deviceGroupService.getAccessible(SecurityUtils.requireUserId(), id));
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

    @Operation(summary = "分组内设备分页", description = "所有者或共享成员可读；含所有后代分组，结果批量装配 groups / tags")
    @GetMapping("/{id}/devices")
    public Result<IPage<Device>> pageDevices(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        IPage<Device> result = deviceGroupService.pageDevices(SecurityUtils.requireUserId(), id, page, size);
        assembler.assemble(result.getRecords());
        return Result.success(result);
    }

    @Operation(summary = "分组成员列表", description = "仅分组所有者可读")
    @GetMapping("/{id}/members")
    public Result<List<DeviceGroupMemberVO>> listMembers(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id) {
        return Result.success(deviceGroupService.listMembers(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "添加分组成员", description = "仅所有者；按用户名或邮箱查找，权限 VIEW / OPERATE；重复添加幂等更新")
    @PostMapping("/{id}/members")
    public Result<DeviceGroupMemberVO> addMember(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @RequestBody DeviceGroupMemberRequest request) {
        return Result.success(deviceGroupService.addMember(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "修改分组成员权限", description = "仅所有者；权限 VIEW / OPERATE")
    @PutMapping("/{id}/members/{memberId}")
    public Result<DeviceGroupMemberVO> updateMember(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "成员记录 ID", required = true) @PathVariable Long memberId,
            @RequestBody DeviceGroupMemberRequest request) {
        return Result.success(deviceGroupService.updateMember(
                SecurityUtils.requireUserId(), id, memberId, request));
    }

    @Operation(summary = "移除分组成员", description = "仅所有者；立即收回该用户对本分组子树的访问")
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(
            @Parameter(description = "分组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "成员记录 ID", required = true) @PathVariable Long memberId) {
        deviceGroupService.removeMember(SecurityUtils.requireUserId(), id, memberId);
        return Result.success();
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