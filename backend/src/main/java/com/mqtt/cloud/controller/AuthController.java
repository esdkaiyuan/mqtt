package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ChangePasswordDTO;
import com.mqtt.cloud.dto.request.LoginDTO;
import com.mqtt.cloud.dto.request.RegisterDTO;
import com.mqtt.cloud.dto.response.LoginResponseDTO;
import com.mqtt.cloud.dto.response.UserResponseDTO;
import com.mqtt.cloud.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证管理控制器
 */
@Tag(name = "认证管理", description = "用户注册、登录、登出、当前用户信息与修改密码")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册", description = "注册新用户，默认角色为 VIEWER；用户名重复返回 1001")
    @PostMapping("/register")
    public Result<UserResponseDTO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @Operation(summary = "用户登录", description = "校验用户名密码，成功返回 JWT Token 与用户信息；凭证错误返回 3001")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto.getUsername(), dto.getPassword()));
    }

    @Operation(summary = "用户登出", description = "将当前 Token 加入黑名单使其立即失效，需携带 Bearer Token")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            userService.logout(token);
        }
        SecurityContextHolder.clearContext();
        return Result.success();
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
}