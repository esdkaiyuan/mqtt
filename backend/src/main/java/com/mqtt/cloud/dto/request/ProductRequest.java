package com.mqtt.cloud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProductRequest {

    @NotBlank(message = "产品标识不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$",
            message = "产品标识只能包含小写字母、数字与连字符，且以字母或数字开头")
    private String productKey;

    @NotBlank(message = "产品名称不能为空")
    private String productName;

    private String description;

    /**
     * 认证方式，取值 {@code SECRET} / {@code X509} / {@code JWT}，可空且空值回落 {@code SECRET}。
     * 取值集合以 {@link com.mqtt.cloud.common.constant.AuthModeValue} 为准，
     * 校验落在服务层，避免在此注解内硬编码字符串。
     */
    private String authMode;

    private String topicPrefix;

    private String payloadFormat;

    private String metadataSchema;
}