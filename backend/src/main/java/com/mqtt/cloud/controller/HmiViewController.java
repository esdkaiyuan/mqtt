package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.HmiSaveDTO;
import com.mqtt.cloud.dto.response.HmiDetailVO;
import com.mqtt.cloud.dto.response.HmiDraftVO;
import com.mqtt.cloud.dto.response.HmiSummaryVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.HmiViewService;
import com.mqtt.cloud.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 快速上位机控制器（T-38）。
 * <p>
 * HMI 仅归属创建者本人；绑定设备必须属于当前用户（ADMIN 可跨账号）。
 * 设备归属在控制层校验，配置与物模型绑定在服务层校验。
 */
@Tag(name = "快速上位机", description = "按物模型生成 Web 组态，实时监控与控制单台设备")
@RestController
@RequestMapping("/hmi-views")
public class HmiViewController {

    private final HmiViewService hmiViewService;
    private final DeviceService deviceService;
    private final ProductService productService;

    public HmiViewController(HmiViewService hmiViewService,
                             DeviceService deviceService,
                             ProductService productService) {
        this.hmiViewService = hmiViewService;
        this.deviceService = deviceService;
        this.productService = productService;
    }

    @Operation(summary = "上位机列表", description = "返回本人上位机摘要，按更新时间倒序")
    @GetMapping
    public Result<List<HmiSummaryVO>> list() {
        return Result.success(hmiViewService.list(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "创建上位机", description = "校验名称、设备归属与控件绑定；"
            + "数量超限 6301、配置非法 6302、绑定非法 6303、控件类型非法 6304")
    @PostMapping
    public Result<HmiDetailVO> create(@Valid @RequestBody HmiSaveDTO dto) {
        Device device = requireOwnedDevice(dto.getDeviceId());
        Product product = productService.requireById(device.getProductId());
        return Result.success(hmiViewService.create(SecurityUtils.requireUserId(), device, product, dto));
    }

    @Operation(summary = "生成上位机草稿", description = "按设备物模型生成控件布局，不落库；"
            + "未建模时只生成事件面板并返回提示")
    @GetMapping("/draft")
    public Result<HmiDraftVO> draft(
            @Parameter(description = "设备 ID", required = true) @RequestParam Long deviceId) {
        Device device = requireOwnedDevice(deviceId);
        Product product = productService.requireById(device.getProductId());
        return Result.success(hmiViewService.draft(device, product));
    }

    @Operation(summary = "上位机详情", description = "不存在或非本人返回 6300")
    @GetMapping("/{id}")
    public Result<HmiDetailVO> detail(
            @Parameter(description = "上位机 ID", required = true) @PathVariable Long id) {
        return Result.success(hmiViewService.detail(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新上位机", description = "校验同创建；不存在或非本人返回 6300")
    @PutMapping("/{id}")
    public Result<HmiDetailVO> update(
            @Parameter(description = "上位机 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody HmiSaveDTO dto) {
        Device device = requireOwnedDevice(dto.getDeviceId());
        Product product = productService.requireById(device.getProductId());
        return Result.success(hmiViewService.update(SecurityUtils.requireUserId(), id, device, product, dto));
    }

    @Operation(summary = "删除上位机", description = "物理删除本人上位机；不存在或非本人返回 6300")
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @Parameter(description = "上位机 ID", required = true) @PathVariable Long id) {
        hmiViewService.remove(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    /** 与 DeviceController 同口径：不存在 2002，非本人 2003，ADMIN 可跨账号。 */
    private Device requireOwnedDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        Device device = deviceService.getDeviceById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        boolean isOwner = device.getOwnerId() != null && device.getOwnerId().equals(principal.getUserId());
        if (!"ADMIN".equals(principal.getRole()) && !isOwner) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
        return device;
    }
}
