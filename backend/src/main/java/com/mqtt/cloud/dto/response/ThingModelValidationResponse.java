package com.mqtt.cloud.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 物模型校验响应：{@code valid} 为 false 时 {@code errors} 列出全部错误路径。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThingModelValidationResponse {

    private boolean valid;

    private List<Error> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private String path;
        private String message;
    }
}