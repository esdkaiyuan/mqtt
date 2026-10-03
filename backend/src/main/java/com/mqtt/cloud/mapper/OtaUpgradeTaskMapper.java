package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.OtaUpgradeTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * OTA 升级任务 Mapper（T-22 设计文档 §7.1 / §7.2）。
 * <p>
 * 单表 CRUD 直接继承 {@code BaseMapper}；计数与状态的维护统一走 {@link #refreshCounters(Long)}：
 * 由 {@code ota_upgrade_record} 聚合重算 {@code total/dispatched/success/failed} 与 {@code status}，
 * 而非在业务路径上逐条增减，避免并发回传下的计数漂移。
 * <p>
 * 注意：{@code ota_upgrade_task} 无逻辑删除列，删除任务须使用物理删除（自定义 SQL 或
 * {@code delete} 的物理删除包装），不可使用 MyBatis-Plus 走 {@code deleted} 的逻辑删除 API。
 */
@Mapper
public interface OtaUpgradeTaskMapper extends BaseMapper<OtaUpgradeTask> {

    /**
     * 按 {@code ota_upgrade_record} 重算任务计数与状态。
     * <p>
     * 无记录时子查询无行，聚合结果为空，更新命中 0 行（任务保持原值），调用方无需特殊处理。
     *
     * @param taskId 任务主键
     * @return 受影响行数（0 表示该任务下暂无记录）
     */
    int refreshCounters(@Param("taskId") Long taskId);
}
