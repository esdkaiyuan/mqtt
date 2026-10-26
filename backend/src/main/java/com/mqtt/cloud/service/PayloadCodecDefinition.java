package com.mqtt.cloud.service;

import tools.jackson.databind.JsonNode;

/**
 * Payload Codec 运行时定义（T-55）。
 * <p>
 * 只保存已校验的模板与 fallback 开关；{@link #EMPTY} 表示按原始 Alink 解析。
 * 模板是只读 JSON 节点，缓存后可跨线程复用。
 */
public record PayloadCodecDefinition(JsonNode template, boolean fallbackToRaw) {

    public static final PayloadCodecDefinition EMPTY = new PayloadCodecDefinition(null, true);

    public boolean enabled() {
        return template != null;
    }
}