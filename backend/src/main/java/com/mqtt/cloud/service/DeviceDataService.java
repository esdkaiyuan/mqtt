package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.entity.DeviceEventRecord;
import com.mqtt.cloud.entity.DevicePropertyLatest;

import java.util.List;

/**
 * 设备物模型数据只读查询（T-14 P5）。
 * <p>
 * 数据源是解析链路写入的两张派生表，本接口只读不回写；归属校验由控制层完成。
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}。
 */
public interface DeviceDataService {

    /** 设备属性最新值，按标识符升序。 */
    List<DevicePropertyLatest> getPropertyLatest(Long deviceId);

    /** 设备事件记录分页，按上报时间倒序（同一时间以自增 ID 兜底稳定排序）。 */
    IPage<DeviceEventRecord> getEvents(Long deviceId, long page, long size);
}