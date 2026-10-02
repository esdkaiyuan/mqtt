package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 设备命令记录 Mapper（T-15）。
 * <p>
 * 所有状态迁移都是<b>条件更新</b>（{@code WHERE status IN ('PENDING','SENT')}），
 * 返回受影响行数：0 表示记录不存在或已是终态。这样「重复回执」与「超时巡检」
 * 与「同步等待超时」三者并发时，终态只会被写入一次，不会被互相覆盖。
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
}