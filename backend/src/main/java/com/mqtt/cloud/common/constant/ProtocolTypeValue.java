package com.mqtt.cloud.common.constant;

/**
 * 接入协议类型（T-30）。
 * <p>
 * 这是平台<b>唯一</b>的「设备怎么连上来」抽象维度，此前 product 表完全没有此概念，
 * 致使纯 Modbus / OPC-UA 设备只能被建模成网关。
 * <p>
 * 路线 A（边缘驱动）与路线 B（平台侧主站）都挂在这套维度下：
 * 差别只在 {@code device_point_table.executor}（谁来执行采集），
 * 不在协议本身——这样切换路线不需要改产品定义。
 */
public final class ProtocolTypeValue {

    /** 设备以 MQTT 协议直连（既有行为，存量产品默认） */
    public static final String MQTT = "MQTT";

    /** 设备以 HTTP 上报（既有行为，T-24） */
    public static final String HTTP = "HTTP";

    /** Modbus 寄存器设备（需经网关转换或平台主站采集） */
    public static final String MODBUS = "MODBUS";

    /** OPC-UA 节点设备 */
    public static final String OPC_UA = "OPC_UA";

    /** 网关代理接入：以网关自身 MQTT 连接代连子设备（T-26既有能力） */
    public static final String GATEWAY_MQTT = "GATEWAY_MQTT";

    private ProtocolTypeValue() {
    }

    /** 是否为受支持的协议；null 视为 {@link #MQTT}（向后兼容存量数据）。 */
    public static boolean isSupported(String protocolType) {
        if (protocolType == null || protocolType.isBlank()) {
            return true;
        }
        return switch (protocolType) {
            case MQTT, HTTP, MODBUS, OPC_UA, GATEWAY_MQTT -> true;
            default -> false;
        };
    }

    /** 归一化：空值回落 MQTT。 */
    public static String normalize(String protocolType) {
        return (protocolType == null || protocolType.isBlank()) ? MQTT : protocolType;
    }
}
