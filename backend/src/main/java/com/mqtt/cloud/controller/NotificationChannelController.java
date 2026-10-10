package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.AlertConstants;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.config.NotificationProperties;
import com.mqtt.cloud.dto.request.NotificationChannelConfigRequest;
import com.mqtt.cloud.entity.NotificationChannelConfig;
import com.mqtt.cloud.service.NotificationChannelConfigService;
import com.mqtt.cloud.service.notification.ChannelSendResult;
import com.mqtt.cloud.service.notification.NotificationChannel;
import com.mqtt.cloud.service.notification.NotificationMessage;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知渠道配置控制台接口（T-27 设计文档 §10.1）。
 * <p>
 * 渠道配置按当前登录用户隔离，仅可读写本人数据；归属越权统一返回 {@code 403}。
 * 响应中的敏感字段（SMTP 密码、短信密钥、自定义请求头）一律脱敏为 {@code ******}，
 * 更新时留空或回填脱敏串表示保留原值 —— 脱敏与保留规则统一在服务层实现。
 * <p>
 * 回滚开关 {@code app.notification.enabled} 关闭时所有端点一律返回 {@code 404}
 * （{@link ResultCode#NOT_FOUND}），不泄露渠道能力已存在的事实（与 T-26 拓扑同构）。
 */
@Tag(name = "通知渠道", description = "邮件 / 短信渠道配置与连通性测试，仅可操作本人配置")
@RestController
@RequestMapping("/notification-channels")
public class NotificationChannelController {

    private static final String TEST_TITLE = "通知渠道连通性测试";
    private static final String TEST_CONTENT = "这是一条来自 MQTT 云平台的通知渠道连通性测试消息。";

    private final NotificationChannelConfigService channelService;
    private final NotificationProperties properties;
    private final Map<String, NotificationChannel> channelsByType;

    public NotificationChannelController(NotificationChannelConfigService channelService,
                                         NotificationProperties properties,
                                         List<NotificationChannel> channels) {
        this.channelService = channelService;
        this.properties = properties;
        Map<String, NotificationChannel> indexed = new HashMap<>();
        for (NotificationChannel channel : channels) {
            indexed.put(channel.channelType(), channel);
        }
        this.channelsByType = Map.copyOf(indexed);
    }

    @Operation(summary = "列出通知渠道", description = "返回当前用户的全部渠道配置，敏感字段已脱敏")
    @GetMapping
    public Result<List<NotificationChannelConfig>> list() {
        requireEnabled();
        return Result.success(channelService.listByUser(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "新建通知渠道", description = "类型仅支持 EMAIL / SMS；参数非法返回 6262，"
            + "类型不支持或数量超限返回 6263")
    @PostMapping
    public Result<NotificationChannelConfig> create(@RequestBody NotificationChannelConfigRequest request) {
        requireEnabled();
        return Result.success(channelService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "获取渠道详情", description = "仅所有者可访问，不存在返回 6261，越权返回 403")
    @GetMapping("/{id}")
    public Result<NotificationChannelConfig> detail(
            @Parameter(description = "渠道 ID", required = true) @PathVariable Long id) {
        requireEnabled();
        return Result.success(channelService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新通知渠道", description = "渠道类型不可变更；敏感字段留空或回填脱敏串表示保留原值")
    @PutMapping("/{id}")
    public Result<NotificationChannelConfig> update(
            @Parameter(description = "渠道 ID", required = true) @PathVariable Long id,
            @RequestBody NotificationChannelConfigRequest request) {
        requireEnabled();
        return Result.success(channelService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除通知渠道", description = "不存在返回 6261，越权返回 403；历史投递留痕保留")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "渠道 ID", required = true) @PathVariable Long id) {
        requireEnabled();
        channelService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "测试渠道连通性", description = "向该渠道同步发送一条测试消息；"
            + "渠道停用、无收件人或发送失败返回 6264")
    @PostMapping("/{id}/test")
    public Result<String> test(
            @Parameter(description = "渠道 ID", required = true) @PathVariable Long id) {
        requireEnabled();
        Long userId = SecurityUtils.requireUserId();
        NotificationChannelConfig config = channelService.getOwned(userId, id);
        NotificationChannel channel = channelsByType.get(config.getChannelType());
        if (channel == null) {
            throw new BusinessException(ResultCode.NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED,
                    "渠道类型不可用: " + config.getChannelType());
        }
        ChannelSendResult result = send(channel, userId, config);
        if (result == null || result.skip()) {
            throw new BusinessException(ResultCode.NOTIFICATION_SEND_FAILED, "渠道未启用或无可用收件人");
        }
        if (!result.success()) {
            throw new BusinessException(ResultCode.NOTIFICATION_SEND_FAILED, result.errorMessage());
        }
        return Result.success("发送成功");
    }

    /** 同步测试发送：钉上 {@code channelId} 使渠道按该行配置投递，绕过事件订阅过滤。 */
    private ChannelSendResult send(NotificationChannel channel, Long userId, NotificationChannelConfig config) {
        NotificationMessage message = new NotificationMessage();
        message.setUserId(userId);
        message.setEventType(AlertConstants.EVENT_ALERT_TRIGGERED);
        message.setChannelId(config.getId());
        message.setTitle(TEST_TITLE);
        message.setContent(TEST_CONTENT);
        try {
            return channel.send(message);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.NOTIFICATION_SEND_FAILED, e.getMessage());
        }
    }

    /** 回滚开关：关闭时端点整体隐藏，返回 404 而非 403，避免暴露渠道能力。 */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }
}