package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组成员共享视图（T-48）。
 */
@Data
public class DeviceGroupMemberVO {

    private Long id;

    private Long userId;

    private String username;

    private String email;

    /** VIEW / OPERATE */
    private String permission;

    private LocalDateTime createdAt;
}
