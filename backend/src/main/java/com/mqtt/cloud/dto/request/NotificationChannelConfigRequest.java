package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 通知渠道配置请求（T-27 设计文档 §10.1）。
 * <p>
 * 参数校验统一在服务层完成（返回 {@code 6262} / {@code 6263} 等业务码），
 * 因此不挂 Bean Validation 注解，避免出现「校验失败返 400 而非设计业务码」的口径漂移。
 * <p>
 * {@code config} 的敏感字段（{@code password} / {@code secret} / {@code headers} 等）在更新时
 * 留空或回填脱敏串表示保留原值。
 */
@Data
public class NotificationChannelConfigRequest {

    /** 渠道类型：EMAIL / SMS */
    private String channelType;

    /** 渠道名称，单用户内唯一 */
    private String name;

    /** 按类型不同的配置对象 */
    private Map<String, Object> config;

    /** 订阅的事件名数组 */
    private List<String> events;

    /** 是否启用；为空按启用处理 */
    private Boolean isActive;
}