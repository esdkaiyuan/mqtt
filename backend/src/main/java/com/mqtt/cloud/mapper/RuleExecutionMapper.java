package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.RuleExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则执行记录 Mapper（T-19 设计文档 §6.2）。
 * <p>
 * 终态与重试锚点更新均为**条件更新**（{@code status='PENDING'} 守卫），返回受影响行数，
 * 用于多副本并发下的幂等兜底；巡检只拾取「{@code next_attempt_at} 非空且到期」的 {@code PENDING} 记录。
 */
@Mapper
public interface RuleExecutionMapper extends BaseMapper<RuleExecution> {

    /**
     * 执行记录分页：按归属用户过滤，可选规则 / 设备 / 状态。
     */
    IPage<RuleExecution> pageByUser(Page<RuleExecution> page,
                                    @Param("userId") Long userId,
                                    @Param("ruleId") Long ruleId,
                                    @Param("deviceId") Long deviceId,
                                    @Param("status") String status);

    /**
     * 取到期重试记录：{@code status='PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= now}。
     * <p>
     * {@code next_attempt_at IS NULL} 的首次执行记录（已投递待执行）不被拾取，避免与线程池内的首次执行并发重复。
     */
    List<RuleExecution> selectDueRetries(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 置成功（终态）。仅当记录仍 {@code PENDING} 时生效。 */
    int markSuccess(@Param("id") Long id, @Param("finishedAt") LocalDateTime finishedAt);

    /** 置重试：刷新尝试次数与下次重试锚点、失败原因。仅当记录仍 {@code PENDING} 时生效。 */
    int markRetry(@Param("id") Long id,
                  @Param("attemptCount") int attemptCount,
                  @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                  @Param("errorMessage") String errorMessage);

    /** 置失败（终态）：刷新最终尝试次数与失败原因。仅当记录仍 {@code PENDING} 时生效。 */
    int markFailed(@Param("id") Long id,
                   @Param("attemptCount") int attemptCount,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);

    /** 回填转发载荷快照（HTTP/MQTT 动作渲染后调用，无论发送成功与否）。 */
    int updateForwardPayload(@Param("id") Long id, @Param("forwardPayload") String forwardPayload);

    /**
     * 手动重试重置：仅当记录为终态 {@code FAILED} 时，重置为 {@code PENDING} 并清零尝试次数与重试锚点。
     * <p>
     * 返回受影响行数，{@code 0} 表示记录非 {@code FAILED}（已被其它副本处理或状态冲突），
     * 由服务层据此返回 {@code 409}。
     */
    int resetForRetry(@Param("id") Long id);

    /** 保留策略清理：物理删除终态记录（{@code SUCCESS}/{@code FAILED}），按批处理。 */
    int deleteFinishedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}