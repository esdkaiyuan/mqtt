package com.mqtt.cloud.common.constant;

/**
 * 点表下发通道常量（T-31 P3，路线 A）。
 * <p>
 * 路线 A 的点表下发<b>不新开 topic</b>，而是复用既有的设备下行通道
 * {@code device 下设备Key/cmd/down}，靠 {@link #SERVICE_POINT_TABLE_DISPATCH} 这个
 * 保留服务标识与普通控制指令区分。这样做的收益是三重的：
 * <ol>
 *   <li>回执关联天然成立 —— 回执 {@code id} 即命令记录主键，{@code CommandReplyService}
 *       的条件更新（{@code WHERE status IN ('PENDING','SENT')}）已经保证「重复回执只生效一次」，
 *       无需再写一套幂等；</li>
 *   <li>设备离线时自动入队补发，无需自建重试队列；</li>
 *   <li>超时巡检、命令历史、前端控制台全部直接可用。</li>
 * </ol>
 * 代价是点表下发会出现在命令历史里，与服务调用混排 —— 这一点靠 {@link #SOURCE} 区分。
 */
public final class PointDispatchConstants {

    /**
     * 下发载荷中的协议版本串（与 Alink 载荷的 {@code version} 字段区分：
     * 那个是协议版本，本字段是<b>点表版本</b>，用于边缘侧丢弃乱序到达的旧版本）。
     */
    public static final String PAYLOAD_VERSION_FIELD = "version";

    /** 点表版本取值来源：Redis 原子自增键前缀（跨副本单调）。 */
    public static final String VERSION_KEY_PREFIX = "mqtt:pointtable:ver:";

    /** Redis 不可用时的版本退化前缀（单副本保证，见 P6 文档限制说明）。 */
    public static final String VERSION_FALLBACK_PREFIX = "mqtt:pointtable:fbver:";

    /** 下发载荷字段：点表版本号（单调递增的整数）。 */
    public static final String FIELD_VERSION = "version";

    /** 下发载荷字段：点位数组。 */
    public static final String FIELD_POINTS = "points";

    /** 下发载荷字段：下发时间（毫秒时间戳，便于网关排查时序问题）。 */
    public static final String FIELD_DISPATCHED_AT = "dispatchedAt";

    /** 下发载荷字段：点表所属设备 ID（网关侧校验，防止串设备应用）。 */
    public static final String FIELD_DEVICE_ID = "deviceId";

    /** 单个点位的字段名，<b>与 {@code device_point_table} 列名一一对应</b>。 */
    public static final String FIELD_POINT_KEY = "pointKey";

    public static final String FIELD_POINT_TYPE = "pointType";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_DATA_TYPE = "dataType";
    public static final String FIELD_SCALE = "scale";
    public static final String FIELD_OFFSET = "offset";
    public static final String FIELD_UNIT = "unit";
    public static final String FIELD_READ_INTERVAL_MS = "readIntervalMs";

    /** 度量标签：下发结果。 */
    public static final String METRIC_DISPATCH = "point_table_dispatch_total";

    private PointDispatchConstants() {
    }
}
