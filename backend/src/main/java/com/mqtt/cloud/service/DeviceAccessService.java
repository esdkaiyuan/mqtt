package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.DeviceGroup;

import java.util.List;

/**
 * 分组级权限与设备访问判定（T-48）。
 * <p>
 * 所有者始终可读写；被共享用户按成员权限获得 {@code VIEW} / {@code OPERATE}。
 * 共享权限沿分组祖先链继承：在祖先分组上授权，子孙分组自动获得。
 */
public interface DeviceAccessService {

    /**
     * 解析分组访问级别。
     *
     * @return {@code OWNER} / {@code OPERATE} / {@code VIEW} / {@code NONE}
     */
    String resolveGroupLevel(Long userId, Long groupId);

    /** 解析分组访问权限（含分组本身，供共享页展示）。 */
    GroupAccess resolveGroup(Long userId, Long groupId);

    /**
     * 解析设备访问级别：所有者 / ADMIN / 共享分组（取最高权限）/ 无权。
     */
    String resolveDeviceLevel(Long userId, String role, Long deviceId);

    boolean canViewDevice(Long userId, String role, Long deviceId);

    boolean canOperateDevice(Long userId, String role, Long deviceId);

    /** 当前用户被共享的全部分组（含子孙），已标记 {@code shared} 与 {@code sharedPermission}。 */
    List<DeviceGroup> sharedGroups(Long userId);

    /** 分组 + 访问级别。 */
    record GroupAccess(DeviceGroup group, String level) {
    }
}
