package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建Webhook配置请求DTO
 */
@Data
public class WebhookConfigRequest {

    @NotBlank(message = "Webhook名称不能为空")
    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;

    @NotBlank(message = "回调URL不能为空")
    @Size(max = 500, message = "URL不能超过500个字符")
    private String url;

    private String secret;

    @NotBlank(message = "事件类型不能为空")
    private String events;

    private String deviceKey;

    @Size(max = 500, message = "自定义请求头不能超过500个字符")
    private String headers;

    private Integer retryCount;
    private Integer timeoutSeconds;
}
