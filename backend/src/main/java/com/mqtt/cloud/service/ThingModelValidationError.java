package com.mqtt.cloud.service;

/**
 * 物模型校验的单条错误：{@code path} 为出错位置（如 {@code properties[0].identifier}），
 * {@code message} 为可读原因。见 T-14 设计文档 §5。
 */
public record ThingModelValidationError(String path, String message) {
}