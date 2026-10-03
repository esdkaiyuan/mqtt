package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备分组 Mapper（T-18 设计文档 §6.2）。
 * <p>
 * 自定义查询均显式带 {@code deleted = 0}（逻辑删除不走 BaseMapper 的自动条件）。
 */
@Mapper
public interface DeviceGroupMapper extends BaseMapper<DeviceGroup> {

    /** 某用户全部分组（平铺，按同级排序）。 */
    List<DeviceGroup> selectByUser(@Param("userId") Long userId);

    /** 直接子分组数（删除前置校验）。 */
    int countChildren(@Param("parentId") Long parentId);

    /**
     * 某分组及其所有后代分组 ID（含自身），用于「按分组筛选含子分组」与批量目标解析。
     * <p>
     * 使用 MySQL 8 递归 CTE；限定 {@code user_id} 与 {@code deleted=0}。
     */
    List<Long> selectDescendantIds(@Param("userId") Long userId, @Param("rootId") Long rootId);

    /**
     * 同级重名校验：同一用户、同一父节点下的未删除分组数。
     *
     * @param parentId 父分组 ID，{@code null} 表示根分组
     * @param excludeId 排除的分组 ID（更新时排除自身），可空
     */
    int countByUserAndName(@Param("userId") Long userId,
                           @Param("parentId") Long parentId,
                           @Param("name") String name,
                           @Param("excludeId") Long excludeId);

    /** 同级最大排序值，用于创建时追加到末尾；无兄弟节点时返回 {@code null}。 */
    Integer selectMaxSortOrder(@Param("userId") Long userId, @Param("parentId") Long parentId);
}