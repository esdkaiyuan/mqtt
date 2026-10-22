package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建API密钥请求DTO
 */
@Data
public class ApiKeyRequest {

    @NotBlank(message = "API Key名称不能为空")
    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    private String permissions; // JSON数组，如 ["device:read","device:write","data:read"]

    @Pattern(
            regexp = "^$|^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z?$",
            message = "expiresAt 必须为 ISO-8601 本地时间，例如 2026-12-31T23:59:59")
    private String expiresAt; // 可选，ISO 8601格式，如 2026-12-31T23:59:59
}
