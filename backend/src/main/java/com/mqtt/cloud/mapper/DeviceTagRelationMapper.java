package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 设备-标签关联 Mapper（T-18 设计文档 §6.2）。
 * <p>
 * 关联表为物理行：新增用 {@code INSERT IGNORE} 幂等，解除用物理删除。
 */
@Mapper
public interface DeviceTagRelationMapper extends BaseMapper<DeviceTagRelation> {

    /** 批量建立关联（唯一键去重，重复关联幂等成功）。返回实际新增行数。 */
    int insertIgnoreBatch(@Param("deviceIds") Collection<Long> deviceIds,
                          @Param("tagId") Long tagId,
                          @Param("createdAt") LocalDateTime createdAt);

    /** 解除「指定设备 × 指定标签」的关联。返回受影响行数。 */
    int deleteByDeviceIdsAndTag(@Param("deviceIds") Collection<Long> deviceIds,
                                @Param("tagId") Long tagId);

    /** 解除指定设备的全部标签关联（删除设备时兜底；物理删除由外键级联负责）。 */
    int deleteByDeviceIds(@Param("deviceIds") Collection<Long> deviceIds);

    /** 解除某标签的全部设备关联（删除标签时调用）。 */
    int deleteByTagId(@Param("tagId") Long tagId);

    /** 按标签集合取关联设备 ID（去重）。 */
    List<Long> selectDeviceIdsByTagIds(@Param("tagIds") Collection<Long> tagIds);

    /** 按设备集合取全部关联行，供设备列表批量装配。 */
    List<DeviceTagRelation> selectByDeviceIds(@Param("deviceIds") Collection<Long> deviceIds);

    /** 某标签关联的设备数。 */
    int countByTagId(@Param("tagId") Long tagId);
}