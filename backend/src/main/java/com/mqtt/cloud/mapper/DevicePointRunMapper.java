package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DevicePointRun;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 点位采集执行日志Mapper（T-31）
 */
@Mapper
public interface DevicePointRunMapper extends BaseMapper<DevicePointRun> {

    /**
     * 批量落采集日志（T-31 采集轮次的批量出口）。
     * <p>
     * 一次设备轮询会产出「设备下全部点位」的结果行，逐条 INSERT 在点位规模大时
     * 会对 DB 造成不必要的往返压力，故统一批量写入。
     * <p>
     * 参数名沿用项目既有 XML 惯例 {@code rows}（见 {@code DevicePropertyHistoryMapper#insertBatch}）。
     */
    int insertBatch(@Param("rows") List<DevicePointRun> rows);

    /** 清理早于保留期的采集日志（保留期由配置决定；点位表可能高频写入，不清理会持续增长）。 */
    int deleteBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 取某点位最近一条采集日志。
     * <p>
     * 用注解而非 {@code lambdaQuery()}：后者经 {@code ServiceImpl#getBaseMapper} 反查
     * {@code SqlSessionFactory}，在纯单测（Mapper 为 mock）下会抛
     * {@code Unable to retrieve the mapperInterface}。
     * <p>
     * 二级排序键用 {@code id} 而非只靠 {@code collected_at}：批量写入时同一批多行的
     * 时间戳可能完全相同，只按时间排序会导致「最近一条」返回不确定的那一行。
     */
    @Select("SELECT * FROM device_point_run WHERE point_id = #{pointId} "
            + "ORDER BY collected_at DESC, id DESC LIMIT 1")
    DevicePointRun findLatestByPointId(@Param("pointId") Long pointId);

    /** 清理指定点位的历史日志（点位被删除或地址变更时调用）。 */
    @Delete("DELETE FROM device_point_run WHERE point_id = #{pointId}")
    int deleteByPointId(@Param("pointId") Long pointId);
}
