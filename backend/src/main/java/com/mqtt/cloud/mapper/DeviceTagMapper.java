package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备标签 Mapper（T-18 设计文档 §6.2）。
 * <p>
 * 自定义查询均显式带 {@code deleted = 0}（逻辑删除不走 BaseMapper 的自动条件）。
 */
@Mapper
public interface DeviceTagMapper extends BaseMapper<DeviceTag> {

    /** 某用户全部标签。 */
    List<DeviceTag> selectByUser(@Param("userId") Long userId);

    /**
     * 重名校验：同一用户下的未删除标签数。
     *
     * @param excludeId 排除的标签 ID（更新时排除自身），可空
     */
    int countByUserAndName(@Param("userId") Long userId,
                           @Param("name") String name,
                           @Param("excludeId") Long excludeId);
}