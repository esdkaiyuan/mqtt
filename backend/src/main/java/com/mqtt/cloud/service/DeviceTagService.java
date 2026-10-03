package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.DeviceTagRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceTag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设备标签服务（T-18 设计文档 §10.2）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 标签为用户私有的扁平维度（无层级）；删除标签会自动解除其全部设备关联。
 */
public interface DeviceTagService {

    /** 创建标签：同一用户下不重名、颜色格式校验。 */
    DeviceTag create(Long userId, DeviceTagRequest request);

    /** 更新标签：重命名 / 改色。 */
    DeviceTag update(Long userId, Long id, DeviceTagRequest request);

    /** 删除标签：先解除全部设备关联，再逻辑删除。 */
    void delete(Long userId, Long id);

    /** 本人标签列表。 */
    List<DeviceTag> list(Long userId);

    /** 取本人标签，不存在 {@code 6214}，越权 {@code 403}。 */
    DeviceTag getOwned(Long userId, Long id);

    /** 单台 / 批量打标签（设备归属校验，重复关联幂等）。 */
    void addDevices(Long userId, Long tagId, List<Long> deviceIds);

    /** 单台 / 批量去标签。 */
    void removeDevices(Long userId, Long tagId, List<Long> deviceIds);

    /** 标签内设备分页。 */
    IPage<Device> pageDevices(Long userId, Long tagId, long page, long size);

    /** 按设备集合批量装配标签（设备列表用，一次查询避免 N+1）。 */
    Map<Long, List<DeviceTag>> tagsByDeviceIds(Collection<Long> deviceIds);

    /** 按标签集合取关联设备 ID（批量目标解析用）。 */
    List<Long> deviceIdsByTagIds(Collection<Long> tagIds);
}