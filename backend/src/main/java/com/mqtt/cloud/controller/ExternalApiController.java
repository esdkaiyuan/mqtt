package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.Result;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.common.constant.OtaStatusValue;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.dto.request.CommandInvokeRequest;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AlertRule;
import com.mqtt.cloud.entity.ApiKey;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.request.HistoryQueryDTO;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.request.RuleQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;
import com.mqtt.cloud.dto.response.OtaBatchVO;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.OtaTaskDetailVO;
import com.mqtt.cloud.dto.response.PropertySeriesVO;
import com.mqtt.cloud.entity.HistoryRecord;
import com.mqtt.cloud.entity.OtaFirmware;
import com.mqtt.cloud.service.AlertRuleService;
import com.mqtt.cloud.service.AlertService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceLogService;
import com.mqtt.cloud.service.HistoryService;
import com.mqtt.cloud.service.OtaFirmwareService;
import com.mqtt.cloud.service.OtaUpgradeService;
import com.mqtt.cloud.service.PropertyHistoryService;
import com.mqtt.cloud.service.RuleService;
import com.mqtt.cloud.service.SceneService;
import com.mqtt.cloud.service.ApiKeyService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API（供第三方系统通过 API Key 调用）
 * <p>
 * 认证由 {@code ApiKeyAuthFilter} 完成，本控制器只负责数据隔离与业务处理：
 * 所有查询与操作都被限制在当前 API Key 所属用户的名下设备范围内。
 * <p>
 * 命令下发与控制台同语义（T-15 设计文档 §10.2），仅 {@code source=OPEN_API}、无操作者 ID；
 * 原 {@code {payload, qos, retain}} 直发契约因主题与 ACL 冲突本就不可用，已被结构化命令契约替换。
 */
@Slf4j
@Tag(name = "外部API", description = "供第三方系统调用的开放接口，使用 X-API-Key 请求头认证，数据范围限定为密钥所属用户")
@RestController
@RequestMapping("/external/v1")
@RequiredArgsConstructor
public class ExternalApiController {

    private final ApiKeyService apiKeyService;
    private final DeviceService deviceService;
    private final DeviceCommandService deviceCommandService;
    private final DeviceShadowService deviceShadowService;
    private final AlertRuleService alertRuleService;
    private final AlertService alertService;

    // ---- T-35 · X-1 开放 API 补齐：全部复用既有服务，零业务逻辑重写 ----
    private final DeviceDataService deviceDataService;
    private final PropertyHistoryService propertyHistoryService;
    private final HistoryService historyService;
    private final DeviceLogService deviceLogService;
    private final RuleService ruleService;
    private final OtaFirmwareService otaFirmwareService;
    private final OtaUpgradeService otaUpgradeService;
    private final SceneService sceneService;

    @Operation(summary = "外部-查询设备列表", description = "返回密钥所属用户的名下设备列表；传入 deviceKey 时精确返回单个设备")
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices(
            @Parameter(description = "设备唯一标识，可选，传则精确查询单设备") @RequestParam(required = false) String deviceKey) {
        Long userId = currentUserId();
        if (deviceKey != null && !deviceKey.isBlank()) {
            return Result.success(List.of(toDeviceMap(requireOwnedDevice(deviceKey, userId))));
        }

        List<Map<String, Object>> devices = deviceService.getDevicesByOwner(userId).stream()
                .map(this::toDeviceMap)
                .toList();
        return Result.success(devices);
    }

    @Operation(summary = "外部-查询设备详情", description = "按 deviceKey 返回设备完整信息（含 description 与 metadata）")
    @GetMapping("/devices/{deviceKey}")
    public Result<Map<String, Object>> getDevice(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = toDeviceMap(device);
        result.put("description", device.getDescription());
        result.put("metadata", device.getMetadata());
        return Result.success(result);
    }

    @Operation(summary = "外部-查询设备状态", description = "返回设备的 status/lastSeen/online 字段")
    @GetMapping("/devices/{deviceKey}/status")
    public Result<Map<String, Object>> getDeviceStatus(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("deviceKey", device.getDeviceKey());
        result.put("status", device.getStatus());
        result.put("lastSeen", device.getLastSeen());
        result.put("online", "ONLINE".equals(device.getStatus()));
        return Result.success(result);
    }

