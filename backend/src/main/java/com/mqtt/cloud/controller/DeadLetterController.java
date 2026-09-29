package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.entity.IngestDeadLetter;
import com.mqtt.cloud.service.IngestDeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "死信管理", description = "上行摄取死信的查询与处置")
@RestController
@RequestMapping("/admin/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final IngestDeadLetterService deadLetterService;

    @Operation(summary = "分页查询死信")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<IPage<IngestDeadLetter>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(deadLetterService.page(status, pageNum, pageSize));
    }
}