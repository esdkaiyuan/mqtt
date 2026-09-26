package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.MessageQueryDTO;
import com.mqtt.cloud.dto.request.PublishMessageDTO;
import com.mqtt.cloud.entity.Message;
import com.mqtt.cloud.mapper.AnalyticsMapper;
import com.mqtt.cloud.mapper.MessageMapper;
import com.mqtt.cloud.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息管理控制器
 */
@RestController
@RequestMapping("/messages")
public class MessageController {

    private static final int MAX_RECENT_LIMIT = 100;

    private final MessageService messageService;
    private final MessageMapper messageMapper;
    private final AnalyticsMapper analyticsMapper;

    public MessageController(MessageService messageService,
                             MessageMapper messageMapper,
                             AnalyticsMapper analyticsMapper) {
        this.messageService = messageService;
        this.messageMapper = messageMapper;
        this.analyticsMapper = analyticsMapper;
    }

    @PostMapping("/publish")
    public Result<Message> publishMessage(@Valid @RequestBody PublishMessageDTO dto) {
        return Result.success(messageService.publishMessage(dto, SecurityUtils.requireUserId()));
    }

    @GetMapping
    public Result<IPage<Message>> getMessages(MessageQueryDTO dto) {
        return Result.success(messageService.getMessages(dto, SecurityUtils.requireUserId()));
    }

    @GetMapping("/recent")
    public Result<List<Message>> getRecentMessages(@RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        return Result.success(messageMapper.findRecentMessages(safeLimit));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getMessageTrend(
            @RequestParam(defaultValue = "7") Integer days) {
        return Result.success(analyticsMapper.countMessagesByDay(days));
    }
}