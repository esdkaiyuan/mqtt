package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.audit.AuditRequestInfo;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ChangePasswordDTO;
import com.mqtt.cloud.dto.request.LoginDTO;
import com.mqtt.cloud.dto.request.RegisterDTO;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.UserResponseDTO;
import com.mqtt.cloud.entity.AuditLog;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.AuditLogService;
import com.mqtt.cloud.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 认证管理控制器
 */
@Slf4j
@Tag(name = "认证管理", description = "用户注册、登录、登出、当前用户信息与修改密码")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;
    private final AuditLogService auditLogService;

    public AuthController(UserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "用户注册", description = "注册新用户，默认角色为 VIEWER；用户名重复返回 1001")
    @SecurityRequirements
    @PostMapping("/register")
    public Result<UserResponseDTO> register(@Valid @RequestBody RegisterDTO dto,
                                            HttpServletRequest request) {
        try {
            UserResponseDTO created = userService.register(dto);
            recordAuth(request, created.getId(), created.getUsername(), created.getRole(),
                    "REGISTER", true, 200, null);
            return Result.success(created);
        } catch (BusinessException e) {
            recordAuth(request, null, dto.getUsername(), null,
                    "REGISTER", false, e.getHttpStatus().value(), e.getMessage());
            throw e;
        }
    }

    @Operation(summary = "用户登录", description = "校验用户名密码，成功返回 JWT Token 与用户信息；凭证错误返回 3001")
    @SecurityRequirements
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody LoginDTO dto,
                                          HttpServletRequest request) {
        try {
            LoginResponseDTO result = userService.login(dto.getUsername(), dto.getPassword());
            recordAuth(request, result.getId(), result.getUsername(), result.getRole(),
                    "LOGIN", true, 200, null);
            return Result.success(result);
        } catch (BusinessException e) {
            // 登录失败也要留痕：连续失败是账号被盗用的第一信号
            recordAuth(request, null, dto.getUsername(), null,
                    "LOGIN", false, e.getHttpStatus().value(), e.getMessage());
            throw e;
        }
    }

    @Operation(summary = "用户登出", description = "将当前 Token 加入黑名单使其立即失效，需携带 Bearer Token")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        UserPrincipal principal = SecurityUtils.getPrincipalOrNull();
        try {
            String token = resolveToken(request);
            if (token != null) {
                userService.logout(token);
            }
            if (principal != null) {
                recordAuth(request, principal.getUserId(), principal.getUsername(), principal.getRole(),
                        "LOGOUT", true, 200, null);
            }
            return Result.success();
        } catch (RuntimeException e) {
            if (principal != null) {
                recordAuth(request, principal.getUserId(), principal.getUsername(), principal.getRole(),
                        "LOGOUT", false, 500, e.getMessage());
            }
            throw e;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Operation(summary = "获取当前用户", description = "返回当前登录用户的资料，需携带 Bearer Token")
    @GetMapping("/current")
    public Result<UserResponseDTO> getCurrentUser() {
        return Result.success(userService.getCurrentUser(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "修改密码", description = "校验旧密码后更新为新密码，需携带 Bearer Token")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(SecurityUtils.requireUserId(), dto);
        return Result.success();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 认证类操作的审计留痕。
     * <p>
     * 登录 / 注册 / 登出不在 {@code AuditLogInterceptor} 的覆盖范围内：
     * 前两者在鉴权之前（拿不到 SecurityContext），登出会主动清空上下文；
     * 因此由本控制器显式记录。
     */
    private void recordAuth(HttpServletRequest request, Long userId, String username, String role,
                            String action, boolean success, int statusCode, String error) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setUsername(AuditRequestInfo.truncate(username, 64));
            entry.setRole(AuditRequestInfo.truncate(role, 32));
            entry.setSource(userId == null ? "ANONYMOUS" : "CONSOLE");
            entry.setHttpMethod("POST");
            entry.setPath(AuditRequestInfo.servletPath(request));
            entry.setAction(action);
            entry.setResourceType("AUTH");
            entry.setResourceId(AuditRequestInfo.truncate(username, 128));
            entry.setStatusCode(statusCode);
            entry.setSuccess(success);
            entry.setErrorMessage(AuditRequestInfo.truncate(error, 512));
            entry.setIp(AuditRequestInfo.clientIp(request));
            entry.setUserAgent(AuditRequestInfo.userAgent(request));
            entry.setDurationMs(0L);
            entry.setCreatedAt(LocalDateTime.now());
            auditLogService.record(entry);
        } catch (Exception e) {
            log.warn("认证审计写入失败 action={} username={} err={}", action, username, e.getMessage());
        }
    }
}