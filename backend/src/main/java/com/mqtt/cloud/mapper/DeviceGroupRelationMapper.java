package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceGroupRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 设备-分组关联 Mapper（T-18 设计文档 §6.2）。
 * <p>
 * 关联表为物理行：新增用 {@code INSERT IGNORE} 幂等，解除用物理删除。
 */
@Mapper
public interface DeviceGroupRelationMapper extends BaseMapper<DeviceGroupRelation> {

    /** 批量建立关联（唯一键去重，重复关联幂等成功）。返回实际新增行数。 */
    int insertIgnoreBatch(@Param("deviceIds") Collection<Long> deviceIds,
                          @Param("groupId") Long groupId,
                          @Param("createdAt") LocalDateTime createdAt);

    /** 解除「指定设备 × 指定分组」的关联。返回受影响行数。 */
    int deleteByDeviceIdsAndGroup(@Param("deviceIds") Collection<Long> deviceIds,
                                  @Param("groupId") Long groupId);

    /** 解除指定设备的全部分组关联（删除设备时兜底；物理删除由外键级联负责）。 */
    int deleteByDeviceIds(@Param("deviceIds") Collection<Long> deviceIds);

    /** 解除某分组的全部设备关联（删除分组时关联必为空，仅作兜底）。 */
    int deleteByGroupId(@Param("groupId") Long groupId);

    /** 按分组集合取关联设备 ID（去重）。 */
    List<Long> selectDeviceIdsByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    /** 按设备集合取全部关联行，供设备列表批量装配。 */
    List<DeviceGroupRelation> selectByDeviceIds(@Param("deviceIds") Collection<Long> deviceIds);

    /** 某分组直接关联的设备数（删除前置校验）。 */
    int countByGroupId(@Param("groupId") Long groupId);

    /**
     * 批量取各分组直接关联的设备数（分组树一次性装配 {@code deviceCount}，避免逐节点查询）。
     * <p>
     * 返回每行含 {@code groupId} / {@code cnt} 两个键；调用方须保证 {@code groupIds} 非空。
     */
    List<Map<String, Object>> countByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}