package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.MfaCodeRequest;
import com.mqtt.cloud.dto.request.MfaVerifyRequest;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.MfaSetupResponse;
import com.mqtt.cloud.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TOTP 多因素认证控制器（T-58）。
 * <p>
 * {@code app.mfa.enabled=false} 时整组不注册（404），登录也不再要求二步验证；
 * 已绑定用户的密钥保留，重新开启后自动恢复生效。
 */
@Tag(name = "多因素认证", description = "TOTP 动态口令绑定、启停与二步登录")
@RestController
@RequestMapping("/auth/mfa")
@ConditionalOnProperty(prefix = "app.mfa", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    @Operation(summary = "查询 MFA 状态", description = "返回当前登录用户是否已启用 TOTP MFA")
    @GetMapping("/status")
    public Result<Boolean> status() {
        return Result.success(mfaService.isEnabled(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "发起 MFA 绑定", description = "生成待确认密钥并返回 authenticator 录入信息；"
            + "secret 与 otpauthUri 只在本次返回，请录入 authenticator 后回填动态口令确认")
    @PostMapping("/setup")
    public Result<MfaSetupResponse> setup() {
        return Result.success(mfaService.beginEnrollment(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "确认并启用 MFA", description = "校验动态口令后启用 MFA；口令错误 6320，"
            + "未发起绑定 6319，已启用 6318")
    @PostMapping("/enable")
    public Result<Void> enable(@Valid @RequestBody MfaCodeRequest request) {
        mfaService.enable(SecurityUtils.requireUserId(), request.getCode());
        return Result.success();
    }

    @Operation(summary = "停用 MFA", description = "校验动态口令后停用并清空密钥；口令错误 6320，未启用 6319")
    @PostMapping("/disable")
    public Result<Void> disable(@Valid @RequestBody MfaCodeRequest request) {
        mfaService.disable(SecurityUtils.requireUserId(), request.getCode());
        return Result.success();
    }

    @Operation(summary = "二步登录验证", description = "密码通过后校验 MFA 临时令牌与动态口令，"
            + "成功返回完整登录响应；令牌无效 6321，口令错误 6320。该端点免登录")
    @SecurityRequirements
    @PostMapping("/verify")
    public Result<LoginResponseDTO> verify(@Valid @RequestBody MfaVerifyRequest request) {
        return Result.success(mfaService.verifyLogin(request));
    }
}