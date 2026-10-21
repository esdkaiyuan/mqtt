package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 分组成员共享请求（T-48）。
 */
@Data
public class DeviceGroupMemberRequest {

    /** 目标用户：用户名或邮箱（二选一匹配，精确）。 */
    private String username;

    /** 权限：VIEW / OPERATE。 */
    private String permission;
}
