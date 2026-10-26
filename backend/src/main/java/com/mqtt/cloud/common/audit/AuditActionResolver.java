package com.mqtt.cloud.common.audit;

import java.util.Locale;
import java.util.Set;

/**
 * 审计动作解析（T-53）。
 * <p>
 * 审计日志由拦截器统一记录，不要求各控制器逐个埋点；因此「这次请求到底做了什么」
 * 只能从 HTTP 方法 + 路径推导。解析结果是**展示与检索用的近似语义**，
 * 不是权限判断依据：真正的业务语义仍以各控制器为准。
 * <p>
 * 纯函数、无状态，便于单测；新增控制器时只需在 {@link #resourceType(String)} /
 * {@link #action(String, String)} 中补一条路径映射，未命中时落 {@code OTHER} / 方法级默认值。
 */
public final class AuditActionResolver {

    private static final Set<String> NON_RESOURCE_SEGMENTS = Set.of(
            "batch", "import", "import-template", "export", "export-credentials", "online",
            "shadow-summary", "executions", "current", "logout", "login", "register",
            "change-password", "activate", "batches", "stats", "replay-batch",
            "read-all", "read", "test");

    private AuditActionResolver() {
    }

    /** 解析结果：动作编码 + 资源类型 + 资源标识（均可空，动作与资源类型恒非空）。 */
    public record Resolved(String action, String resourceType, String resourceId) {
    }

    /**
     * 把一次写请求解析为审计三元组。
     *
     * @param method HTTP 方法（大小写不敏感）
     * @param path   去掉 context-path 后的请求路径
     */
    public static Resolved resolve(String method, String path) {
        String normalized = path == null ? "" : path;
        String httpMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return new Resolved(action(httpMethod, normalized),
                resourceType(normalized),
                resourceId(normalized));
    }

    /**
     * 资源类型判定：前缀匹配，特殊路径优先于通用前缀。
     */
    public static String resourceType(String path) {
        String p = stripTrailingSlash(path);
        if (p.startsWith("/devices/batch")) {
            return "DEVICE_BATCH";
        }
        if (p.startsWith("/devices")) {
            return "DEVICE";
        }
        if (p.startsWith("/device-registration")) {
            return "DEVICE_REGISTRATION";
        }
        if (p.startsWith("/products")) {
            return "PRODUCT";
        }
        if (p.startsWith("/device-groups")) {
            return "DEVICE_GROUP";
        }
        if (p.startsWith("/device-tags")) {
            return "DEVICE_TAG";
        }
        if (p.startsWith("/rules")) {
            return "RULE";
        }
        if (p.startsWith("/scenes")) {
            return "SCENE";
        }
        if (p.startsWith("/alert-escalations") || p.startsWith("/alerts/escalations")) {
            return "ALERT_ESCALATION";
        }
        if (p.startsWith("/alerts")) {
            return "ALERT";
        }
        if (p.startsWith("/ota/firmwares")) {
            return "OTA_FIRMWARE";
        }
        if (p.startsWith("/ota/tasks")) {
            return "OTA_TASK";
        }
        if (p.startsWith("/api-keys")) {
            return "API_KEY";
        }
        if (p.startsWith("/webhooks")) {
            return "WEBHOOK";
        }
        if (p.startsWith("/notification-channels")) {
            return "NOTIFICATION_CHANNEL";
        }
        if (p.startsWith("/notifications")) {
            return "NOTIFICATION";
        }
        if (p.startsWith("/dashboards")) {
            return "DASHBOARD";
        }
        if (p.startsWith("/hmi-views")) {
            return "HMI_VIEW";
        }
        if (p.startsWith("/admin/dead-letters")) {
            return "DEAD_LETTER";
        }
        if (p.startsWith("/auth")) {
            return "AUTH";
        }
        return "OTHER";
    }

