package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.DebugSimulateRequest;
import com.mqtt.cloud.dto.response.DebugCodeResponse;
import com.mqtt.cloud.dto.response.DebugContextResponse;
import com.mqtt.cloud.dto.response.DebugSimulateResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.DeviceDebugService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备调试中心控制器（T-37）。
 * <p>
 * 归属校验与 {@code DeviceController} 同口径：设备不存在 2002，非本人（非 ADMIN）2003。
 * 模拟上行为写操作，{@code SecurityConfig} 限定 ADMIN / OPERATOR；连接信息与代码生成只读，
 * 任意登录用户均可查看自己名下的设备。
 * <p>
 * 开关：{@code app.debug.enabled}（默认 true）。关闭时控制器不注册，端点 404。
 */
@Tag(name = "设备调试", description = "连接信息、模拟上行、属性代码生成")
@ConditionalOnProperty(prefix = "app.debug", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/devices/{deviceKey}/debug")
public class DeviceDebugController {

    private final DeviceService deviceService;
    private final ProductService productService;
    private final DeviceDebugService deviceDebugService;

    public DeviceDebugController(DeviceService deviceService,
                                 ProductService productService,
                                 DeviceDebugService deviceDebugService) {
        this.deviceService = deviceService;
        this.productService = productService;
        this.deviceDebugService = deviceDebugService;
    }

    @Operation(summary = "调试上下文", description = "返回 Broker 连接参数、上下行 Topic、物模型投影与属性最新值；"
            + "设备不存在返回 2002，非归属用户返回 2003")
    @GetMapping("/context")
    public Result<DebugContextResponse> context(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey);
        Product product = productService.requireById(device.getProductId());
        return Result.success(deviceDebugService.context(device, product));
    }

    @Operation(summary = "模拟上行", description = "消息类型 data / heartbeat / lwt / reply / ota；"
            + "与真机上报走同一条摄取管线。类型非法 6297，载荷非法 6298；非归属设备 2003")
    @PostMapping("/simulate")
    public Result<DebugSimulateResponse> simulate(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Valid @RequestBody DebugSimulateRequest request) {
        Device device = requireOwnedDevice(deviceKey);
        Product product = productService.requireById(device.getProductId());
        return Result.success(deviceDebugService.simulate(SecurityUtils.requireUserId(), device, product,
                request.getMessageType(), request.getPayload()));
    }

    @Operation(summary = "属性代码", description = "target=device 生成设备端样例（javascript/python/java/arduino），"
            + "target=host 生成上位机端 Open API 样例（javascript/python/java）；不支持的目标或语言返回 6299")
    @GetMapping("/code")
    public Result<DebugCodeResponse> code(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "目标端：device / host") @RequestParam(defaultValue = "device") String target,
            @Parameter(description = "语言：javascript / python / java / arduino") @RequestParam(defaultValue = "javascript") String language) {
        Device device = requireOwnedDevice(deviceKey);
        Product product = productService.requireById(device.getProductId());
        return Result.success(deviceDebugService.code(device, product, target, language));
    }

    /** 与 DeviceController 同口径：不存在与越权分别返回 2002 / 2003，ADMIN 可跨账号。 */
    private Device requireOwnedDevice(String deviceKey) {
        Device device = deviceService.getDeviceByKey(deviceKey);
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
