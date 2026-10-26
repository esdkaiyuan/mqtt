package com.mqtt.cloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mqtt.cloud.entity.AuditLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 审计日志持久层（T-53）。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 按时间分批删除过期审计日志。
     * <p>
     * 使用 {@code LIMIT} 避免一次删除大表长时间锁表；调用方循环直到影响行数小于批量。
     */
    @Delete("DELETE FROM audit_log WHERE created_at < #{threshold} LIMIT #{limit}")
    int deleteBefore(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}