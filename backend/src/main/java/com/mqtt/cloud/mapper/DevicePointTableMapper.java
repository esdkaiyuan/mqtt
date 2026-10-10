package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DevicePointTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 设备点位Mapper（T-31）
 */
@Mapper
public interface DevicePointTableMapper extends BaseMapper<DevicePointTable> {

    /** 按设备取全部启用点位（不区分执行方）。 */
    @Select("SELECT * FROM device_point_table WHERE device_id = #{deviceId} AND enabled = 1 ORDER BY id")
    List<DevicePointTable> findEnabledByDevice(@Param("deviceId") Long deviceId);

    /**
     * 按设备与执行方取启用点位（T-31 取点位的第一入口）。
     * <p>
     * <b>执行方隔离的强制落点</b>：路线 A 与路线 B 都只能通过本方法取点位，
     * 从而保证同一设备的两条路线不会各自把全量点位捞走造成双采。
     */
    @Select("SELECT * FROM device_point_table WHERE device_id = #{deviceId} "
            + "AND executor = #{executor} AND enabled = 1 ORDER BY id")
    List<DevicePointTable> findEnabledByDeviceAndExecutor(@Param("deviceId") Long deviceId,
                                                           @Param("executor") String executor);

    /**
     * 取「已到采集周期」的待轮询点位（路线 B 调度入口）。
     * <p>
     * 判定依据为「该点位最近一次采集时间 + 采集周期 ≤ now」，而非「距上次调用超过 N 秒」——
     * 后者会让失败点位在每轮都被重试并写满失败日志，前者以真实采集时间为锚点，
     * 天然按点位各自的周期错峰。没有任何采集记录的点位（{@code last_collected_at} 为空）
     * 视为已到期，由 {@code COALESCE} 兜底。
     * <p>
     * 只取 {@code executor='PLATFORM'}：边缘执行方的点位由网关自行采集，平台不轮询。
     *
     * @param limit 单轮上限，防止点位规模大时一次性全量加载拖垮内存与现场总线
     */
    @Select("SELECT p.* FROM device_point_table p "
            + "LEFT JOIN (SELECT point_id, MAX(collected_at) AS last_at FROM device_point_run "
            + "           GROUP BY point_id) r ON r.point_id = p.id "
            + "WHERE p.enabled = 1 AND p.executor = 'PLATFORM' "
            + "AND DATE_ADD(COALESCE(r.last_at, p.created_at), INTERVAL p.read_interval_ms * 1000 MICROSECOND) <= NOW(3) "
            + "ORDER BY COALESCE(r.last_at, p.created_at) ASC "
            + "LIMIT #{limit}")
    List<DevicePointTable> findDuePlatformPoints(@Param("limit") int limit);

    /** 按设备取该设备已存在的执行方集合（写入前的一致性校验用）。 */
    @Select("SELECT DISTINCT executor FROM device_point_table WHERE device_id = #{deviceId}")
    List<String> findExecutorsByDevice(@Param("deviceId") Long deviceId);

    /**
     * 按设备 + 点位标识取点位（upsert 判定用）。
     * <p>
     * 刻意用注解而非 {@code lambdaQuery()}：后者经 {@code ServiceImpl#getBaseMapper}
     * 反查 {@code SqlSessionFactory}，会在纯单测（Mapper 为 mock、无 Spring 容器）下
     * 抛 {@code Unable to retrieve the mapperInterface}。同时它也绕开了显式注入的 Mapper 实例。
     */
    @Select("SELECT * FROM device_point_table WHERE device_id = #{deviceId} "
            + "AND point_key = #{pointKey} LIMIT 1")
    DevicePointTable findByPointKey(@Param("deviceId") Long deviceId,
                                    @Param("pointKey") String pointKey);

    /** 批量置为停用（删除设备时清理，避免遗留点位被继续轮询）。 */
    @Update("<script>UPDATE device_point_table SET enabled = 0, updated_at = NOW(3) "
            + "WHERE device_id IN <foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "</script>")
    int disableByDeviceIds(@Param("deviceIds") Collection<Long> deviceIds);
}
