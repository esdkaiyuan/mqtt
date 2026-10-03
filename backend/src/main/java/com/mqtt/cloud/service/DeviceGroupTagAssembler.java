package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;
import com.mqtt.cloud.entity.DeviceTag;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设备列表的分组 / 标签批量装配（T-18 设计文档 §10.4）。
 * <p>
 * 一页设备只做两次关联查询（分组、标签各一次），避免逐台 N+1；
 * 设备列表、分组内设备、标签内设备三处共用，保证投影一致。
 * <p>
 * 只依赖 {@link DeviceGroupService} / {@link DeviceTagService}，不触碰 {@code mapper}，
 * 满足 ArchUnit「服务层不得越界」约束。
 */
@Component
public class DeviceGroupTagAssembler {

    private final DeviceGroupService deviceGroupService;
    private final DeviceTagService deviceTagService;

    public DeviceGroupTagAssembler(DeviceGroupService deviceGroupService, DeviceTagService deviceTagService) {
        this.deviceGroupService = deviceGroupService;
        this.deviceTagService = deviceTagService;
    }

    /** 就地填充每台设备的 {@code groups} / {@code tags}（空集合而非 null，便于前端直接渲染）。 */
    public void assemble(Collection<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        List<Long> deviceIds = devices.stream().map(Device::getId).toList();
        Map<Long, List<DeviceGroup>> groups = deviceGroupService.groupsByDeviceIds(deviceIds);
        Map<Long, List<DeviceTag>> tags = deviceTagService.tagsByDeviceIds(deviceIds);
        for (Device device : devices) {
            device.setGroups(groups.getOrDefault(device.getId(), List.of()));
            device.setTags(tags.getOrDefault(device.getId(), List.of()));
        }
    }
}