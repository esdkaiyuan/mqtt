package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.DeviceGroupMemberRequest;
import com.mqtt.cloud.dto.request.DeviceGroupRequest;
import com.mqtt.cloud.dto.response.DeviceGroupMemberVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceGroup;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设备分组服务（T-18 设计文档 §10.1）。
 * <p>
 * 边界约定（ArchUnit 规则 4）：本接口不得依赖 {@code mapper}，持久化细节只出现在 {@code service.impl}。
 * <p>
 * 分组为用户私有的一棵自引用树；「按分组筛选」与「按分组批量」均**包含其所有后代分组**。
 */
public interface DeviceGroupService {

    /** 创建分组：同级不重名、父分组归属校验、深度上限校验。 */
    DeviceGroup create(Long userId, DeviceGroupRequest request);

    /** 更新分组：重命名 / 描述 / 排序 / 移动父节点（成环与深度校验）。 */
    DeviceGroup update(Long userId, Long id, DeviceGroupRequest request);

    /** 删除分组：仅空分组（无子分组且无关联设备）可删，否则 {@code 6216}。 */
    void delete(Long userId, Long id);

    /** 本人分组树（含 {@code children} 与直接关联设备数 {@code deviceCount}）。 */
    default List<DeviceGroup> tree(Long userId) {
        return tree(userId, false);
    }

    /** 分组树；{@code includeShared=true} 时合并被共享的分组子树（仅分组管理页使用）。 */
    List<DeviceGroup> tree(Long userId, boolean includeShared);

    /** 本人分组平铺列表（下拉 / 选择器用）。 */
    default List<DeviceGroup> list(Long userId) {
        return list(userId, false);
    }

    /** 分组平铺列表；{@code includeShared=true} 时包含被共享分组。 */
    List<DeviceGroup> list(Long userId, boolean includeShared);

    /** 取本人分组，不存在 {@code 6212}，越权 {@code 403}。 */
    DeviceGroup getOwned(Long userId, Long id);

    /** 取可访问分组（所有者或共享成员）；无权 {@code 6310}，不存在 {@code 6212}。 */
    DeviceGroup getAccessible(Long userId, Long id);

    /** 分组成员列表（仅所有者）。 */
    List<DeviceGroupMemberVO> listMembers(Long userId, Long groupId);

    /** 添加成员（仅所有者；用户名 / 邮箱查找；重复添加幂等更新权限）。 */
    DeviceGroupMemberVO addMember(Long userId, Long groupId, DeviceGroupMemberRequest request);

    /** 修改成员权限（仅所有者）。 */
    DeviceGroupMemberVO updateMember(Long userId, Long groupId, Long memberId, DeviceGroupMemberRequest request);

    /** 移除成员（仅所有者）。 */
    void removeMember(Long userId, Long groupId, Long memberId);

    /** 某分组及其所有后代分组 ID（含自身），供筛选与批量共用。 */
    List<Long> descendantIds(Long userId, Long id);

    /** 校验并解析「按分组筛选」的分组 ID 集合（含后代）。 */
    List<Long> resolveFilterGroupIds(Long userId, Long groupId);

    /** 单台 / 批量加入分组（设备归属校验，重复关联幂等）。 */
    void addDevices(Long userId, Long groupId, List<Long> deviceIds);

    /** 单台 / 批量移出分组。 */
    void removeDevices(Long userId, Long groupId, List<Long> deviceIds);

    /** 分组（含子分组）内设备分页。 */
    IPage<Device> pageDevices(Long userId, Long groupId, long page, long size);

    /** 按设备集合批量装配分组（设备列表用，一次查询避免 N+1）。 */
    Map<Long, List<DeviceGroup>> groupsByDeviceIds(Collection<Long> deviceIds);

    /** 按设备集合取设备所属分组 ID（批量目标解析用）。 */
    List<Long> deviceIdsByGroupIds(Collection<Long> groupIds);
}