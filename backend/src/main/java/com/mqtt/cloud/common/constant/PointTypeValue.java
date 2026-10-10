package com.mqtt.cloud.common.constant;

/**
 * 点位类型（T-31）。
 * <p>
 * 描述「这个点位在设备侧的物理位置属于哪一类」，决定采集时走哪种 Modbus 功能码，
 * 以及边缘网关下发时该读什么。命名刻意保持协议中性：{@link #HOLDING_REGISTER} 等寄存器类
 * 是 Modbus 概念，{@link #TAG} / {@link #NODE} 则是给 OPC-UA 等后续协议预留的位置。
 * <p>
 * 与 {@link ProtocolTypeValue} 的关系：协议决定「怎么连设备」，点位类型决定「连上后读什么」。
 * 两者正交，例如 Modbus 设备上可以同时存在寄存器点位与线圈点位。
 */
public final class PointTypeValue {

    /** Modbus 保持寄存器（功能码 03），可读，工程量最常用 */
    public static final String HOLDING_REGISTER = "HOLDING_REGISTER";

    /** Modbus 输入寄存器（功能码 04），只读 */
    public static final String INPUT_REGISTER = "INPUT_REGISTER";

    /** Modbus 线圈（功能码 01/05），单个布尔量 */
    public static final String COIL = "COIL";

    /** Modbus 离散输入（功能码 02），单个只读布尔量 */
    public static final String DISCRETE_INPUT = "DISCRETE_INPUT";

    /** OPC-UA / PLC 标签点 */
    public static final String TAG = "TAG";

    /** OPC-UA 节点点 */
    public static final String NODE = "NODE";

    private PointTypeValue() {
    }

    /** 归一化点位类型；{@code null} / 空白按 {@link #HOLDING_REGISTER} 处理。 */
    public static String normalize(String pointType) {
        if (pointType == null || pointType.isBlank()) {
            return HOLDING_REGISTER;
        }
        return pointType.trim().toUpperCase();
    }

    /** 是否为受支持的点位类型；{@code null} / 空白视为合法（归一化为 {@link #HOLDING_REGISTER}）。 */
    public static boolean isSupported(String pointType) {
        if (pointType == null || pointType.isBlank()) {
            return true;
        }
        return switch (pointType.trim().toUpperCase()) {
            case HOLDING_REGISTER, INPUT_REGISTER, COIL, DISCRETE_INPUT, TAG, NODE -> true;
            default -> false;
        };
    }

    /** 是否为单点布尔量类型（线圈 / 离散输入）；此类点位无需寄存器区间合并读取。 */
    public static boolean isSingleBit(String pointType) {
        return switch (normalize(pointType)) {
            case COIL, DISCRETE_INPUT -> true;
            default -> false;
        };
    }
}
