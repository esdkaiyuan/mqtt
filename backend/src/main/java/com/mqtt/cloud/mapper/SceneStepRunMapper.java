package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.SceneStepRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景步骤执行明细 Mapper（T-23 设计文档 §6.2）。
 * <p>
 * 执行以 {@link #claimPending(Long)} 为**唯一抢占入口**（{@code status='PENDING'} 条件更新），
 * 终态迁移均带 {@code status} 守卫；巡检只拾取「{@code next_attempt_at} 非空且到期」的 {@code PENDING} 行，
 * 未排期（{@code next_attempt_at IS NULL}）的后续步骤不被抢先执行。
 */
@Mapper
public interface SceneStepRunMapper extends BaseMapper<SceneStepRun> {

    /** 某次执行的全部步骤明细，按 {@code seq} 升序。 */
    List<SceneStepRun> selectByExecutionId(@Param("executionId") Long executionId);

    /**
     * 取到期步骤：{@code status='PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= now}。
     * <p>
     * 未排期（{@code next_attempt_at IS NULL}）的后续步骤不被拾取，避免前序未完成时抢先执行。
     */
    List<SceneStepRun> selectDueSteps(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 取卡死的运行中步骤：{@code status='RUNNING' AND started_at <= deadline}（进程中途退出恢复用）。 */
    List<SceneStepRun> selectStaleRunning(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /** 并发抢占：仅当仍 {@code PENDING} 时置 {@code RUNNING}；返回 1 才允许执行。 */
    int claimPending(@Param("id") Long id);

    /** 为下一步骤排期：置 {@code scheduled_at} / {@code next_attempt_at}（仅 {@code PENDING} 生效）。 */
    int scheduleNext(@Param("id") Long id,
                     @Param("scheduledAt") LocalDateTime scheduledAt,
                     @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    /** 置成功（终态）。仅当仍 {@code RUNNING} 时生效。 */
    int markSuccess(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);

    /** 置重试：刷新尝试次数与下次重试锚点、失败原因。仅当仍 {@code RUNNING} 时生效。 */
    int markRetry(@Param("id") Long id,
                  @Param("attemptCount") int attemptCount,
                  @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                  @Param("errorMessage") String errorMessage);

    /** 置失败（终态）：刷新最终尝试次数与失败原因。仅当仍 {@code RUNNING} 时生效。 */
    int markFailed(@Param("id") Long id,
                   @Param("attemptCount") int attemptCount,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);

    /** 中止时把**尚未执行**的后续步骤置 {@code SKIPPED}。仅当仍 {@code PENDING} 时生效。 */
    int markSkipped(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);

    /** 卡死恢复：把 {@code RUNNING} 复位为 {@code PENDING} 并推进尝试次数。仅当仍 {@code RUNNING} 时生效。 */
    int resetStale(@Param("id") Long id,
                   @Param("attemptCount") int attemptCount,
                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    /**
     * 手动重试重置：把该执行下**失败 / 已跳过**的步骤重置为 {@code PENDING}（{@code attempt_count=0}、清空失败原因与起止时刻）。
     * <p>
     * 仅序号最小的待重放步骤置 {@code next_attempt_at=scheduled_at=nextAttemptAt}（立即执行），
     * 其余重置步骤的锚点置 {@code NULL}（未排期），待前序步骤成功后由执行器逐级排期，避免抢先执行。
     * 已 {@code SUCCESS} 的步骤不受影响（不重放）。
     */
    int resetForRetry(@Param("executionId") Long executionId,
                      @Param("firstSeq") Integer firstSeq,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    /** 回填转发载荷快照（HTTP/MQTT 步骤渲染成功后调用，无论发送成功与否）。 */
    int updateForwardPayload(@Param("id") Long id, @Param("forwardPayload") String forwardPayload);
}