package com.mqtt.cloud.dto.response;

/**
 * 属性代码生成结果（T-37）。
 *
 * @param target   设备端 {@code device} / 上位机端 {@code host}
 * @param language 语言标识，同时用于前端高亮
 * @param filename 建议文件名
 * @param content  代码原文
 */
public record DebugCodeResponse(String target, String language, String filename, String content) {
}
