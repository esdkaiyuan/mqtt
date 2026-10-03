package com.mqtt.cloud.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 设备查询DTO（含分页）
 */
@Data
public class DeviceQueryDTO {

    /** 设备名称（模糊查询），选填 */
    private String deviceName;

    /** 设备类型，选填 */
    private String deviceType;

    /** 设备状态，选填：ONLINE/OFFLINE/INACTIVE */
    private String status;

    /** 所属用户ID，选填（管理员查询时可指定） */
    private Long ownerId;

    /** 设备分组ID（筛选含其所有子分组），选填 */
    private Long groupId;

    /** 设备标签ID，选填 */
    private Long tagId;

    /**
     * 服务端解析出的分组ID集合（分组自身 + 所有后代）。
     * <p>
     * 不对外暴露：客户端只传 {@link #groupId}，由控制器校验归属后解析填充，避免客户端伪造集合越权。
     */
    private List<Long> groupIds;

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100 */
    private Integer pageSize = 10;
}