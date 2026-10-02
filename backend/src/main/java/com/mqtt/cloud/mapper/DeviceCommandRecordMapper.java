package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备命令记录 Mapper（T-15）。
 * <p>
 * 所有状态迁移都是<b>条件更新</b>（{@code WHERE status IN ('PENDING','SENT')}），
 * 返回受影响行数：0 表示记录不存在或已是终态。这样「重复回执」与「超时巡检」
 * 与「同步等待超时」三者并发时，终态只会被写入一次，不会被互相覆盖。
 * <p>
 * T-16 新增离线补发相关方法：{@code PENDING → QUEUED}（入队）与
 * {@code QUEUED → SENT/FAILED}（补发），守卫改为 {@code status = 'QUEUED'}，
 * 与既有 {@code status IN ('PENDING','SENT')} 守卫互不干扰。
 */
@Mapper
public interface DeviceCommandRecordMapper extends BaseMapper<DeviceCommandRecord> {

    /** 发布成功：PENDING → SENT。 */
    int markSent(@Param("commandId") String commandId, @Param("sentAt") LocalDateTime sentAt);

    /** 收到成功回执：PENDING/SENT → ACKED。 */
    int markAcked(@Param("commandId") String commandId,
                  @Param("result") String result,
                  @Param("finishedAt") LocalDateTime finishedAt);

    /** 失败（发布异常或回执 code≠200）：PENDING/SENT → FAILED。 */
    int markFailed(@Param("commandId") String commandId,
                   @Param("errorMessage") String errorMessage,
                   @Param("finishedAt") LocalDateTime finishedAt);

    /** 同步等待超时：PENDING/SENT → TIMEOUT。 */
    int markTimeout(@Param("commandId") String commandId,
                    @Param("errorMessage") String errorMessage,
                    @Param("finishedAt") LocalDateTime finishedAt);

    /** 巡检兜底：把指定 {@code callType} 下创建时间早于 {@code before} 的未终态记录批量置 TIMEOUT，返回受影响行数。 */
    int sweepTimeout(@Param("callType") String callType,
                     @Param("before") LocalDateTime before,
                     @Param("errorMessage") String errorMessage,
                     @Param("limit") int limit);

    // ==================== T-16 设备影子：离线入队与补发 ====================

    /** 入队：PENDING → QUEUED（设备离线或 property_set 发布失败），并设置下次可补发时间。 */
    int markQueued(@Param("commandId") String commandId,
                   @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                   @Param("errorMessage") String errorMessage);

    /** 补发失败退避：QUEUED → QUEUED，尝试次数 +1 并按退避重设下次时间。 */
    int markRetry(@Param("commandId") String commandId,
                  @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                  @Param("errorMessage") String errorMessage);

    /** 补发成功：QUEUED → SENT。 */
    int markSentFromQueued(@Param("commandId") String commandId,
                           @Param("sentAt") LocalDateTime sentAt);

    /** 重试次数耗尽：QUEUED → FAILED。 */
    int markFailedFromQueued(@Param("commandId") String commandId,
                             @Param("errorMessage") String errorMessage,
                             @Param("finishedAt") LocalDateTime finishedAt);

    /** 取某设备到期可补发的 QUEUED 命令（按 next_attempt_at 升序，NULL 视为立即到期）。 */
    List<DeviceCommandRecord> selectQueuedByDevice(@Param("deviceId") Long deviceId,
                                                   @Param("now") LocalDateTime now,
                                                   @Param("limit") int limit);

    /** 统计某设备处于 QUEUED 的命令数（不看到期与否），用于上线补发的轻量短路。 */
    int countQueuedByDevice(@Param("deviceId") Long deviceId);

    /**
     * 巡检选取跨设备到期可补发的 QUEUED 命令：要求命令到期、设备 {@code ONLINE} 且启用未删除、
     * 关联产品 {@code ENABLED}；按 next_attempt_at 升序取 {@code limit} 条。
     */
    List<DeviceCommandRecord> selectDueQueued(@Param("now") LocalDateTime now,
                                              @Param("limit") int limit);
}