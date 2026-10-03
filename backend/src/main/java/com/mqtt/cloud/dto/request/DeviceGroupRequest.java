package com.mqtt.cloud.dto.request;

import lombok.Data;

/**
 * 设备分组创建 / 更新请求（T-18 设计文档 §7.1）。
 * <p>
 * 字段允许为空并在服务层集中校验：非法配置统一抛 {@code 6213}，
 * 避免 Bean Validation 的错误码被全局处理器改写为 {@code 400}。
 */
@Data
public class DeviceGroupRequest {

    /** 分组名称，非空，去空白后长度 1~64，同级不重名。 */
    private String name;

    /** 父分组 ID，可空表示根分组；非空时必须属于当前用户。 */
    private Long parentId;

    /** 同级排序（升序），可空缺省 0。 */
    private Integer sortOrder;

    /** 描述，可空，长度 ≤ 255。 */
    private String description;
}