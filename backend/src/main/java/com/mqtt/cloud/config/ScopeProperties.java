package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 规则与告警作用域配置（T-28 设计文档 §11）。
 * <p>
 * 前缀 {@code app.scope}，字段带默认值，风格对齐 {@link AlertProperties} / {@link RuleProperties}。
 * 该配置跨「消息规则」与「告警规则」两域共享，故不挂到任一域前缀下。
 * <p>
 * 不设独立总开关：作用域是既有能力的粒度扩展，迁移只加列，应用回滚后新列被忽略，
 * 存量规则按回填的 {@code ALL} / {@code DEVICE} 取值继续按旧语义运行。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.scope")
public class ScopeProperties {

    /** 单条规则 / 告警作用域内分组或标签的数量上限，超出按对应错误码拒绝 */
    private int maxTargets = 50;
}