package com.mqtt.cloud.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.dto.request.AuditLogQueryDTO;
import com.mqtt.cloud.entity.AuditLog;

import java.util.List;

/**
 * 审计日志服务（T-53）。
 * <p>
 * 写路径由 {@code AuditLogInterceptor} 统一调用 {@link #record(AuditLog)}；
 * 读路径仅 ADMIN 可用（由 {@code SecurityConfig} 限定）。
 */
public interface AuditLogService {

    /**
     * 追加一条审计日志。
     * <p>
     * 调用方应自行 try/catch：审计写库失败不得影响业务请求。
     */
    void record(AuditLog log);

    /**
     * 分页查询审计日志，按操作时间倒序。
     */
    IPage<AuditLog> page(AuditLogQueryDTO query);

    /**
     * 导出用查询：按条件取最多 {@code limit} 条，不分页。
     */
    List<AuditLog> listForExport(AuditLogQueryDTO query, int limit);

    /**
     * 删除超过保留期的审计日志，返回删除条数。
     */
    int purgeExpired();
}