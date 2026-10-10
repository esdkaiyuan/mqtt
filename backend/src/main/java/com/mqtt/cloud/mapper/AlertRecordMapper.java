package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警记录 Mapper（T-17 设计文档 §6.2）。
 * <p>
 * 所有状态迁移均为**条件更新**（`status &lt;&gt; 'RECOVERED'` 等守卫），返回受影响行数，
 * 用于多副本并发下的幂等兜底。
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    /** 取某规则 + 某设备的唯一活动记录（{@code status <> 'RECOVERED'}），不存在返回 null。 */
    AlertRecord selectOpenByRuleAndDevice(@Param("ruleId") Long ruleId,
                                          @Param("deviceId") Long deviceId);

    /**
     * 抑制窗口内 / 外的重复触发累加：刷新计数、最近触发时间、触发值、可选的最近通知时间。
     * 仅当记录仍活动（{@code status <> 'RECOVERED'}）时生效。
     *
     * @return 受影响行数；0 表示记录已被并发置恢复
     */
    int updateTrigger(@Param("id") Long id,
                      @Param("triggerCount") int triggerCount,
                      @Param("lastTriggeredAt") LocalDateTime lastTriggeredAt,
                      @Param("triggerValue") String triggerValue,
                      @Param("notifiedAt") LocalDateTime notifiedAt);

    /** 置恢复（终态）。仅当记录仍活动时生效。 */
    int markRecovered(@Param("id") Long id, @Param("recoveredAt") LocalDateTime recoveredAt);

    /** 人工确认。仅当记录处于 {@code TRIGGERED} 时生效。 */
    int markAcknowledged(@Param("id") Long id,
                         @Param("acknowledgedAt") LocalDateTime acknowledgedAt,
                         @Param("acknowledgedBy") Long acknowledgedBy);

    /** 未读数：本人全部活动告警数（{@code status <> 'RECOVERED'}）。 */
    long countOpenByUser(@Param("userId") Long userId);

    /** 顶栏最近告警：本人活动告警按 {@code last_triggered_at} 倒序取前 N 条。 */
    List<AlertRecord> selectOpenByUserLimit(@Param("userId") Long userId,
                                            @Param("limit") int limit);

    /** 某规则下的活动告警（离线巡检恢复判定用）。 */
    List<AlertRecord> selectOpenByRule(@Param("ruleId") Long ruleId,
                                       @Param("limit") int limit);

    /** 批量取多台设备的活动告警（离线巡检按规则恢复判定用，避免逐台查询）。 */
    List<AlertRecord> selectOpenByRuleAndDevices(@Param("ruleId") Long ruleId,
                                                 @Param("deviceIds") List<Long> deviceIds);

    /**
     * 取规则已逻辑删除（或规则行已不存在）的活动告警，供离线巡检兜底置恢复。
     * <p>
     * 规则删除后不再被 {@code selectEnabledBySource} 命中，其活动告警不会进入按规则恢复判定，
     * 若无此兜底将永久停留活动态（设计文档 §13⑥）。此处用原生 SQL 关联 {@code alert_rule}
     * 绕过全局逻辑删除过滤，从而能看到 {@code deleted=1} 的规则行。
     */
    List<AlertRecord> selectOpenByDeletedRule(@Param("limit") int limit);

    /**
     * 取升级候选告警（T-27 设计文档 §5.1）：未确认（{@code status='TRIGGERED'}）且
     * 升级基准时间已越过阈值。基准时间取「{@code last_escalated_at} 为空时用
     * {@code first_triggered_at}，否则用 {@code last_escalated_at}」。
     *
     * @param userId   归属用户
     * @param severity 策略适用级别；{@code ALL} 表示不限级别
     * @param threshold 基准时间上限（早于等于此值即到期）
     * @param limit    单轮上限
     */
    List<AlertRecord> selectEscalationCandidates(@Param("userId") Long userId,
                                                 @Param("severity") String severity,
                                                 @Param("threshold") LocalDateTime threshold,
                                                 @Param("limit") int limit);

    /**
     * 升级条件更新（T-27 设计文档 §5.2）：仅当记录当前级别等于 {@code currentLevel} 时生效，
     * 返回受影响行数供多副本并发去重（0 表示已被并发升级，跳过）。{@code assigneeId} 为空时保持原处理人。
     */
    int escalate(@Param("id") Long id,
                 @Param("currentLevel") int currentLevel,
                 @Param("newLevel") int newLevel,
                 @Param("assigneeId") Long assigneeId,
                 @Param("escalatedAt") LocalDateTime escalatedAt);

    /** 手动指派 / 解除指派（T-27 设计文档 §5.3）；{@code assigneeId} 为空即解除指派。 */
    int assign(@Param("id") Long id,
               @Param("userId") Long userId,
               @Param("assigneeId") Long assigneeId,
               @Param("updatedAt") LocalDateTime updatedAt);
}
