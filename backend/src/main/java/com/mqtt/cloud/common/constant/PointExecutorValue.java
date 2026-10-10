package com.mqtt.cloud.common.constant;

/**
 * 点位执行方（T-30）。
 * <p>
 * 这是路线 A 与路线 B 的<b>唯一差异点</b>：
 * <ul>
 *   <li>{@link #EDGE} —— 点位定义下发给边缘网关，由网关自行采集（路线 A，平台不碰现场网络）；</li>
 *   <li>{@link #PLATFORM} —— 平台侧主站按点表轮询采集（路线 B，平台需可达现场网络）。</li>
 * </ul>
 * 点表数据模型两者共用，切换路线只需改这一列，无需改产品定义或重建点表。
 */
public final class PointExecutorValue {

    /** 边缘网关执行采集（路线 A，默认） */
    public static final String EDGE = "EDGE";

    /** 平台侧主站执行轮询采集（路线 B） */
    public static final String PLATFORM = "PLATFORM";

    private PointExecutorValue() {
    }

    public static boolean isSupported(String executor) {
        return EDGE.equals(executor) || PLATFORM.equals(executor);
    }
}
