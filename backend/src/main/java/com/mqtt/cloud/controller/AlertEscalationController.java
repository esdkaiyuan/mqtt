package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.AlertEscalationPolicyRequest;
import com.mqtt.cloud.entity.AlertEscalationPolicy;
import com.mqtt.cloud.service.AlertEscalationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警升级策略控制台接口（T-27 设计文档 §10.1）。
 * <p>
 * 策略按当前登录用户隔离，仅可读写本人数据；归属越权统一返回 {@code 403}。
 * 校验矩阵（名称 / 级别 / 阈值 / 处理人 / 渠道 / 数量）在服务层实现，命中即抛 {@code 6266}。
 * <p>
 * 回滚开关 {@code app.notification.enabled} 关闭时所有端点一律返回 {@code 404}
 * （{@link ResultCode#NOT_FOUND}），不泄露升级能力已存在的事实（与通知渠道端点同构）。
 */
@Tag(name = "告警升级策略", description = "未确认告警的升级与分派规则，仅可操作本人策略")
@RestController
@RequestMapping("/alert-escalations")
public class AlertEscalationController {

    private final AlertEscalationService escalationService;
    private final NotificationProperties properties;

    public AlertEscalationController(AlertEscalationService escalationService,
                                     NotificationProperties properties) {
        this.escalationService = escalationService;
        this.properties = properties;
    }

    @Operation(summary = "列出升级策略", description = "返回当前用户的全部升级策略，按创建时间倒序")
    @GetMapping
    public Result<List<AlertEscalationPolicy>> list() {
        requireEnabled();
        return Result.success(escalationService.listByUser(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "新建升级策略",
            description = "校验名称 / 级别 / 阈值 / 处理人 / 渠道 / 数量，非法返回 6266；"
                    + "渠道须为本人所有")
    @PostMapping
    public Result<AlertEscalationPolicy> create(@RequestBody AlertEscalationPolicyRequest request) {
        requireEnabled();
        return Result.success(escalationService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "获取策略详情", description = "仅所有者可访问，不存在返回 6265，越权返回 403")
    @GetMapping("/{id}")
    public Result<AlertEscalationPolicy> detail(
            @Parameter(description = "策略 ID", required = true) @PathVariable Long id) {
        requireEnabled();
        return Result.success(escalationService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新升级策略", description = "校验同创建；不存在返回 6265，非法返回 6266")
    @PutMapping("/{id}")
    public Result<AlertEscalationPolicy> update(
            @Parameter(description = "策略 ID", required = true) @PathVariable Long id,
            @RequestBody AlertEscalationPolicyRequest request) {
        requireEnabled();
        return Result.success(escalationService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除升级策略", description = "逻辑删除，不影响历史告警；不存在返回 6265，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "策略 ID", required = true) @PathVariable Long id) {
        requireEnabled();
        escalationService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    /** 回滚开关：关闭时端点整体隐藏，返回 404 而非 403，避免暴露升级能力。 */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }
}