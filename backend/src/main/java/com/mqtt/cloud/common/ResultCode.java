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

    WEBHOOK_TEST_FAILED(5001, "Webhook 测试失败", HttpStatus.BAD_GATEWAY),

    PRODUCT_KEY_EXISTS(6001, "产品标识已存在", HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(6002, "产品不存在", HttpStatus.NOT_FOUND),
    PRODUCT_HAS_DEVICES(6003, "该产品下存在设备，无法删除", HttpStatus.CONFLICT),
    PRODUCT_DISABLED(6004, "产品已停用", HttpStatus.CONFLICT),
    DEVICE_DISABLED(6005, "设备已被禁用", HttpStatus.FORBIDDEN),
    INTERNAL_TOKEN_INVALID(6006, "内部调用令牌无效", HttpStatus.UNAUTHORIZED),

    THING_MODEL_PARSE_FAILED(6101, "物模型 JSON 解析失败", HttpStatus.BAD_REQUEST),
    THING_MODEL_INVALID(6102, "物模型校验不通过", HttpStatus.BAD_REQUEST),
    THING_MODEL_TOO_LARGE(6103, "物模型内容超过大小上限", HttpStatus.PAYLOAD_TOO_LARGE),

    COMMAND_MODEL_MISSING(6201, "产品未定义物模型，无法下发命令", HttpStatus.BAD_REQUEST),
    COMMAND_PROPERTY_READONLY(6202, "属性不可写", HttpStatus.BAD_REQUEST),
    COMMAND_IDENTIFIER_UNKNOWN(6203, "标识符未在物模型中定义", HttpStatus.BAD_REQUEST),
    COMMAND_PARAM_INVALID(6204, "参数不符合物模型定义", HttpStatus.BAD_REQUEST),
    COMMAND_NOT_FOUND(6205, "命令记录不存在", HttpStatus.NOT_FOUND),
    SHADOW_DESIRED_INVALID(6206, "影子期望状态非法", HttpStatus.BAD_REQUEST),

    ALERT_RULE_NOT_FOUND(6207, "告警规则不存在", HttpStatus.NOT_FOUND),
    ALERT_RULE_INVALID(6208, "告警规则配置非法", HttpStatus.BAD_REQUEST),
    ALERT_NOT_FOUND(6209, "告警记录不存在", HttpStatus.NOT_FOUND),
    ALERT_STATUS_INVALID(6210, "告警状态不允许该操作", HttpStatus.CONFLICT),
    ALERT_SOURCE_UNSUPPORTED(6211, "不支持的告警来源类型", HttpStatus.BAD_REQUEST),

    DEVICE_GROUP_NOT_FOUND(6212, "设备分组不存在", HttpStatus.NOT_FOUND),
    DEVICE_GROUP_INVALID(6213, "设备分组配置非法", HttpStatus.BAD_REQUEST),
    DEVICE_TAG_NOT_FOUND(6214, "设备标签不存在", HttpStatus.NOT_FOUND),
    DEVICE_TAG_INVALID(6215, "设备标签配置非法", HttpStatus.BAD_REQUEST),
    DEVICE_GROUP_NOT_EMPTY(6216, "分组下存在子分组或设备，无法删除", HttpStatus.CONFLICT),
    BATCH_TARGET_INVALID(6217, "批量操作目标非法（为空或超出上限）", HttpStatus.BAD_REQUEST),

    RULE_NOT_FOUND(6218, "规则不存在", HttpStatus.NOT_FOUND),
    RULE_INVALID(6219, "规则配置非法", HttpStatus.BAD_REQUEST),
    RULE_ACTION_UNSUPPORTED(6220, "不支持的规则动作类型", HttpStatus.BAD_REQUEST),
    RULE_EXECUTION_NOT_FOUND(6221, "规则执行记录不存在", HttpStatus.NOT_FOUND),
    RULE_LIMIT_EXCEEDED(6222, "规则数量超出上限", HttpStatus.BAD_REQUEST);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}