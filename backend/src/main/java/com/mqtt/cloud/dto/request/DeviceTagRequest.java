package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 设备标签创建 / 更新请求（T-18 设计文档 §7.2）。
 * <p>
 * 字段允许为空并在服务层集中校验：非法配置统一抛 {@code 6215}。
 */
@Data
public class DeviceTagRequest {

    /** 标签名称，非空，去空白后长度 1~64，同一用户下不重名。 */
    private String name;

    /** 展示色，可空；非空时匹配 {@code ^#[0-9A-Fa-f]{6}$}。 */
    private String color;
}