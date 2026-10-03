package com.mqtt.cloud.mapper;

import com.mqtt.cloud.dto.response.DeviceLogItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备统一日志 Mapper（T-20 设计文档 §7.2 / §7.4）。
 * <p>
 * <b>不继承 {@code BaseMapper}</b>：设备日志不是某一张表的实体，而是四张存量表
 * （{@code message} / {@code device_command_record} / {@code device_event_record} / {@code device_status_history}）
 * 在<strong>查询时</strong>以 {@code UNION ALL} 归并出的只读投影（跨表归并 + 全局排序 + 全局分页，
 * 无法用单表自动 count），因此仅提供两个自定义查询方法，四张表的既有 Mapper 零改动。
 * <ul>
 *   <li>{@link #countQuery} —— 显式计数（UNION 场景下 MyBatis-Plus 自动 count 不可靠）；</li>
 *   <li>{@link #pageQuery} —— 归并 + 过滤 + 倒序 + 分页。</li>
 * </ul>
 */
@Mapper
public interface DeviceLogMapper {

    /**
     * 统计符合条件的日志条目总数。
     *
     * @param deviceId  设备 ID（必填，设备级限定）
     * @param types     日志类型白名单（非空时仅统计这些类型）
     * @param startTime 起始时间（含，可空）
     * @param endTime   结束时间（不含，可空）
     * @param keyword   LIKE 关键字（已由服务层包裹 {@code %...%}，可空）
     */
    long countQuery(@Param("deviceId") Long deviceId,
                    @Param("types") List<String> types,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime,
                    @Param("keyword") String keyword);

    /**
     * 分页查询归并后的日志条目，按 {@code occurred_at DESC, seq DESC} 排序。
     *
     * @param offset 偏移量（{@code (pageNum - 1) * pageSize}）
     * @param size   每页数量
     *               其余参数同 {@link #countQuery}
     */
    List<DeviceLogItem> pageQuery(@Param("deviceId") Long deviceId,
                                  @Param("types") List<String> types,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("keyword") String keyword,
                                  @Param("offset") long offset,
                                  @Param("size") long size);
}
