package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 设备 ID 集合请求体（T-18：单台 / 批量加入分组、打标签复用）。
 */
@Data
public class DeviceIdsRequest {

    /** 设备 ID 集合 */
    private List<Long> deviceIds;
}