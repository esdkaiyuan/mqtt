package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.service.AnalyticsService;
import com.mqtt.cloud.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息管理控制器
 */
@Tag(name = "消息管理", description = "MQTT 消息发布、查询、最近消息与趋势统计")
@RestController
@RequestMapping("/messages")
public class MessageController {

    private static final int MAX_RECENT_LIMIT = 100;

    private final MessageService messageService;
    private final AnalyticsService analyticsService;

    public MessageController(MessageService messageService, AnalyticsService analyticsService) {
        this.messageService = messageService;
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "发布消息", description = "向指定 Topic 发布 MQTT 消息并落库，发布失败返回 MQTT 相关错误码")
    @PostMapping("/publish")
    public Result<Message> publishMessage(@Valid @RequestBody PublishMessageDTO dto) {
        return Result.success(messageService.publishMessage(dto, SecurityUtils.requireUserId()));
    }

    @Operation(summary = "查询消息列表", description = "分页查询消息，支持 topic、deviceId、direction 与时间范围过滤")
    @GetMapping
    public Result<IPage<Message>> getMessages(MessageQueryDTO dto) {
        return Result.success(messageService.getMessages(dto, SecurityUtils.requireUserId()));
    }

    @Operation(summary = "获取最近消息", description = "返回最近的实时消息，limit 默认 50、最大 100")
    @GetMapping("/recent")
    public Result<List<Message>> getRecentMessages(
            @Parameter(description = "返回条数，默认50，最大100") @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        return Result.success(messageService.getRecentMessages(safeLimit));
    }

    @Operation(summary = "消息趋势统计", description = "按天统计消息量，days 默认 7 天")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getMessageTrend(
            @Parameter(description = "统计天数，默认7") @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(analyticsService.getMessageTrendByDay(days));
    }
}