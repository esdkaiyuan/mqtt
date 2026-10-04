package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.SceneExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 场景执行记录 Mapper（T-23 设计文档 §6.2）。
 * <p>
 * 状态迁移均为**条件更新**（{@code status IN ('PENDING','RUNNING')} 守卫），返回受影响行数，
 * 用于多副本并发下的幂等兜底。保留策略只清理终态记录（{@code PENDING} / {@code RUNNING} 永不清理）。
 */
@Mapper
public interface SceneExecutionMapper extends BaseMapper<SceneExecution> {

    /** 执行记录分页：按归属用户过滤，可选场景 / 触发设备 / 状态。 */
    IPage<SceneExecution> pageByUser(Page<SceneExecution> page,
                                     @Param("userId") Long userId,
                                     @Param("sceneId") Long sceneId,
                                     @Param("deviceId") Long deviceId,
                                     @Param("status") String status);

    /** 置运行中：记录首个步骤开始执行时刻。仅当仍 {@code PENDING} 时生效。 */
    int markRunning(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    /** 置成功（终态）。仅当非终态时生效。 */
    int markSuccess(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);

    /** 置失败（终态）：刷新失败原因。仅当非终态时生效。 */
    int markFailed(@Param("id") Long id,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);

    /** 进度自增：已完成（SUCCESS/SKIPPED）步骤数。 */
    int bumpFinishedSteps(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 手动重试重置：仅当记录为 {@code FAILED} 时置回 {@code RUNNING} 并清空失败原因。
     * <p>
     * 返回受影响行数，{@code 0} 表示记录非 {@code FAILED}（已被其它副本处理或状态冲突），
     * 由服务层据此返回 {@code 409}。
     */
    int resetForRetry(@Param("id") Long id);

    /** 保留策略清理：物理删除终态记录（{@code SUCCESS}/{@code FAILED}），按批处理。 */
    int deleteFinishedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}