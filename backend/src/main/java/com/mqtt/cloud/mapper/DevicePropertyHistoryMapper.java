package com.mqtt.cloud.mapper;

import com.mqtt.cloud.dto.response.PropertyHistoryAggregate;
import com.mqtt.cloud.entity.DevicePropertyHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备属性历史 Mapper（T-21 设计文档 §7.1 / §7.2）。
 * <p>
 * <b>不继承 {@code BaseMapper}</b>：本表只做「按批追加写 / 分桶聚合读 / 按时间清理」三件事，
 * 无需 MyBatis-Plus 的单表 CRUD 与自动分页（聚合查询不分页），故只暴露三个自定义方法，语句统一放 XML。
 */
@Mapper
public interface DevicePropertyHistoryMapper {

    /**
     * 按批追加写入历史行（不去重、不更新时间戳）。
     * <p>
     * {@code created_at} 由表默认值 {@code CURRENT_TIMESTAMP(3)} 维护，语句不显式赋值。
     * 调用方须保证 {@code rows} 非空（空集合会生成非法 SQL）。
     */
    int insertBatch(@Param("rows") List<DevicePropertyHistory> rows);

    /**
     * 按「设备 × 属性 × 时间桶」分桶聚合。
     * <p>
     * 半开区间 {@code [startTime, endTime)}，仅命中 {@code idx_device_identifier_time}；
     * {@code numeric=true} 时输出 {@code min/max/avg}，否则三列为 {@code NULL}（避免对文本列做无谓 CAST）。
     * 结果按 {@code device_id ASC, identifier ASC, time ASC} 排序。
     *
     * @param deviceIds     设备 ID 列表（非空）
     * @param identifiers   属性标识符列表（非空）
     * @param startTime     起始（含）
     * @param endTime       结束（不含）
     * @param bucketSeconds 桶宽（秒），由服务层按白名单映射，SQL 内不做字符串解析
     * @param numeric       是否数值型：{@code true} 才生成 {@code MIN/MAX/AVG}
     */
    List<PropertyHistoryAggregate> aggregateByBucket(@Param("deviceIds") List<Long> deviceIds,
                                                     @Param("identifiers") List<String> identifiers,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime,
                                                     @Param("bucketSeconds") long bucketSeconds,
                                                     @Param("numeric") boolean numeric);

    /**
     * 清理早于阈值的历史（单批，硬删除）。
     * <p>
     * 调用方以 {@code threshold = now - retention-days} 分批循环，直到影响行数小于 {@code batchSize}。
     */
    int purgeBefore(@Param("threshold") LocalDateTime threshold, @Param("batchSize") int batchSize);
}
