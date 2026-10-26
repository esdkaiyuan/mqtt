package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.dto.request.AuditLogQueryDTO;
import com.mqtt.cloud.entity.AuditLog;
import com.mqtt.cloud.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询（T-53）。
 * <p>
 * 仅 ADMIN 可读：审计日志包含全平台用户的操作轨迹与来源 IP，属于安全敏感数据。
 * 导出见 {@code GET /audit-logs/export}（{@code ExportController}）。
 */
@Tag(name = "审计日志", description = "管理端写操作留痕：谁、何时、从哪里、做了什么、结果如何")
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志", description = "按用户 / 操作类型 / 资源类型 / 来源 / 结果 / "
            + "关键字与时间窗过滤，按操作时间倒序；仅 ADMIN 可访问")
    @GetMapping
    public Result<IPage<AuditLog>> page(AuditLogQueryDTO query) {
        return Result.success(auditLogService.page(query));
    }
}