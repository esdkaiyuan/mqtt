package com.mqtt.cloud.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务状态码枚举
 * <p>
 * code 为业务码（返回体中的 code 字段），httpStatus 为对应的 HTTP 状态码。
 * 二者分离：HTTP 状态码供网关/客户端做通用判断，业务码供前端做精确分支。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success", HttpStatus.OK),

    BAD_REQUEST(400, "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未认证，请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "权限不足", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(500, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    USERNAME_EXISTS(1001, "用户名已存在", HttpStatus.CONFLICT),
    PHONE_EXISTS(1002, "手机号已注册", HttpStatus.CONFLICT),
    EMAIL_EXISTS(1003, "邮箱已注册", HttpStatus.CONFLICT),

    DEVICE_KEY_EXISTS(2001, "设备标识已存在", HttpStatus.CONFLICT),
    DEVICE_NOT_FOUND(2002, "设备不存在", HttpStatus.NOT_FOUND),
    DEVICE_NOT_OWNED(2003, "无权限操作此设备", HttpStatus.FORBIDDEN),

    INVALID_CREDENTIALS(3001, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(3002, "Token已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(3003, "无效的Token", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(3004, "账号已锁定，请联系管理员", HttpStatus.FORBIDDEN),

    PASSWORD_SAME_AS_OLD(3005, "新密码不能与旧密码相同", HttpStatus.BAD_REQUEST),

    API_KEY_INVALID(3501, "无效的API Key", HttpStatus.UNAUTHORIZED),

    MQTT_PUBLISH_FAILED(4001, "消息发送失败", HttpStatus.SERVICE_UNAVAILABLE),

    WEBHOOK_TEST_FAILED(5001, "Webhook 测试失败", HttpStatus.BAD_GATEWAY);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}