    @Operation(summary = "外部-下发设备命令", description = "与控制台同语义：按物模型校验参数后下发到 device/{deviceKey}/cmd/down；"
            + "type=property_set / service，callType=sync 等待回执到终态。物模型缺失 6201 / 属性不可写 6202 / "
            + "标识符未定义 6203 / 参数非法 6204 / 发布失败 4001")
    @PostMapping("/devices/{deviceKey}/command")
    public Result<DeviceCommandRecord> sendCommand(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Valid @RequestBody CommandInvokeRequest request) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        DeviceCommandRecord record = deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                device.getId(), device.getProductId(), request.getType(), request.getIdentifier(),
                request.getParams() == null ? null : request.getParams().toString(),
                request.getCallType(), DeviceCommandService.SOURCE_OPEN_API, null));
        return Result.success(record);
    }

    @Operation(summary = "外部-查询命令记录", description = "按 deviceKey 分页返回命令记录，按创建时间倒序")
    @GetMapping("/devices/{deviceKey}/commands")
    public Result<IPage<DeviceCommandRecord>> getCommandRecords(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceCommandService.listCommands(device.getId(), page, size));
    }

    @Operation(summary = "外部-查询设备影子", description = "返回 desired/reported/delta 三份状态与影子版本号；"
            + "影子不存在时返回空映射与 version=0；modeled=false 表示产品未定义物模型")
    @GetMapping("/devices/{deviceKey}/shadow")
    public Result<DeviceShadowService.DeviceShadowResponse> getDeviceShadow(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceShadowService.get(device.getId(), device.getProductId()));
    }

    @Operation(summary = "外部-写入影子期望值", description = "等价于 type=property_set 命令（source=OPEN_API）；"
            + "在线设备返回 SENT，离线返回 QUEUED 并在上线后补发；params 缺失或为空返回 6206")
    @PutMapping("/devices/{deviceKey}/shadow/desired")
    public Result<DeviceCommandRecord> putDesired(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestBody CommandInvokeRequest request) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        JsonNode params = request == null ? null : request.getParams();
        if (params == null || !params.isObject() || params.isEmpty()) {
            throw new BusinessException(ResultCode.SHADOW_DESIRED_INVALID, "params 必须为非空 JSON 对象");
        }
        return Result.success(deviceCommandService.invoke(new DeviceCommandService.CommandInvoke(
                device.getId(), device.getProductId(), DeviceCommandService.TYPE_PROPERTY_SET, null,
                params.toString(), DeviceCommandService.CALL_TYPE_ASYNC,
                DeviceCommandService.SOURCE_OPEN_API, null)));
    }

    // ================= T-35 · X-1 开放 API 补齐（全部只读） =================
    //
    // 设计约定（勿破坏）：
    //   1. **零业务逻辑重写** —— 全部转发既有服务，只做「API Key → userId」转换与响应裁剪；
    //   2. **严格只读** —— 不开放任何写操作。写操作的审计、回滚与幂等语义远比读复杂，
    //      且控制台已完整覆盖；第三方需要写能力时应走既有的命令 / 影子写端点。
    //   3. **归属隔离由 userId 承担** —— 所有服务方法本身就以 userId 做归属校验，
    //      因此 API Key 天然只能看到自己账号的数据，无需在控制器重复判断。

    @Operation(summary = "外部-查询属性最新值", description = "返回设备全部属性的最新上报值（identifier/value/reportedAt）；"
            + "设备无上报时返回空数组；非归属用户访问返回 2003")
    @GetMapping("/devices/{deviceKey}/properties")
    public Result<List<Map<String, Object>>> getPropertyLatest(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceDataService.getPropertyLatest(device.getId()).stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("identifier", item.getIdentifier());
                    map.put("dataType", item.getDataType());
                    map.put("value", item.getValueText());
                    map.put("reportedAt", item.getReportedAt());
                    return map;
                })
                .toList());
    }

    @Operation(summary = "外部-查询事件记录", description = "按 deviceKey 分页返回事件上报记录，按时间倒序；"
            + "pageSize 上限 100；非归属用户访问返回 2003")
    @GetMapping("/devices/{deviceKey}/events")
    public Result<IPage<com.mqtt.cloud.entity.DeviceEventRecord>> getEvents(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        return Result.success(deviceDataService.getEvents(device.getId(),
                Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "外部-查询属性时序", description = "按属性标识符集合与时间窗做分桶聚合，返回序列点；"
            + "校验口径与控制台一致：时间范围非法 6225 / 未建模 6226 / 桶非法 6227；"
            + "identifiers 为空时默认取该设备全部已建模属性")
    @GetMapping("/devices/{deviceKey}/properties/history")
    public Result<List<PropertySeriesVO>> getPropertyHistory(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestParam(required = false) String identifiers,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @Parameter(description = "时间桶：1m/5m/15m/30m/1h/6h/1d，非法值返回 6227")
                    @RequestParam(defaultValue = "1m") String bucket) {
        Long userId = currentUserId();
        Device device = requireOwnedDevice(deviceKey, userId);

        PropertyHistoryQueryDTO query = new PropertyHistoryQueryDTO();
        query.setDeviceIds(List.of(device.getId()));
        query.setIdentifiers(splitIdentifiers(identifiers));
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setBucket(bucket);
        return Result.success(propertyHistoryService.query(userId, query));
    }

    @Operation(summary = "外部-查询消息历史", description = "按 deviceKey 与可选 topic / 时间窗分页返回上行消息，"
            + "按上报时间倒序；数据范围限定为密钥所属用户")
    @GetMapping("/devices/{deviceKey}/messages")
    public Result<IPage<HistoryRecord>> getMessages(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Device device = requireOwnedDevice(deviceKey, currentUserId());
        HistoryQueryDTO query = new HistoryQueryDTO();
        query.setDeviceId(device.getId());
        query.setTopic(topic);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setPageNum((int) Math.max(page, 1));
        query.setPageSize((int) Math.min(Math.max(size, 1), 100));
        return Result.success(historyService.getHistoryRecords(query, currentUserId()));
    }

    @Operation(summary = "外部-查询设备日志", description = "统一时间线：消息 / 事件 / 命令 / 连接状态，"
            + "可按 types、关键字与时间窗过滤；types 取 message/event/command/status")
    @GetMapping("/devices/{deviceKey}/logs")
    public Result<IPage<DeviceLogItem>> getDeviceLogs(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        Long userId = currentUserId();
        Device device = requireOwnedDevice(deviceKey, userId);
        DeviceLogQueryDTO query = new DeviceLogQueryDTO();
        query.setTypes(types == null || types.isEmpty() ? List.of() : types);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);
        query.setPageNum((int) Math.max(page, 1));
        query.setPageSize((int) Math.min(Math.max(size, 1), 100));
        return Result.success(deviceLogService.page(userId, device.getId(), query));
    }

    @Operation(summary = "外部-查询消息规则列表", description = "只读返回本人消息规则，可按来源 / 动作 / 启用态 / 关键字过滤；"
            + "不提供规则写操作")
    @GetMapping("/rules")
    public Result<IPage<Map<String, Object>>> listRules(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        RuleQueryDTO query = new RuleQueryDTO();
        query.setSourceType(sourceType);
        query.setActionType(actionType);
        query.setEnabled(enabled);
        query.setKeyword(keyword);
        query.setPageNum((int) Math.max(page, 1));
        query.setPageSize((int) Math.min(Math.max(size, 1), 100));
        IPage<com.mqtt.cloud.entity.RuleDefinition> result = ruleService.page(currentUserId(), query);
        return Result.success(result.convert(this::toRuleMap));
    }

    @Operation(summary = "外部-查询 OTA 固件包列表", description = "只读返回本人固件包，可按 productId 过滤；"
            + "不提供上传 / 删除")
    @GetMapping("/ota/firmwares")
    public Result<List<Map<String, Object>>> listFirmwares(
            @RequestParam(required = false) Long productId) {
        return Result.success(otaFirmwareService.list(currentUserId(), productId).stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", item.getId());
                    map.put("productId", item.getProductId());
                    map.put("version", item.getVersion());
                    map.put("fileName", item.getFileName());
                    map.put("fileSize", item.getFileSize());
                    map.put("md5", item.getMd5());
                    map.put("createdAt", item.getCreatedAt());
                    return map;
                })
                .toList());
    }

    @Operation(summary = "外部-查询 OTA 升级任务列表", description = "只读返回本人升级任务聚合视图；"
            + "不提供建任务 / 暂停 / 终止 / 重投等写操作")
    @GetMapping("/ota/tasks")
    public Result<IPage<OtaTaskDetailVO>> listOtaTasks(
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        IPage<com.mqtt.cloud.dto.response.OtaTaskVO> tasks = otaUpgradeService.listTasks(
                currentUserId(), Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        // 列表接口按批取详情会退化成 N+1，外部侧按聚合字段返回即可；批次明细走单任务端点
        return Result.success(tasks.convert(task -> {
            OtaTaskDetailVO vo = new OtaTaskDetailVO();
            vo.setId(task.getId());
            vo.setName(task.getName());
            vo.setVersion(task.getVersion());
            vo.setProductId(task.getProductId());
            vo.setProductName(task.getProductName());
            vo.setTotalCount(task.getTotalCount());
            vo.setDispatchedCount(task.getDispatchedCount());
            vo.setSuccessCount(task.getSuccessCount());
            vo.setFailedCount(task.getFailedCount());
            vo.setSkippedCount(task.getSkippedCount());
            vo.setStatus(task.getStatus());
            vo.setBatchMode(task.getBatchMode());
            vo.setBatchTotal(task.getBatchTotal());
            vo.setCurrentBatchNo(task.getCurrentBatchNo());
            vo.setCreatedAt(task.getCreatedAt());
            return vo;
        }));
    }

    @Operation(summary = "外部-查询 OTA 升级任务详情与批次", description = "只读返回任务聚合、灰度批次时间线与目标快照；"
            + "逐台记录走独立端点")
    @GetMapping("/ota/tasks/{id}")
    public Result<OtaTaskDetailVO> getOtaTask(
            @Parameter(description = "升级任务 ID", required = true) @PathVariable Long id) {
        return Result.success(otaUpgradeService.detail(currentUserId(), id));
    }

    @Operation(summary = "外部-查询 OTA 任务逐台记录", description = "按任务 ID 分页返回逐台升级记录，"
            + "含批号与固件校验结果；可按 status 过滤")
    @GetMapping("/ota/tasks/{id}/records")
    public Result<IPage<OtaRecordVO>> getOtaRecords(
            @Parameter(description = "升级任务 ID", required = true) @PathVariable Long id,
            @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        return Result.success(otaUpgradeService.listRecords(currentUserId(), id, status,
                Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "外部-查询场景列表", description = "只读返回本人场景定义，可按触发源 / 启用态 / 关键字过滤；"
            + "不提供创建 / 启停 / 试运行")
    @GetMapping("/scenes")
    public Result<IPage<Map<String, Object>>> listScenes(
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") long size) {
        com.mqtt.cloud.dto.request.SceneQuery query = new com.mqtt.cloud.dto.request.SceneQuery();
        query.setTriggerType(triggerType);
        query.setEnabled(enabled);
        query.setKeyword(keyword);
        query.setPageNum((int) Math.max(page, 1));
        query.setPageSize((int) Math.min(Math.max(size, 1), 100));
        return Result.success(sceneService.listScenes(currentUserId(), query)
                .convert(this::toSceneMap));
    }

    @Operation(summary = "外部-获取统计信息", description = "返回 totalDevices/onlineDevices/apiKeyName/permissions")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        List<Device> devices = deviceService.getDevicesByOwner(principal.getUserId());
        long onlineDevices = devices.stream().filter(d -> "ONLINE".equals(d.getStatus())).count();

        ApiKey apiKey = principal.getApiKeyId() != null ? apiKeyService.getById(principal.getApiKeyId()) : null;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDevices", (long) devices.size());
        stats.put("onlineDevices", onlineDevices);
        stats.put("apiKeyName", apiKey != null ? apiKey.getName() : null);
        stats.put("permissions", apiKey != null ? apiKey.getPermissions() : null);
        return Result.success(stats);
    }

    @Operation(summary = "外部-查询告警列表", description = "分页返回密钥所属用户的告警记录，按 last_triggered_at 倒序；"
            + "过滤参数同控制台（status / sourceType / severity / deviceId）")
    @GetMapping("/alerts")
    public Result<IPage<AlertRecord>> listAlerts(AlertQuery query) {
        return Result.success(alertService.page(currentUserId(), query));
    }

    @Operation(summary = "外部-查询告警详情", description = "按 ID 返回本人告警详情，不存在 6209 / 越权 403")
    @GetMapping("/alerts/{id}")
    public Result<AlertRecord> getAlert(
            @Parameter(description = "告警 ID", required = true) @PathVariable Long id) {
        return Result.success(alertService.getOwned(currentUserId(), id));
    }

    @Operation(summary = "外部-查询告警未读数", description = "本人活动告警数")
    @GetMapping("/alerts/unread-count")
    public Result<Long> getAlertUnreadCount() {
        return Result.success(alertService.unreadCount(currentUserId()));
    }

    @Operation(summary = "外部-查询告警规则列表", description = "只读返回本人告警规则，可按来源与启用状态过滤；不提供规则写操作")
    @GetMapping("/alerts/rules")
    public Result<List<AlertRule>> listAlertRules(
            @Parameter(description = "来源过滤：THRESHOLD / OFFLINE / EVENT") @RequestParam(required = false) String sourceType,
            @Parameter(description = "启用状态过滤") @RequestParam(required = false) Boolean enabled) {
        return Result.success(alertRuleService.list(currentUserId(), sourceType, enabled));
    }

    private Long currentUserId() {
        return SecurityUtils.requireUserId();
    }

    /**
     * 按 deviceKey 取设备并校验归属，防止 API Key 跨账号读取或操作他人设备。
     */
    private Device requireOwnedDevice(String deviceKey, Long userId) {
        Device device = deviceService.getDeviceByKey(deviceKey);
        if (!device.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_OWNED);
        }
        return device;
    }

    /** 逗号分隔的标识符串 → 去空白、去空项的列表；入参为空时返回空列表（由服务层决定取全部已建模属性）。 */
    private List<String> splitIdentifiers(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 规则投影：显式挑字段，避免把 target_json / 内部快照直接暴露给第三方。 */
    private Map<String, Object> toRuleMap(com.mqtt.cloud.entity.RuleDefinition rule) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rule.getId());
        map.put("name", rule.getName());
        map.put("description", rule.getDescription());
        map.put("scopeType", rule.getScopeType());
        map.put("sourceType", rule.getSourceType());
        map.put("identifier", rule.getIdentifier());
        map.put("operator", rule.getOperator());
        map.put("thresholdValue", rule.getThresholdValue());
        map.put("conditionLogic", rule.getConditionLogic());
        map.put("actionType", rule.getActionType());
        map.put("cooldownSeconds", rule.getCooldownSeconds());
        map.put("enabled", rule.getEnabled());
        map.put("lastTriggeredAt", rule.getLastTriggeredAt());
        map.put("createdAt", rule.getCreatedAt());
        return map;
    }

    /** 场景投影：显式挑字段，conditionConfig / stepConfig 等 JSON 原文不外泄。 */
    private Map<String, Object> toSceneMap(com.mqtt.cloud.entity.SceneDefinition scene) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", scene.getId());
        map.put("name", scene.getName());
        map.put("description", scene.getDescription());
        map.put("triggerType", scene.getTriggerType());
        map.put("enabled", scene.getEnabled());
        map.put("lastTriggeredAt", scene.getLastTriggeredAt());
        map.put("createdAt", scene.getCreatedAt());
        return map;
    }

    private Map<String, Object> toDeviceMap(Device device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getId());
        map.put("deviceKey", device.getDeviceKey());
        map.put("deviceName", device.getDeviceName());
        map.put("deviceType", device.getDeviceType());
        map.put("topic", device.getTopic());
        map.put("status", device.getStatus());
        map.put("lastSeen", device.getLastSeen());
        return map;
    }
}