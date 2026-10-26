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
    RULE_LIMIT_EXCEEDED(6222, "规则数量超出上限", HttpStatus.BAD_REQUEST),

    DEVICE_LOG_RANGE_INVALID(6223, "日志查询时间范围非法或超出上限", HttpStatus.BAD_REQUEST),
    DEVICE_LOG_TYPE_UNSUPPORTED(6224, "不支持的日志类型", HttpStatus.BAD_REQUEST),

    PROPERTY_HISTORY_RANGE_INVALID(6225, "属性时序查询时间范围非法或超出上限", HttpStatus.BAD_REQUEST),
    PROPERTY_HISTORY_IDENTIFIER_UNSUPPORTED(6226, "属性标识符不存在或未在物模型中定义", HttpStatus.BAD_REQUEST),
    PROPERTY_HISTORY_BUCKET_UNSUPPORTED(6227, "不支持的时间桶粒度或数据点过多", HttpStatus.BAD_REQUEST),
    DASHBOARD_NOT_FOUND(6228, "看板不存在", HttpStatus.NOT_FOUND),
    DASHBOARD_INVALID(6229, "看板配置非法", HttpStatus.BAD_REQUEST),
    DASHBOARD_LIMIT_EXCEEDED(6230, "看板数量超出上限", HttpStatus.BAD_REQUEST),

    OTA_FIRMWARE_NOT_FOUND(6231, "固件包不存在", HttpStatus.NOT_FOUND),
    OTA_FIRMWARE_INVALID(6232, "固件包参数非法（版本格式、文件缺失或超出大小上限）", HttpStatus.BAD_REQUEST),
    OTA_FIRMWARE_DUPLICATE(6233, "同产品下该固件版本已存在", HttpStatus.BAD_REQUEST),
    OTA_FIRMWARE_IN_USE(6234, "固件包已被升级任务引用，无法删除", HttpStatus.BAD_REQUEST),
    OTA_UPLOAD_FAILED(6235, "固件文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR),
    OTA_TASK_NOT_FOUND(6236, "升级任务不存在", HttpStatus.NOT_FOUND),
    OTA_TASK_INVALID(6237, "升级任务参数非法（目标为空、超限或与固件产品不匹配）", HttpStatus.BAD_REQUEST),
    OTA_TASK_STATE_INVALID(6238, "升级任务状态不允许该操作", HttpStatus.BAD_REQUEST),
    OTA_PROGRESS_INVALID(6239, "OTA 进度回传报文字段非法", HttpStatus.BAD_REQUEST),

    SCENE_NOT_FOUND(6240, "场景不存在", HttpStatus.NOT_FOUND),
    SCENE_INVALID(6241, "场景配置非法", HttpStatus.BAD_REQUEST),
    SCENE_STEP_UNSUPPORTED(6242, "不支持的场景步骤动作类型", HttpStatus.BAD_REQUEST),
    SCENE_EXECUTION_NOT_FOUND(6243, "场景执行记录不存在", HttpStatus.NOT_FOUND),
    SCENE_LIMIT_EXCEEDED(6244, "场景数量或步骤数超出上限", HttpStatus.BAD_REQUEST),
    SCENE_TRIGGER_UNSUPPORTED(6245, "不支持或非法的场景触发源", HttpStatus.BAD_REQUEST),

    DEVICE_CREDENTIAL_INVALID(6246, "设备凭据缺失或非法", HttpStatus.UNAUTHORIZED),
    INGEST_MESSAGE_TYPE_UNSUPPORTED(6247, "不支持的 HTTP 上报消息类型", HttpStatus.BAD_REQUEST),
    INGEST_PAYLOAD_TOO_LARGE(6248, "HTTP 上报载荷超出上限", HttpStatus.PAYLOAD_TOO_LARGE),

    AUTH_MODE_UNSUPPORTED(6249, "不支持的认证方式", HttpStatus.BAD_REQUEST),
    TOPIC_PREFIX_TEMPLATE_INVALID(6250, "主题前缀模板非法", HttpStatus.BAD_REQUEST),
    DEVICE_CERTIFICATE_ISSUE_FAILED(6251, "设备证书签发失败", HttpStatus.INTERNAL_SERVER_ERROR),
    DEVICE_CERTIFICATE_REVOKE_FAILED(6252, "设备证书吊销失败", HttpStatus.BAD_REQUEST),
    DEVICE_TOKEN_ISSUE_FAILED(6253, "设备令牌签发失败", HttpStatus.INTERNAL_SERVER_ERROR),

    DEVICE_TOPOLOGY_INVALID(6254, "设备拓扑配置非法", HttpStatus.BAD_REQUEST),
    DEVICE_PARENT_NOT_FOUND(6255, "父网关设备不存在", HttpStatus.NOT_FOUND),
    DEVICE_PARENT_KIND_INVALID(6256, "父设备不是网关，无法挂载子设备", HttpStatus.BAD_REQUEST),
    DEVICE_TOPOLOGY_DEPTH_EXCEEDED(6257, "设备拓扑层级超出上限", HttpStatus.BAD_REQUEST),
    DEVICE_PRODUCT_MISMATCH(6258, "子设备与网关不属于同一产品", HttpStatus.BAD_REQUEST),
    DEVICE_HAS_SUBDEVICES(6259, "网关下存在子设备，无法执行该操作", HttpStatus.CONFLICT),
    DEVICE_SUBDEVICE_LIMIT_EXCEEDED(6260, "网关子设备数量超出上限", HttpStatus.BAD_REQUEST),

    NOTIFICATION_CHANNEL_NOT_FOUND(6261, "通知渠道不存在", HttpStatus.NOT_FOUND),
    NOTIFICATION_CHANNEL_INVALID(6262, "通知渠道参数非法", HttpStatus.BAD_REQUEST),
    NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED(6263, "不支持的通知渠道类型或渠道数超限", HttpStatus.BAD_REQUEST),
    NOTIFICATION_SEND_FAILED(6264, "通知测试发送失败", HttpStatus.BAD_REQUEST),
    ESCALATION_POLICY_NOT_FOUND(6265, "告警升级策略不存在", HttpStatus.NOT_FOUND),
    ESCALATION_POLICY_INVALID(6266, "告警升级策略参数非法", HttpStatus.BAD_REQUEST),
    ALERT_ASSIGNEE_INVALID(6267, "指派处理人非法", HttpStatus.BAD_REQUEST),

    ALERT_RULE_SCOPE_INVALID(6268, "告警规则作用域非法（档位、字段一致性或数量超限）", HttpStatus.BAD_REQUEST),
    RULE_SCOPE_INVALID(6269, "消息规则作用域非法（档位、字段一致性或数量超限）", HttpStatus.BAD_REQUEST),

    // ── T-30 · 批量注册与设备激活 ──
    REG_BATCH_NOT_FOUND(6270, "注册批次不存在", HttpStatus.NOT_FOUND),
    REG_BATCH_COUNT_INVALID(6271, "批量注册数量非法（须为 1~1000）", HttpStatus.BAD_REQUEST),
    REG_BATCH_PATTERN_INVALID(6272, "deviceKey 生成模板非法（仅允许字母数字中划线下划线与 {index}）", HttpStatus.BAD_REQUEST),
    DEVICE_ACTIVATE_CODE_INVALID(6273, "设备激活码非法或已被使用", HttpStatus.BAD_REQUEST),
    DEVICE_ACTIVATE_CODE_EXPIRED(6274, "设备激活码已过期", HttpStatus.BAD_REQUEST),

    // ── T-31 · 点位表 ──
    POINT_KEY_DUPLICATED(6280, "点位标识在该设备下已存在", HttpStatus.BAD_REQUEST),
    POINT_EXECUTOR_MIXED(6281, "同一设备下的点位执行方必须一致（EDGE 与 PLATFORM 不可混用）", HttpStatus.BAD_REQUEST),
    POINT_EXECUTOR_UNSUPPORTED(6282, "点位执行方非法（仅支持 EDGE / PLATFORM）", HttpStatus.BAD_REQUEST),
    POINT_TYPE_UNSUPPORTED(6283, "点位类型非法", HttpStatus.BAD_REQUEST),
    POINT_ADDRESS_REQUIRED(6284, "寄存器类点位必须填写地址", HttpStatus.BAD_REQUEST),
    POINT_ADDRESS_INVALID(6285, "点位地址非法（寄存器号须为非负整数）", HttpStatus.BAD_REQUEST),
    POINT_INTERVAL_INVALID(6286, "采集周期非法", HttpStatus.BAD_REQUEST),
    POINT_SCALE_INVALID(6287, "换算系数非法（scale 不可为 0，offset 为偏移量）", HttpStatus.BAD_REQUEST),
    POINT_DATA_TYPE_INVALID(6288, "点位数据类型非法", HttpStatus.BAD_REQUEST),
    POINT_BATCH_EMPTY(6289, "点位批量保存内容为空", HttpStatus.BAD_REQUEST),
    POINT_DISPATCH_NOT_EDGE(6290, "仅边缘执行方（EDGE）的点表可下发；PLATFORM 点表由平台自行采集，无需下发", HttpStatus.BAD_REQUEST),
    POINT_DISPATCH_NO_POINTS(6291, "该设备没有启用中的边缘点位可下发", HttpStatus.BAD_REQUEST),
    POINT_DISPATCH_DEVICE_INVALID(6292, "设备不可下发点表（不存在 / 已禁用 / 接入协议不支持边缘驱动）", HttpStatus.BAD_REQUEST),

    // ── T-32 · OTA 灰度与固件校验 ──
    /** G-11-4：目标设备全部已在别的升级任务中，互斥后无设备可下发。 */
    OTA_DEVICE_CONFLICT(6293, "目标设备全部已被其他升级任务占用", HttpStatus.BAD_REQUEST),
    /** G-11-1：灰度参数非法（批次大小、批次间隔越界或非 BATCHED 策略下携带灰度参数）。 */
    OTA_BATCH_PARAM_INVALID(6294, "灰度批次参数非法", HttpStatus.BAD_REQUEST),

    // ── T-34 · 命令级权限与调用配额 ──
    /** 危险命令标识符：命中全局黑名单且该角色未豁免。 */
    COMMAND_IDENTIFIER_DENIED(6295, "当前角色无权下发该命令", HttpStatus.FORBIDDEN),
    /** 命令类型不在该角色的允许矩阵内。 */
    COMMAND_TYPE_DENIED(6296, "当前角色无权下发该类型的命令", HttpStatus.FORBIDDEN),

    // ── T-30 · 限流 ──
    /** 开放接口按 API Key 维度限流。文档（apiEndpoints.js）早已宣称 429，此处补齐后端实现。 */
    RATE_LIMIT_EXCEEDED(4002, "超出密钥的调用频率限制", HttpStatus.TOO_MANY_REQUESTS),
    /** 命令调用配额超限（T-34），按操作者维度而非 API Key 维度。 */
    COMMAND_QUOTA_EXCEEDED(4291, "命令调用超出配额", HttpStatus.TOO_MANY_REQUESTS),

    // ── T-37 · 设备调试中心 ──
    /** 模拟上行的消息类型不在 data / heartbeat / lwt / reply / ota 内。 */
    DEBUG_MESSAGE_TYPE_UNSUPPORTED(6297, "调试消息类型非法（仅支持 data / heartbeat / lwt / reply / ota）", HttpStatus.BAD_REQUEST),
    /** data / reply / ota 的载荷必须是 JSON 对象，或超出大小上限。 */
    DEBUG_PAYLOAD_INVALID(6298, "调试载荷非法（须为 JSON 对象且不超过大小上限）", HttpStatus.BAD_REQUEST),
    /** 代码生成的目标端或语言不在白名单内。 */
    DEBUG_CODE_TARGET_UNSUPPORTED(6299, "代码生成目标或语言不支持", HttpStatus.BAD_REQUEST),

    // ── T-38 · 快速上位机（Web 组态） ──
    HMI_VIEW_NOT_FOUND(6300, "上位机不存在或无权访问", HttpStatus.NOT_FOUND),
    HMI_VIEW_LIMIT_EXCEEDED(6301, "上位机数量已达上限", HttpStatus.BAD_REQUEST),
    HMI_CONFIG_INVALID(6302, "上位机配置非法", HttpStatus.BAD_REQUEST),
    HMI_BINDING_INVALID(6303, "上位机控件绑定非法（属性 / 服务不存在、类型或可写性不匹配）", HttpStatus.BAD_REQUEST),
    HMI_WIDGET_TYPE_UNSUPPORTED(6304, "上位机控件类型非法", HttpStatus.BAD_REQUEST),

    // ── T-46 · 看板大屏与跨用户分享 ──
    DASHBOARD_SHARE_NOT_FOUND(6305, "看板分享不存在或已失效", HttpStatus.NOT_FOUND),
    DASHBOARD_SHARE_INVALID(6306, "看板分享配置非法", HttpStatus.BAD_REQUEST),
    DASHBOARD_SHARE_LIMIT_EXCEEDED(6307, "看板分享数量超出上限", HttpStatus.BAD_REQUEST),

    // ── T-48 · 分组级权限共享 ──
    DEVICE_GROUP_MEMBER_INVALID(6308, "分组成员或权限非法", HttpStatus.BAD_REQUEST),
    DEVICE_GROUP_MEMBER_NOT_FOUND(6309, "分组成员不存在", HttpStatus.NOT_FOUND),
    DEVICE_GROUP_ACCESS_DENIED(6310, "无权访问该分组或设备", HttpStatus.FORBIDDEN),

    // ── T-50 · Modbus 多从站与 64 位整型 ──
    POINT_UNIT_ID_INVALID(6311, "Modbus 从站地址非法（须在 0~247，留空表示继承驱动配置）", HttpStatus.BAD_REQUEST),

    // ── T-51 · 场景版本回滚 ──
    SCENE_VERSION_NOT_FOUND(6312, "场景版本不存在或不属于该场景", HttpStatus.NOT_FOUND),

    // ── T-30 · 死信处置闭环 ──
    DEAD_LETTER_NOT_FOUND(5002, "死信记录不存在", HttpStatus.NOT_FOUND),
    DEAD_LETTER_ALREADY_DISPOSED(5003, "死信已被处置（已重放或已丢弃）", HttpStatus.CONFLICT),
    DEAD_LETTER_REPLAY_FAILED(5004, "死信重放入队失败", HttpStatus.SERVICE_UNAVAILABLE),

    // ── T-53 · 平台治理与规模化补强 ──
    /** 审计日志查询时间范围非法（格式错误或 start 晚于 end）。 */
    AUDIT_LOG_QUERY_INVALID(6313, "审计日志查询条件非法（时间格式错误或起始晚于结束）", HttpStatus.BAD_REQUEST),
    /** HTTP 上报按设备固定窗口限流。 */
    INGEST_RATE_LIMIT_EXCEEDED(6314, "HTTP 上报超出设备频率限制", HttpStatus.TOO_MANY_REQUESTS),
    /** 幂等键仅允许 1~128 个可见 ASCII 字符。 */
    INGEST_IDEMPOTENCY_KEY_INVALID(6315, "幂等键非法（长度 1~128，仅允许可见 ASCII）", HttpStatus.BAD_REQUEST),
    /** 设备导入文件格式 / 表头 / 行数 / 大小不合法。 */
    DEVICE_IMPORT_INVALID(6316, "设备导入文件非法（格式、表头、行数或大小超限）", HttpStatus.BAD_REQUEST),

    // ── T-55 · Payload Codec ──
    /** Payload Codec 不是合法模板（JSON、版本、mode、template、路径或大小超限）。 */
    PAYLOAD_CODEC_INVALID(6317, "Payload Codec 配置非法（JSON、template 或路径表达式不合法）", HttpStatus.BAD_REQUEST),

    // ── T-58 · TOTP MFA ──
    /** 用户已启用 MFA，不能重复发起绑定。 */
    MFA_ALREADY_ENABLED(6318, "已启用多因素认证，无需重复绑定", HttpStatus.BAD_REQUEST),
    /** 尚未完成 MFA 绑定（没有待确认密钥）。 */
    MFA_NOT_ENROLLED(6319, "尚未完成多因素认证绑定", HttpStatus.BAD_REQUEST),
    /** 动态口令错误或已过期。 */
    MFA_CODE_INVALID(6320, "动态口令错误或已过期", HttpStatus.BAD_REQUEST),
    /** MFA 临时令牌非法 / 过期 / 非 MFA 用途。 */
    MFA_TOKEN_INVALID(6321, "多因素认证会话无效，请重新登录", HttpStatus.UNAUTHORIZED),
    /** MFA 功能整体关闭。 */
    MFA_DISABLED(6322, "多因素认证功能已关闭", HttpStatus.NOT_FOUND),

    // ── T-56 · 数据桥接 ──
    /** Kafka 数据桥接未启用 / 配置缺失 / 发送失败。 */
    DATA_BRIDGE_FAILED(6323, "数据桥接未启用、配置缺失或发送失败", HttpStatus.BAD_GATEWAY);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}