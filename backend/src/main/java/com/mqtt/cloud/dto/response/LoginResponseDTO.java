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

    public static LoginResponseDTO of(String token, UserResponseDTO user) {
        LoginResponseDTO dto = new LoginResponseDTO();
        dto.setToken(token);
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}