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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @Operation(summary = "重放单条死信",
            description = "把原始载荷重新投回摄取管线，受理成功后置REPLAYED。"
                    + "返回成功仅代表**已受理**，不代表已落库（与 HTTP 上报同为 fire-and-forget）。"
                    + "已处置的记录返回 5003，防止同一条消息被反复重放造成数据翻倍；"
                    + "队列不可用时回退为 PENDING 并返回 5004")
    @PostMapping("/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> replay(@PathVariable Long id) {
        return Result.success(deadLetterService.replay(id));
    }

    @Operation(summary = "丢弃单条死信",
            description = "标记为 DISCARDED 且不再重放。已处置返回 5003，不存在返回 5002")
    @PostMapping("/{id}/discard")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Boolean> discard(@PathVariable Long id) {
        return Result.success(deadLetterService.discard(id));
    }

    @Operation(summary = "批量重放死信",
            description = "按 ID 列表批量重放，返回实际提交重放的条数。逐条隔离："
                    + "单条失败（已处置 / 队列不可用）不阻断其余条目")
    @PostMapping("/replay-batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Integer> replayBatch(@RequestBody List<Long> ids) {
        return Result.success(deadLetterService.replayBatch(ids));
    }

    @Operation(summary = "死信状态统计",
            description = "返回 PENDING / REPLAYED / DISCARDED 三态条数，供健康检查与告警读取")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<java.util.Map<String, Long>> stats() {
        return Result.success(deadLetterService.countByStatus());
    }
}