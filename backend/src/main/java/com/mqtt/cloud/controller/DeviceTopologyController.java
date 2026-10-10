package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.DeviceTopologyProperties;
import com.mqtt.cloud.dto.request.CreateSubDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceTopologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备拓扑控制器（T-26 设计文档 §10.1）。
 * <p>
 * 六端点覆盖网关与子设备的挂载关系：建子设备、挂载、解绑、列子设备、查父网关、变更角色。
 * 归属校验沿用设备管理既有规则 —— ADMIN 可操作任意设备，其他角色仅限自己名下设备；
 * 校验发生在调用任何业务服务之前，避免越权读写。
 * <p>
 * 回滚开关 {@code app.device-topology.enabled} 关闭时所有端点一律返回 {@code 404}
 * （{@link ResultCode#NOT_FOUND}），不泄露拓扑能力已存在的事实。
 */
@Tag(name = "设备拓扑", description = "网关与子设备挂载、解绑、角色变更（T-26）")
@RestController
@RequestMapping("/devices")
public class DeviceTopologyController {

    private static final String FIELD_NODE_TYPE = "nodeType";

    private final DeviceTopologyService deviceTopologyService;
    private final DeviceService deviceService;
    private final DeviceTopologyProperties topologyProperties;

    public DeviceTopologyController(DeviceTopologyService deviceTopologyService,
                                    DeviceService deviceService,
                                    DeviceTopologyProperties topologyProperties) {
        this.deviceTopologyService = deviceTopologyService;
        this.deviceService = deviceService;
        this.topologyProperties = topologyProperties;
    }

    @Operation(summary = "创建子设备", description = "在指定网关下创建子设备：产品与父网关取自路径，"
            + "一步建成即挂载（node_type=SUB）；子设备与网关同产品为硬约束，违反返回 6258")
    @PostMapping("/{gatewayId}/sub-devices")
    public Result<DeviceCreatedDTO> createSubDevice(
            @Parameter(description = "网关设备ID", required = true) @PathVariable Long gatewayId,
            @Valid @RequestBody CreateSubDeviceDTO dto) {
        requireEnabled();
        checkDeviceOwnership(gatewayId);
        return Result.success(deviceService.createSubDevice(gatewayId, dto));
    }

    @Operation(summary = "挂载子设备", description = "把既有设备挂到网关下；网关当前为 DIRECT 时自动提升为 GATEWAY。"
            + "父设备不是网关返回 6256，产品不一致返回 6258，层级或子设备数量超限返回 6257 / 6260")
    @PostMapping("/{gatewayId}/sub-devices/{subDeviceId}")
    public Result<Device> attachSubDevice(
            @Parameter(description = "网关设备ID", required = true) @PathVariable Long gatewayId,
            @Parameter(description = "待挂载的设备ID", required = true) @PathVariable Long subDeviceId) {
        requireEnabled();
        checkDeviceOwnership(gatewayId);
        checkDeviceOwnership(subDeviceId);
        return Result.success(deviceTopologyService.attach(gatewayId, subDeviceId));
    }

    @Operation(summary = "解绑子设备", description = "解除挂载关系：子设备 parent_id 置空、角色回落 DIRECT；"
            + "网关角色保持 GATEWAY 不回落")
    @DeleteMapping("/{gatewayId}/sub-devices/{subDeviceId}")
    public Result<Void> detachSubDevice(
            @Parameter(description = "网关设备ID", required = true) @PathVariable Long gatewayId,
            @Parameter(description = "待解绑的设备ID", required = true) @PathVariable Long subDeviceId) {
        requireEnabled();
        checkDeviceOwnership(gatewayId);
        checkDeviceOwnership(subDeviceId);
        deviceTopologyService.detach(gatewayId, subDeviceId);
        return Result.success();
    }

    @Operation(summary = "获取子设备列表", description = "分页查询指定网关下的子设备，过滤项沿用设备查询条件")
    @GetMapping("/{gatewayId}/sub-devices")
    public Result<IPage<Device>> listSubDevices(
            @Parameter(description = "网关设备ID", required = true) @PathVariable Long gatewayId,
            DeviceQueryDTO dto) {
        requireEnabled();
        checkDeviceOwnership(gatewayId);
        return Result.success(deviceTopologyService.listSubDevices(gatewayId, dto));
    }

    @Operation(summary = "查询父网关", description = "取子设备的父网关；该设备没有父时 data 为空")
    @GetMapping("/{subDeviceId}/parent")
    public Result<Device> getParent(
            @Parameter(description = "子设备ID", required = true) @PathVariable Long subDeviceId) {
        requireEnabled();
        checkDeviceOwnership(subDeviceId);
        return Result.success(deviceTopologyService.findParent(subDeviceId));
    }

    @Operation(summary = "变更拓扑角色", description = "在 DIRECT 与 GATEWAY 之间切换；网关下仍有子设备时不得降级（6259）。"
            + "子设备角色由挂载/解绑推导，不接受直接设置")
    @PutMapping("/{deviceId}/node-type")
    public Result<Device> changeNodeType(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId,
            @RequestBody Map<String, String> body) {
        requireEnabled();
        checkDeviceOwnership(deviceId);
        String nodeType = body == null ? null : body.get(FIELD_NODE_TYPE);
        if (nodeType == null || nodeType.isBlank()) {
            throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_INVALID);
        }
        return Result.success(deviceTopologyService.changeNodeType(deviceId, nodeType));
    }

    /** 回滚开关：关闭时端点整体隐藏，返回 404 而非 403，避免暴露拓扑能力。 */
    private void requireEnabled() {
        if (!topologyProperties.isEnabled()) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * 校验设备归属：ADMIN 可访问任意设备，其他角色仅能访问自己名下的设备。
     * <p>
     * 与 {@link DeviceController} 同语义：设备不存在或已逻辑删除抛 2002，非归属且非 ADMIN 抛 2003。
     */
    private void checkDeviceOwnership(Long deviceId) {
        Device device = deviceService.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean isOwner = device.getOwnerId() != null && device.getOwnerId().equals(principal.getUserId());
        if (!"ADMIN".equals(principal.getRole()) && !isOwner) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
    }
}
