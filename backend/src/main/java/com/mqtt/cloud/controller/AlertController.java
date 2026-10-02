package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.dto.request.AlertRuleRequest;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.service.AlertRuleService;
import com.mqtt.cloud.service.AlertService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警中心控制台接口（T-17 设计文档 §10.1）。
 * <p>
 * 规则与记录均按当前登录用户隔离，仅可读写本人数据；归属越权统一返回 {@code 403}。
 */
@Tag(name = "告警中心", description = "告警规则配置与告警记录管理，仅可操作本人数据")
@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final AlertService alertService;

    public AlertController(AlertRuleService alertRuleService, AlertService alertService) {
        this.alertRuleService = alertRuleService;
        this.alertService = alertService;
    }

    @Operation(summary = "规则列表", description = "返回本人告警规则，可按来源与启用状态过滤")
    @GetMapping("/rules")
    public Result<List<AlertRule>> listRules(
            @Parameter(description = "来源过滤：THRESHOLD / OFFLINE / EVENT") @RequestParam(required = false) String sourceType,
            @Parameter(description = "启用状态过滤") @RequestParam(required = false) Boolean enabled) {
        return Result.success(alertRuleService.list(SecurityUtils.requireUserId(), sourceType, enabled));
    }

    @Operation(summary = "创建规则", description = "校验来源 / 比较符 / 阈值等配置，非法返回 6208；设备非本人返回 2003")
    @PostMapping("/rules")
    public Result<AlertRule> createRule(@RequestBody AlertRuleRequest request) {
        return Result.success(alertRuleService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "规则详情", description = "按 ID 查询本人规则，不存在返回 6207，越权返回 403")
    @GetMapping("/rules/{id}")
    public Result<AlertRule> getRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id) {
        return Result.success(alertRuleService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新规则", description = "校验同创建；不存在返回 6207，非法返回 6208")
    @PutMapping("/rules/{id}")
    public Result<AlertRule> updateRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id,
            @RequestBody AlertRuleRequest request) {
        return Result.success(alertRuleService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除规则", description = "逻辑删除，不影响历史告警记录；不存在返回 6207")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id) {
        alertRuleService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "告警记录分页", description = "按 last_triggered_at 倒序，可按状态 / 来源 / 级别 / 设备过滤")
    @GetMapping
    public Result<IPage<AlertRecord>> pageAlerts(AlertQuery query) {
        return Result.success(alertService.page(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "告警详情", description = "不存在返回 6209，越权返回 403")
    @GetMapping("/{id}")
    public Result<AlertRecord> getAlert(
            @Parameter(description = "告警 ID", required = true) @PathVariable Long id) {
        return Result.success(alertService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "确认告警", description = "仅待处理（TRIGGERED）告警可确认，否则返回 6210")
    @PostMapping("/{id}/ack")
    public Result<Void> acknowledge(
            @Parameter(description = "告警 ID", required = true) @PathVariable Long id) {
        alertService.acknowledge(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "人工置恢复", description = "事件类告警的主要关闭方式；已恢复返回 6210")
    @PostMapping("/{id}/recover")
    public Result<Void> recover(
            @Parameter(description = "告警 ID", required = true) @PathVariable Long id) {
        alertService.recover(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "未读数", description = "本人活动告警数，供顶栏角标使用")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(alertService.unreadCount(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "最近活动告警", description = "本人活动告警按最近触发时间倒序取前 N 条，供顶栏通知下拉使用")
    @GetMapping("/recent")
    public Result<List<AlertRecord>> recentAlerts(
            @Parameter(description = "返回条数，默认 10，上限 50") @RequestParam(defaultValue = "10") int limit) {
        return Result.success(alertService.recent(SecurityUtils.requireUserId(), limit));
    }
}