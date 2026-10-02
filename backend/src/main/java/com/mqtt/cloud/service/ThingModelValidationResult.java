package com.mqtt.cloud.service;

import java.util.List;

/**
 * 物模型校验结果。
 * <p>
 * {@code parseFailed} 与普通校验失败分开承载：前者对应错误码 6101（JSON 无法解析），
 * 后者对应 6102（结构合法但违反 V1~V16 规则）。两者都不通过时以 {@code parseFailed} 优先。
 */
public record ThingModelValidationResult(boolean parseFailed, List<ThingModelValidationError> errors) {

    public ThingModelValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ThingModelValidationResult ok() {
        return new ThingModelValidationResult(false, List.of());
    }

    public static ThingModelValidationResult invalid(List<ThingModelValidationError> errors) {
        return new ThingModelValidationResult(false, errors);
    }

    public static ThingModelValidationResult parseFailed(String message) {
        return new ThingModelValidationResult(true, List.of(new ThingModelValidationError("$", message)));
    }

    public boolean valid() {
        return !parseFailed && errors.isEmpty();
    }

    /** 首个错误，供保存接口拼装错误提示。 */
    public ThingModelValidationError firstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
}