package com.mqtt.cloud.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 多设备影子聚合条目（T-42 / G-5-1）。
 * <p>
 * 只读投影：把单设备影子接口的 desired / reported / delta 与设备展示信息合并成一行，
 * 供运维在一个表格里横向对比多台设备。不做写入与缓存。
 */
@Data
public class DeviceShadowSummaryVO {

    private Long deviceId;
    private String deviceKey;
    private String deviceName;
    private String status;
    private boolean modeled;
    private long version;
    private Map<String, String> desired;
    private Map<String, String> reported;
    private Map<String, String> delta;
    private LocalDateTime updatedAt;
}
