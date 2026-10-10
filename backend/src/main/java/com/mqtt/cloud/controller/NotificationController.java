package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.entity.Notification;
import com.mqtt.cloud.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信控制台接口（T-27 设计文档 §10.1）。
 * <p>
 * 站内信按当前登录用户隔离，仅可读写本人数据；归属越权统一返回 {@code 403}。
 * {@code app.notification.enabled=false} 时本组端点保留（读取与已读照常），仅停止新增
 * —— 新写入由分发器与站内信通道把关。
 */
@Tag(name = "站内信", description = "站内信分页、未读数与已读管理，仅可操作本人数据")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "站内信分页", description = "按创建时间倒序，支持 unreadOnly 过滤")
    @GetMapping
    public Result<IPage<Notification>> page(
            @Parameter(description = "仅看未读") @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "页码，默认 1") @RequestParam(required = false) Integer pageNum,
            @Parameter(description = "每页数量，默认 20，上限 100") @RequestParam(required = false) Integer pageSize) {
        return Result.success(notificationService.page(SecurityUtils.requireUserId(), unreadOnly, pageNum, pageSize));
    }

    @Operation(summary = "未读数", description = "本人未读站内信数，供顶栏角标使用")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "标记单条已读", description = "重复标记幂等；不存在返回 404，越权返回 403")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(
            @Parameter(description = "站内信 ID", required = true) @PathVariable Long id) {
        notificationService.markRead(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "全部标记已读", description = "把本人全部未读站内信置为已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(SecurityUtils.requireUserId());
        return Result.success();
    }

    @Operation(summary = "删除单条", description = "不存在返回 404，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> deleteOne(
            @Parameter(description = "站内信 ID", required = true) @PathVariable Long id) {
        notificationService.deleteOne(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "清空已读", description = "删除本人全部已读站内信，未读保留")
    @DeleteMapping
    public Result<Void> clearRead() {
        notificationService.clearRead(SecurityUtils.requireUserId());
        return Result.success();
    }
}