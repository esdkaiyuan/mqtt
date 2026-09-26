package com.mqtt.cloud.common.constant;

/**
 * 设备在线状态取值，与 device.status 字段一致
 */
public final class DeviceStatusValue {

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";
    public static final String INACTIVE = "INACTIVE";

    private DeviceStatusValue() {
    }
}