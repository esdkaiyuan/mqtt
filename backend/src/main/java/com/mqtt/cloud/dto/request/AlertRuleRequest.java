package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 告警规则创建 / 更新请求（T-17 设计文档 §7.1）。
 * <p>
 * 字段均允许为空并在服务层集中校验：非法配置统一抛 {@code 6208}，
 * 因此这里不做 Bean Validation 注解，避免校验错误码被全局处理器改写为 {@code 400}。
 * <ul>
 *   <li>{@code THRESHOLD}：{@code identifier} / {@code operator} / {@code thresholdValue} 必填</li>
 *   <li>{@code OFFLINE}：仅用 {@code offlineSeconds}（缺省 0）</li>
 *   <li>{@code EVENT}：{@code identifier} 必填，{@code eventType} 可选</li>
 * </ul>
 */
@Data
public class AlertRuleRequest {

    /** 规则名称，非空，长度 ≤ 64。 */
    private String name;

    /** 来源类型：THRESHOLD / OFFLINE / EVENT，必填。 */
    private String sourceType;

    /** 级别：INFO / WARNING / CRITICAL，缺省 WARNING。 */
    private String severity;

    /** 作用设备ID，可空表示全部设备；非空时必须属于当前用户。 */
    private Long deviceId;

    /** 属性 / 事件标识符，THRESHOLD / EVENT 必填，长度 ≤ 64。 */
    private String identifier;

    /** 比较符：GT / GTE / LT / LTE / EQ / NE，THRESHOLD 必填。 */
    private String operator;

    /** 阈值（归一化文本），THRESHOLD 必填；数值比较符下必须是可解析的十进制数。 */
    private String thresholdValue;

    /** 事件类型过滤：info / alert / fault，EVENT 可选。 */
    private String eventType;

    /** 离线持续阈值（秒），OFFLINE 用，缺省 0（状态一变 OFFLINE 即告警），取值 [0, 86400]。 */
    private Integer offlineSeconds;

    /** 抑制窗口（秒），缺省 0 表示用全局默认，取值 [0, 86400]。 */
    private Integer suppressWindowSeconds;

    /** 是否启用：1 / 0，缺省 1。 */
    private Integer enabled;
}
