package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.service.DeviceCertificateService;
import com.mqtt.cloud.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备证书与令牌控制器（T-25）。
 * <p>
 * 三个端点都以设备为主体：X.509 模式下签发一次性证书与私钥、按需吊销，
 * JWT 模式下签发设备令牌。均属产品管理动作，不进入公开端点列表。
 */
@Tag(name = "设备证书与令牌", description = "X.509 证书签发与吊销、设备令牌签发")
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceCertificateController {

    private final DeviceCertificateService deviceCertificateService;

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "签发设备证书",
            description = "为设备签发 X.509 证书，返回证书与私钥 PEM 文本；私钥不留存，仅本次响应返回")
    @PostMapping("/{deviceId}/certificate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceCertificateService.IssuedCertificate> issueCertificate(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        return Result.success(deviceCertificateService.issue(deviceId));
    }

    @Operation(summary = "吊销设备证书",
            description = "按设备吊销已签发证书，可附带原因；吊销后该设备证书认证即时失效")
    @PostMapping("/{deviceId}/certificate/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceCertificateService.RevokedCertificate> revokeCertificate(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId,
            @RequestBody(required = false) RevokeRequest request) {
        String reason = request == null ? null : request.getReason();
        return Result.success(deviceCertificateService.revoke(deviceId, reason));
    }

    @Operation(summary = "签发设备令牌",
            description = "为设备签发 JWT 令牌，用于 JWT 认证模式下以令牌替代一机一密口令")
    @PostMapping("/{deviceId}/token")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<DeviceTokenService.DeviceToken> issueToken(
            @Parameter(description = "设备ID", required = true) @PathVariable Long deviceId) {
        return Result.success(deviceTokenService.issueToken(deviceId));
    }

    /**
     * 吊销请求体。原因可空，长度由服务层统一校验，此处只做承载。
     */
    @Data
    public static class RevokeRequest {

        private String reason;
    }
}
