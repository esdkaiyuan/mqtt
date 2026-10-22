package com.mqtt.cloud.service;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * API Key scope 解析与判定。
 * <p>
 * 空 permissions 保持历史语义：拥有全部 scope；非空时必须是 JSON 字符串数组，
 * 无法解析或数组为空一律拒绝，避免格式错误被误当成“全部权限”。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyScopeService {

    public static final String DEVICE_READ = "device:read";
    public static final String DEVICE_WRITE = "device:write";
    public static final String STATS_READ = "stats:read";
    private static final Set<String> KNOWN_SCOPES = Set.of(
            DEVICE_READ, DEVICE_WRITE, STATS_READ, "data:read", "data:write", "*");

    private final ObjectMapper objectMapper;

    /** 根据请求方法/路径推导所需 scope。 */
    public String requiredScope(String method, String path) {
        String requestPath = path == null ? "" : path;
        if (requestPath.equals("/external/v1/stats") || requestPath.equals("/external/v1/stats/")) {
            return STATS_READ;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return DEVICE_WRITE;
        }
        return DEVICE_READ;
    }

    /** 创建密钥时校验格式与已知 scope，避免写入一个永远无法使用的密钥。 */
    public void requireValid(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(permissions);
            if (node == null || !node.isArray() || node.isEmpty()) {
                throw invalidPermissions();
            }
            for (JsonNode item : node) {
                if (!item.isTextual() || !KNOWN_SCOPES.contains(item.asText().trim().toLowerCase(Locale.ROOT))) {
                    throw invalidPermissions();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw invalidPermissions();
        }
    }

    private BusinessException invalidPermissions() {
        return new BusinessException(ResultCode.BAD_REQUEST,
                "permissions 必须是已知 scope 的 JSON 数组，例如 [\"device:read\",\"device:write\"]");
    }

    public boolean allows(String permissions, String requiredScope) {
        if (permissions == null || permissions.isBlank()) {
            return true;
        }
        if (requiredScope == null || requiredScope.isBlank()) {
            return false;
        }

        Set<String> scopes = parse(permissions);
        if (scopes.contains("*")) {
            return true;
        }
        return switch (requiredScope) {
            case DEVICE_READ -> scopes.contains(DEVICE_READ) || scopes.contains("data:read");
            case DEVICE_WRITE -> scopes.contains(DEVICE_WRITE) || scopes.contains("data:write");
            case STATS_READ -> scopes.contains(STATS_READ);
            default -> false;
        };
    }

    private Set<String> parse(String permissions) {
        try {
            JsonNode node = objectMapper.readTree(permissions);
            if (node == null || !node.isArray()) {
                return Set.of();
            }
            Set<String> scopes = new HashSet<>();
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    scopes.add(item.asText().trim().toLowerCase(Locale.ROOT));
                }
            }
            return scopes;
        } catch (Exception e) {
            return Set.of();
        }
    }
}
