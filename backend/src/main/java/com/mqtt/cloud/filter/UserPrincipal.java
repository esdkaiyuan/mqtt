package com.mqtt.cloud.filter;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

/**
 * 用户认证主体（Spring Security使用）
 * <p>
 * 控制台 JWT 认证与开放接口 API Key 认证共用此主体：
 * 前者 role 为用户角色，后者 role 固定为 {@code EXTERNAL} 且携带 apiKeyId。
 */
@Data
public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final String role;
    /** API Key 认证时对应的密钥 ID，JWT 认证时为 null */
    private final Long apiKeyId;

    public UserPrincipal(Long userId, String username, String role) {
        this(userId, username, role, null);
    }

    public UserPrincipal(Long userId, String username, String role, Long apiKeyId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.apiKeyId = apiKeyId;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role);
    }

    public String getPassword() {
        return null;
    }

    public boolean isAccountNonExpired() {
        return true;
    }

    public boolean isAccountNonLocked() {
        return true;
    }

    public boolean isCredentialsNonExpired() {
        return true;
    }

    public boolean isEnabled() {
        return true;
    }
}