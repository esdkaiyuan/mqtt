package com.mqtt.cloud.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 属性时序与看板共享常量（T-21 设计文档 §4.4 / §6.1 / §7.2）。
 * <p>
 * 集中「时间桶白名单与秒数映射 / 可聚合数值类型 / 跨度与序列与桶数上限兜底 / 保留清理默认值」口径，
 * 避免服务层校验、SQL 分桶、清理巡检三处各自散落字面量而产生漂移。
 */
public final class PropertyHistoryConstants {

    private PropertyHistoryConstants() {
    }

    /** 时间桶白名单 → 秒数：SQL 内不做字符串解析，由服务层查表后以 {@code bucketSeconds} 传入。 */
    public static final Map<String, Long> BUCKET_SECONDS;

    static {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("1m", 60L);
        buckets.put("5m", 300L);
        buckets.put("15m", 900L);
        buckets.put("30m", 1800L);
        buckets.put("1h", 3600L);
        buckets.put("6h", 21600L);
        buckets.put("1d", 86400L);
        BUCKET_SECONDS = Collections.unmodifiableMap(buckets);
    }

    /** 时间桶白名单：请求 {@code bucket} 不在其中即视为非法（{@code 6227}）。 */
    public static final Set<String> SUPPORTED_BUCKETS = BUCKET_SECONDS.keySet();

    /** 默认时间桶。 */
    public static final String DEFAULT_BUCKET = "5m";

    /** 参与 {@code min/max/avg} 聚合的物模型数值类型；其余类型仅 {@code count}。 */
    public static final Set<String> NUMERIC_TYPES = Set.of("int", "float", "double");

    /** 默认单次查询跨度上限（天）兜底值：配置缺失或非正时使用。 */
    public static final int DEFAULT_MAX_RANGE_DAYS = 31;
    /** 默认序列数上限（设备 × 属性）兜底值。 */
    public static final int DEFAULT_MAX_SERIES = 20;
    /** 默认单序列数据点上限兜底值。 */
    public static final int DEFAULT_MAX_POINTS = 500;

    /** 默认历史保留天数兜底值。 */
    public static final int DEFAULT_RETENTION_DAYS = 30;
    /** 默认清理批大小兜底值。 */
    public static final int DEFAULT_CLEANUP_BATCH_SIZE = 1000;

    /** 默认看板数量上限兜底值。 */
    public static final int DEFAULT_MAX_DASHBOARD_COUNT = 20;
    /** 默认单看板面板数上限兜底值。 */
    public static final int DEFAULT_MAX_DASHBOARD_PANELS = 12;

    /** 看板图型白名单。 */
    public static final Set<String> SUPPORTED_CHART_TYPES = Set.of("line", "bar");
    /** 看板聚合口径白名单。 */
    public static final Set<String> SUPPORTED_AGGREGATIONS = Set.of("avg", "min", "max", "count");
}
