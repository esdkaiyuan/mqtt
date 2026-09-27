package com.mqtt.cloud.dto.response;

import lombok.Data;

/**
 * 创建设备响应：含一次性明文密钥，仅本次响应返回，平台不提供二次查询。
 */
@Data
public class DeviceCreatedDTO {

    private Long id;
    private String deviceKey;
    private String productKey;
    /** MQTT 认证用户名：{productKey}.{deviceKey} */
    private String username;
    /** 一次性明文密钥，仅本次响应返回 */
    private String deviceSecret;
}