package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
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

    private String expiresAt; // 可选，ISO 8601格式，如 2026-12-31T23:59:59
}
