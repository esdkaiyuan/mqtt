package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 属性时序配置（T-21 设计文档 §9.2）。
 * <p>
 * 前缀 {@code app.property-history}，字段全部带默认值，风格对齐 {@link DeviceLogProperties}。
 * 三类开关与边界：
 * <ul>
 *   <li>{@code enabled}：写入总开关，关闭时摄取旁路直接短路（用于压测 / 极端降级），查询链路不受影响；</li>
 *   <li>{@code max-range-days} / {@code max-series} / {@code max-points}：查询边界，置 0 / 负数时由服务层夹取为常量默认值；</li>
 *   <li>{@code retention-days} / {@code cleanup-*}：历史清理口径，清理巡检据此决定保留窗口与调度间隔。</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.property-history")
public class PropertyHistoryProperties {

    /** 写入总开关：{@code false} 时摄取旁路短路，不落库 */
    private boolean enabled = true;

    /** 单次查询时间跨度上限（天）；超出报 6225 */
    private int maxRangeDays = 31;

    /** 序列数（设备 × 属性）上限；超出报 6225 */
    private int maxSeries = 20;

    /** 单序列数据点（桶）数上限；超出报 6227 */
    private int maxPoints = 500;

    /** 历史保留天数；早于 {@code now - retention-days} 的记录被清理 */
    private int retentionDays = 30;

    /** 保留清理开关；{@code false} 时巡检不注册 */
    private boolean cleanupEnabled = true;

    /** 清理调度间隔（毫秒）；{@code <= 0} 时巡检不注册 */
    private long cleanupIntervalMs = 3600000L;

    /** 单批清理条数；分批删除避免长事务锁表 */
    private int cleanupBatchSize = 1000;
}
