package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.ExecutionQueryDTO;
import com.mqtt.cloud.dto.request.RuleQueryDTO;
import com.mqtt.cloud.dto.request.RuleRequest;
import com.mqtt.cloud.dto.request.RuleTestRequest;
import com.mqtt.cloud.dto.response.RuleTestResult;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.entity.RuleExecution;
import com.mqtt.cloud.service.RuleExecutionService;
import com.mqtt.cloud.service.RuleService;
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

import java.util.Map;

/**
 * 消息规则控制台接口（T-19 设计文档 §10.1）。
 * <p>
 * 规则与执行记录均按当前登录用户隔离，仅可读写本人数据；设备归属越权返回 {@code 2003}，
 * 规则 / 执行记录资源越权返回 {@code 403}。
 * <p>
 * 执行记录的两个静态段端点（{@code /rules/executions}）先于 {@code /rules/{id}} 声明，
 * 避免动态段吞掉静态路径。
 */
@Tag(name = "消息规则", description = "消息规则配置、执行记录与试运行，仅可操作本人数据")
@RestController
@RequestMapping("/rules")
public class RuleController {

    private final RuleService ruleService;
    private final RuleExecutionService ruleExecutionService;

    public RuleController(RuleService ruleService, RuleExecutionService ruleExecutionService) {
        this.ruleService = ruleService;
        this.ruleExecutionService = ruleExecutionService;
    }

    @Operation(summary = "规则分页列表", description = "返回本人规则，可按来源 / 动作 / 启用态 / 关键字过滤")
    @GetMapping
    public Result<IPage<RuleDefinition>> listRules(RuleQueryDTO query) {
        return Result.success(ruleService.page(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "创建规则", description = "校验来源 / 条件 / 动作配置，非法返回 6219，"
            + "不支持的动作类型返回 6220，越权设备返回 2003，超出规则数上限返回 6222")
    @PostMapping
    public Result<RuleDefinition> createRule(@RequestBody RuleRequest request) {
        return Result.success(ruleService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "执行记录分页", description = "本人执行记录，可按规则 / 设备 / 状态过滤，按创建时间倒序")
    @GetMapping("/executions")
    public Result<IPage<RuleExecution>> listExecutions(ExecutionQueryDTO query) {
        return Result.success(ruleExecutionService.page(SecurityUtils.requireUserId(), query));
    }

    @Operation(summary = "执行记录详情", description = "含转发载荷快照；不存在返回 6221，越权返回 403")
    @GetMapping("/executions/{id}")
    public Result<RuleExecution> getExecution(
            @Parameter(description = "执行记录 ID", required = true) @PathVariable Long id) {
        return Result.success(ruleExecutionService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "手动重试", description = "仅失败（FAILED）记录可重试，重置后立即执行；"
            + "非失败状态返回 409，不存在返回 6221，越权返回 403")
    @PostMapping("/executions/{id}/retry")
    public Result<Void> retryExecution(
            @Parameter(description = "执行记录 ID", required = true) @PathVariable Long id) {
        ruleExecutionService.manualRetry(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "规则详情", description = "按 ID 查询本人规则，不存在返回 6218，越权返回 403")
    @GetMapping("/{id}")
    public Result<RuleDefinition> getRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id) {
        return Result.success(ruleService.getOwned(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "更新规则", description = "校验同创建；不存在返回 6218，非法返回 6219 / 6220，越权设备返回 2003")
    @PutMapping("/{id}")
    public Result<RuleDefinition> updateRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id,
            @RequestBody RuleRequest request) {
        return Result.success(ruleService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "删除规则", description = "逻辑删除，执行记录保留；不存在返回 6218，越权返回 403")
    @DeleteMapping("/{id}")
    public Result<Void> deleteRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id) {
        ruleService.delete(SecurityUtils.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "启用 / 停用规则", description = "请求体 {enabled:true/false}；不存在返回 6218，越权返回 403")
    @PutMapping("/{id}/enabled")
    public Result<Void> setRuleEnabled(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        if (body == null || body.get("enabled") == null) {
            throw new BusinessException(ResultCode.RULE_INVALID, "缺少 enabled 字段");
        }
        ruleService.setEnabled(SecurityUtils.requireUserId(), id, body.get("enabled"));
        return Result.success();
    }

    @Operation(summary = "规则试运行", description = "干跑判定条件是否命中并返回诊断，不产生任何副作用；"
            + "不存在返回 6218，非法返回 6219，越权设备返回 2003")
    @PostMapping("/{id}/test")
    public Result<RuleTestResult> testRule(
            @Parameter(description = "规则 ID", required = true) @PathVariable Long id,
            @RequestBody RuleTestRequest request) {
        return Result.success(ruleService.test(SecurityUtils.requireUserId(), id, request));
    }
}