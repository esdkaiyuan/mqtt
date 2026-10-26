package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * 登录响应DTO（含访问令牌，不含密码）
 */
@Data
public class LoginResponseDTO {
    private String token;
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String role;
    private String status;

    /** 是否需要二步验证（T-58 MFA） */
    private Boolean mfaRequired;

    /** 二步验证临时令牌；仅在 mfaRequired=true 时返回 */
    private String mfaToken;

    public static LoginResponseDTO of(String token, UserResponseDTO user) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setToken(token);
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setMfaRequired(false);
        return dto;
    }

    /** 密码已通过、等待动态口令的二步登录响应（T-58）。 */
    public static LoginResponseDTO mfaRequired(String mfaToken, String username) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setUsername(username);
        dto.setMfaRequired(true);
        dto.setMfaToken(mfaToken);
        return dto;
    }
}