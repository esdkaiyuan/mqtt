package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 告警升级策略请求（T-27 设计文档 §6.4 / §7）。
 * <p>
 * 参数校验统一在服务层完成（返回 {@code 6266}），因此不挂 Bean Validation 注解，
 * 避免出现「校验失败返 400 而非设计业务码」的口径漂移。
 */
@Data
public class AlertEscalationPolicyRequest {

    /** 策略名称 */
    private String name;

    /** 适用级别：ALL / INFO / WARNING / CRITICAL */
    private String severity;

    /** 未确认时长阈值（秒），须不小于 60 */
    private Integer afterSeconds;

    /** 升级时指派的处理人用户ID，可为空 */
    private Long assigneeId;

    /** 升级时额外通知的渠道ID数组，可为空 */
    private List<Long> channelIds;

    /** 是否启用；为空按启用处理 */
    private Boolean enabled;
}