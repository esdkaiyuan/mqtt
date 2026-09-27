package com.mqtt.cloud.common.exception;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 将 Controller 层抛出的异常统一转换为标准 Result 结构，
 * 同时把业务码映射为对应的 HTTP 状态码，避免"业务失败仍返回 200"。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        logger.warn("参数校验失败: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        logger.warn("参数绑定失败: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        logger.warn("约束校验失败: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        logger.warn("请求参数不合法: {}", e.getMessage());
        return badRequest("请求参数格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        logger.warn("请求方法不支持: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.BAD_REQUEST.getHttpStatus())
                .body(Result.error(ResultCode.BAD_REQUEST.getCode(), "请求方法不支持: " + e.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        logger.warn("资源不存在: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(Result.error(ResultCode.NOT_FOUND));
    }

    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class})
    public ResponseEntity<Result<Void>> handleAuthenticationFailed(Exception e) {
        logger.warn("认证失败: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.INVALID_CREDENTIALS.getHttpStatus())
                .body(Result.error(ResultCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        logger.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(ResultCode.FORBIDDEN.getHttpStatus())
                .body(Result.error(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        logger.error("系统异常", e);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.error(ResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(ResultCode.BAD_REQUEST.getHttpStatus())
                .body(Result.error(ResultCode.BAD_REQUEST.getCode(), message));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}