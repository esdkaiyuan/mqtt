package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 看板保存 DTO（T-21 设计文档 §6.2）。
 * <p>
 * {@code config} 为原始 JSON 字符串（结构见设计文档 §5.3），由服务层用 {@code ObjectMapper}
 * 解析为 {@code DashboardConfig} 后做白名单校验，避免绑定层与校验层耦合。
 */
@Data
public class DashboardSaveDTO {

    /** 看板名称，非空且 ≤ 64 字符。 */
    private String name;

    /** 看板配置原文（面板数组 JSON 字符串）。 */
    private String config;
}
