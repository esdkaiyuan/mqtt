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
 * <p>
 * <b>执行方必须严格隔离</b>：同一点位若被两条路线同时采集，会向 {@code device_property_latest}
 * 双写，既污染最新值又让属性历史出现同一时刻的重复点。因此
 * （1）取点位的服务方法必须先按本字段过滤；
 * （2）同一台设备下的点位执行方必须一致，不允许混用 —— 该约束由
 * {@code DevicePointTableServiceImpl} 在写入时校验并拒绝，不做静默改写。
 */
public final class PointExecutorValue {

    /** 边缘网关执行采集（路线 A，默认） */
    public static final String EDGE = "EDGE";

    /** 平台侧主站执行轮询采集（路线 B） */
    public static final String PLATFORM = "PLATFORM";

    private PointExecutorValue() {
    }

    /**
     * 归一化执行方；{@code null} / 空白按 {@link #EDGE} 处理。
     * <p>
     * 与 V18 迁移中 {@code executor VARCHAR(16) NOT NULL DEFAULT 'EDGE'} 保持一致，
     * 保证存量数据与未来手工插入的 NULL 行为确定（不会被判为非法值而写入失败）。
     * 大小写不敏感，便于外部配置（如 {@code driver_config} JSON）传入小写值。
     */
    public static String normalize(String executor) {
        if (executor == null || executor.isBlank()) {
            return EDGE;
        }
        return switch (executor.trim().toUpperCase()) {
            case EDGE -> EDGE;
            case PLATFORM -> PLATFORM;
            default -> executor.trim();
        };
    }

    /** 是否为平台侧执行（路线 B）；{@code null} / 非法值一律视为 {@code false}。 */
    public static boolean isPlatform(String executor) {
        return PLATFORM.equals(normalize(executor));
    }

    /** 是否为受支持的执行方；{@code null} / 空白视为合法（归一化为 {@link #EDGE}）。 */
    public static boolean isSupported(String executor) {
        if (executor == null || executor.isBlank()) {
            return true;
        }
        return switch (executor.trim().toUpperCase()) {
            case EDGE, PLATFORM -> true;
            default -> false;
        };
    }
}
