package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ActivateDeviceDTO;
import com.mqtt.cloud.dto.request.BatchRegisterDeviceDTO;
import com.mqtt.cloud.dto.response.BatchRegisterResultDTO;
import com.mqtt.cloud.entity.DeviceRegBatch;
import com.mqtt.cloud.service.DeviceRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备批量注册与激活控制器（T-30）。
 * <p>
 * 对标阿里云 IoT / 腾讯云物联网的「批量注册实例 → 激活 → 烧录凭据」标准流程，
 * 补齐当前仅有单设备创建（{@code POST /devices}）的接入断层。
 */
@Tag(name = "设备注册", description = "批量注册设备、激活码签发与消费、注册批次追溯")
@RestController
@RequestMapping("/device-registration")
public class DeviceRegistrationController {

    private final DeviceRegistrationService registrationService;

    public DeviceRegistrationController(DeviceRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Operation(summary = "批量注册设备",
            description = "按 prefix + 序号批量创建设备（单批 1~1000），逐台签发一机一密凭据；"
                    + "响应中的 credentials 为一次性凭据明文，仅本次返回，平台不提供二次查询。"
                    + "单台失败（如 deviceKey 重复 2001）不阻断整批，失败明细见 failedItems。"
                    + "issueActivateCode=true（默认）时设备先置为禁用，需消费激活码后方可连接")
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BatchRegisterResultDTO> batchRegister(
            @Valid @RequestBody BatchRegisterDeviceDTO dto) {
        return Result.success(registrationService.batchRegister(SecurityUtils.requireUserId(), dto));
    }

    @Operation(summary = "设备激活",
            description = "设备侧首次连接前消费激活码完成激活，成功后设备转为可连接状态。"
                    + "激活码非法或已被使用返回 6273。未走激活流程的设备（activateCode 为空）幂等返回成功。"
                    + "该端点免登录，供设备直连调用")
    @PostMapping("/activate")
    public Result<Void> activate(@Valid @RequestBody ActivateDeviceDTO dto) {
        registrationService.activate(dto);
        return Result.success();
    }

    @Operation(summary = "查询注册批次列表",
            description = "分页查询当前用户的设备注册批次；ADMIN 可见全部")
    @GetMapping("/batches")
    public Result<IPage<DeviceRegBatch>> pageBatches(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，上限100") @RequestParam(defaultValue = "20") int size) {
        return Result.success(registrationService.pageBatches(SecurityUtils.requireUserId(), page, size));
    }

    @Operation(summary = "查询注册批次详情",
            description = "按批次号查询详情，含一次性凭据与失败明细；非归属用户返回 6270")
    @GetMapping("/batches/{batchNo}")
    public Result<DeviceRegBatch> getBatch(
            @Parameter(description = "批次号", required = true) @PathVariable String batchNo) {
        return Result.success(registrationService.getBatch(SecurityUtils.requireUserId(), batchNo));
    }
}
