package com.mqtt.cloud.common.exception;

import com.mqtt.cloud.common.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 * <p>
 * 由业务逻辑主动抛出，携带业务码与对应的 HTTP 状态码，
 * 由 {@link GlobalExceptionHandler} 统一转换为标准 Result 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;
    private final Integer code;
    private final HttpStatus httpStatus;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    /**
     * 使用标准 ResultCode 的业务码与 HTTP 状态，但自定义提示信息。
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    /**
     * 使用标准 ResultCode 的业务码，但覆盖 HTTP 状态码与提示信息。
     * <p>
     * 用于「业务码复用、HTTP 状态需区分」的场景（如手动重试遇到状态冲突时复用
     * {@code RULE_INVALID} 业务码但返回 {@code 409}，不新增同义错误码）。
     */
    public BusinessException(ResultCode resultCode, HttpStatus httpStatus, String message) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.httpStatus = httpStatus;
    }

    /**
     * 兜底构造：按参数错误处理。
     */
    public BusinessException(String message) {
        this(ResultCode.BAD_REQUEST, message);
    }
}