    /**
     * 动作判定：先按具体路径后缀识别（命令 / 确认 / 启停 / 导入 …），
     * 再按 HTTP 方法兜底为 CREATE / UPDATE / DELETE。
     */
    public static String action(String method, String path) {
        String p = stripTrailingSlash(path);
        // 顺序要点：更具体的后缀必须排在更短的后缀之前（/certificate/revoke 先于 /certificate）
        if (p.endsWith("/certificate/revoke")) {
            return "REVOKE_CERTIFICATE";
        }
        if (p.endsWith("/certificate")) {
            return "ISSUE_CERTIFICATE";
        }
        if (p.endsWith("/reset-secret")) {
            return "RESET_SECRET";
        }
        if (p.endsWith("/export-credentials")) {
            return "EXPORT_CREDENTIALS";
        }
        if (p.endsWith("/commands")) {
            return "COMMAND";
        }
        if (p.endsWith("/shadow/desired")) {
            return "SHADOW_WRITE";
        }
        if (p.endsWith("/simulate")) {
            return "SIMULATE";
        }
        if (p.endsWith("/ack")) {
            return "ACK";
        }
        if (p.endsWith("/recover")) {
            return "RECOVER";
        }
        if (p.endsWith("/assign")) {
            return "ASSIGN";
        }
        if (p.endsWith("/replay-batch") || p.endsWith("/replay")) {
            return "REPLAY";
        }
        if (p.endsWith("/discard")) {
            return "DISCARD";
        }
        if (p.endsWith("/enable")) {
            return "ENABLE";
        }
        if (p.endsWith("/disable")) {
            return "DISABLE";
        }
        if (p.endsWith("/activate")) {
            return "ACTIVATE";
        }
        if (p.endsWith("/run")) {
            return "RUN";
        }
        if (p.endsWith("/test")) {
            return "TEST";
        }
        if (p.endsWith("/pause")) {
            return "PAUSE";
        }
        if (p.endsWith("/resume")) {
            return "RESUME";
        }
        if (p.endsWith("/terminate")) {
            return "TERMINATE";
        }
        if (p.endsWith("/advance")) {
            return "ADVANCE";
        }
        if (p.endsWith("/retry")) {
            return "RETRY";
        }
        if (p.endsWith("/rotate")) {
            return "ROTATE";
        }
        if (p.endsWith("/import")) {
            return "IMPORT";
        }
        if (p.endsWith("/members")) {
            return switch (method) {
                case "DELETE" -> "REMOVE_MEMBER";
                case "PUT", "PATCH" -> "UPDATE_MEMBER";
                default -> "ADD_MEMBER";
            };
        }
        if (p.endsWith("/sub-devices")) {
            return "DELETE".equals(method) ? "DETACH_SUB_DEVICE" : "ATTACH_SUB_DEVICE";
        }
        if (p.endsWith("/node-type")) {
            return "CHANGE_NODE_TYPE";
        }
        if (p.endsWith("/read-all")) {
            return "MARK_READ_ALL";
        }
        if (p.endsWith("/read")) {
            return "MARK_READ";
        }
        if (p.endsWith("/batches")) {
            return "CREATE";
        }
        if (p.startsWith("/auth/mfa/verify")) {
            return "LOGIN";
        }
        if (p.endsWith("/logout")) {
            return "LOGOUT";
        }
        if (p.endsWith("/login")) {
            return "LOGIN";
        }
        if (p.endsWith("/register")) {
            return "REGISTER";
        }
        if (p.endsWith("/change-password")) {
            return "CHANGE_PASSWORD";
        }
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> "OTHER";
        };
    }

    /**
     * 资源标识：取路径中的第一个业务标识段。
     * <p>
     * 例如 {@code /devices/sensor-01/commands → sensor-01}、
     * {@code /ota/tasks/12/records → 12}；静态段（batch/import/executions…）返回 null。
     */
    public static String resourceId(String path) {
        String p = stripTrailingSlash(path);
        if (p.isEmpty() || "/".equals(p)) {
            return null;
        }
        String[] segments = p.split("/");
        // segments[0] 恒为空串（路径以 / 开头），所以业务标识从下标 2 开始
        int index;
        if (p.startsWith("/ota/")) {
            index = 3;
        } else if (p.startsWith("/device-registration/batches/")) {
            index = 3;
        } else if (p.startsWith("/admin/dead-letters/")) {
            index = 3;
        } else {
            index = 2;
        }
        if (segments.length <= index) {
            return null;
        }
        String candidate = segments[index];
        if (candidate.isBlank() || NON_RESOURCE_SEGMENTS.contains(candidate)) {
            return null;
        }
        return candidate.length() > 128 ? candidate.substring(0, 128) : candidate;
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }
}