package com.mqtt.cloud.dto.request;

import lombok.Data;

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

    /** 页码，默认1 */
    private Integer pageNum = 1;

    /** 每页数量，默认10，最大100 */
    private Integer pageSize = 10;
}
