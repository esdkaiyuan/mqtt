package com.mqtt.cloud.common.constant;

/**
 * 设备拓扑角色取值，与 device.node_type 字段一致（T-26）。
 * <p>
 * 与 {@code device_type} 的区别：{@code device_type} 是业务分类（sensor/gateway/actuator），
 * 拓扑角色只描述「在网关代理链路中的位置」，两者互不推导。
 */
public final class DeviceNodeType {

    /** 普通设备：独立直连，既无父也无子 */
    public static final String DIRECT = "DIRECT";

    /** 网关：可挂载子设备并代理其上/下行 */
    public static final String GATEWAY = "GATEWAY";

    /** 子设备：经父网关代连，不直接接入 */
    public static final String SUB = "SUB";

    private DeviceNodeType() {
    }
}
