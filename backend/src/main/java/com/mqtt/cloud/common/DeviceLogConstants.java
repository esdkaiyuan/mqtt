package com.mqtt.cloud.common;

import java.util.Set;

/**
 * 设备日志共享常量（T-20 设计文档 §3 / §5 / §9）。
 * <p>
 * 集中「日志类型 / 关联角色 / 回执主题后缀 / 分页与跨度默认值 / 载荷关联键字段名」的字符串口径，
 * 避免 SQL 投影、服务层回填、前端筛选三处各自散落字面量而产生漂移。
 */
public final class DeviceLogConstants {

    private DeviceLogConstants() {
    }

    /** 日志类型：报文（来源表 {@code message}）。 */
    public static final String TYPE_MESSAGE = "MESSAGE";
    /** 日志类型：命令下发（来源表 {@code device_command_record}）。 */
    public static final String TYPE_COMMAND = "COMMAND";
    /** 日志类型：事件上报（来源表 {@code device_event_record}）。 */
    public static final String TYPE_EVENT = "EVENT";
    /** 日志类型：连接事件（来源表 {@code device_status_history}）。 */
    public static final String TYPE_STATUS = "STATUS";

    /** 全部日志类型：请求 {@code types} 过滤的白名单（非空时任一不在其中即视为非法）。 */
    public static final Set<String> SUPPORTED_TYPES =
            Set.of(TYPE_MESSAGE, TYPE_COMMAND, TYPE_EVENT, TYPE_STATUS);

    /** 关联角色：命令下发（请求侧）。 */
    public static final String ROLE_REQUEST = "REQUEST";
    /** 关联角色：命令回执（设备应答报文侧）。 */
    public static final String ROLE_REPLY = "REPLY";

    /** 命令回执主题后缀：{@code topic} 以此结尾的报文视为命令回执。 */
    public static final String TOPIC_REPLY_SUFFIX = "/reply";

    /** 默认页码。 */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页数量。 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 单页上限兜底值：配置缺失或非正时使用。 */
    public static final int MAX_PAGE_SIZE_FALLBACK = 100;
    /** 时间窗跨度上限兜底值（天）：配置缺失或非正时使用。 */
    public static final int DEFAULT_MAX_RANGE_DAYS = 31;

    /** 回执报文载荷中的关联键字段名（与 T-15 {@code CommandReplyService} 解析口径一致）。 */
    public static final String FIELD_ID = "id";
}
