package com.mqtt.cloud.common.security;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.filter.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户解析工具。
 * <p>
 * 收敛各处在 Controller/Service 中重复的 {@code SecurityContextHolder} 取值逻辑，
 * 统一未认证时的异常行为。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前认证主体，未认证时返回 {@code null}。
     */
    public static UserPrincipal getPrincipalOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    /**
     * 获取当前认证主体，未认证时抛出 401。
     */
    public static UserPrincipal requirePrincipal() {
        UserPrincipal principal = getPrincipalOrNull();
        if (principal == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return principal;
    }

    /**
     * 获取当前登录用户 ID，未认证时抛出 401。
     */
    public static Long requireUserId() {
        return requirePrincipal().getUserId();
    }

    /**
     * 当前用户是否为管理员。
     */
    public static boolean isAdmin() {
        UserPrincipal principal = getPrincipalOrNull();
        return principal != null && "ADMIN".equals(principal.getRole());
    }
}