package com.mqtt.cloud.controller;

import com.mqtt.cloud.common.security.SecurityUtils;
import com.mqtt.cloud.dto.request.AlertQuery;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出控制台接口（T-36 · X-2）。
 * <p>
 * 与既有查询端点**并列**而非替代：控制台表格仍走分页查询（快、可翻页），
 * 导出是一次性拿全量做二次分析的路径（慢、但能带走）。
 * <p>
 * 权限沿用各域既有口径：**设备维度端点做归属校验**（`2003`），告警走 userId 过滤；
 * 导出属读操作，VIEWER 可用。写侧无对应端点。
 * <p>
 * 归属校验复用 {@code ExternalApiController} 的同一套约定：先 `requireOwnedDevice` 拿设备，
 * 再把 userId 交给服务层，避免两处规则漂移。
 */
@Tag(name = "数据导出", description = "把设备日志、属性时序、命令记录与告警记录导出为 CSV")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ExportController {

    /** 截断标记响应头：让用户知道「导出的不是全部」，而不是以为导完了。 */
    private static final String X_EXPORT_TRUNCATED = "X-Export-Truncated";

    private final DeviceService deviceService;
    private final ExportService exportService;

    @Operation(summary = "导出设备日志", description = "按类型 / 关键字 / 时间窗导出统一日志时间线为 CSV；"
            + "结果含 UTF-8 BOM，Excel 直接打开不乱码；超出 limit 时响应头 X-Export-Truncated=true")
    @GetMapping("/devices/{deviceKey}/logs/export")
    public ResponseEntity<byte[]> exportDeviceLogs(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestParam(required = false) List<String> types,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "最大导出行数，默认 10000，上限 100000")
                    @RequestParam(defaultValue = "10000") int limit) {
        Long userId = SecurityUtils.requireUserId();
        Device device = requireOwnedDevice(deviceKey, userId);

        DeviceLogQueryDTO query = new DeviceLogQueryDTO();
        query.setTypes(types == null || types.isEmpty() ? List.of() : types);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setKeyword(keyword);
        return toResponse(exportService.exportDeviceLogs(userId, device.getId(), query, limit));
    }

    @Operation(summary = "导出属性时序", description = "按标识符集合与时间窗导出分桶序列为 CSV（长表：一行一个数据点）；"
            + "identifiers 为空时取该设备全部已建模属性；超出 limit 时响应头 X-Export-Truncated=true")
    @GetMapping("/devices/{deviceKey}/properties/history/export")
    public ResponseEntity<byte[]> exportPropertyHistory(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @RequestParam(required = false) String identifiers,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @Parameter(description = "时间桶：1m/5m/15m/30m/1h/6h/1d，非法值返回 6227")
                    @RequestParam(defaultValue = "5m") String bucket,
            @Parameter(description = "最大导出行数，默认 10000，上限 100000")
                    @RequestParam(defaultValue = "10000") int limit) {
        Long userId = SecurityUtils.requireUserId();
        Device device = requireOwnedDevice(deviceKey, userId);

        PropertyHistoryQueryDTO query = new PropertyHistoryQueryDTO();
        query.setDeviceIds(List.of(device.getId()));
        query.setIdentifiers(splitIdentifiers(identifiers));
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setBucket(bucket);
        return toResponse(exportService.exportPropertyHistory(userId, query, limit));
    }

    @Operation(summary = "导出命令记录", description = "按设备导出命令下发记录为 CSV，含状态、参数与失败原因")
    @GetMapping("/devices/{deviceKey}/commands/export")
    public ResponseEntity<byte[]> exportCommands(
            @Parameter(description = "设备唯一标识", required = true) @PathVariable String deviceKey,
            @Parameter(description = "页码，从 1 起") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "最大导出行数，默认 10000，上限 100000")
                    @RequestParam(defaultValue = "10000") int limit) {
        Long userId = SecurityUtils.requireUserId();
        Device device = requireOwnedDevice(deviceKey, userId);
        return toResponse(exportService.exportCommands(device.getId(), page, limit));
    }

    @Operation(summary = "导出告警记录", description = "按状态 / 级别 / 设备 / 时间窗导出本人告警为 CSV")
    @GetMapping("/alerts/export")
    public ResponseEntity<byte[]> exportAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Boolean openOnly,
            @Parameter(description = "最大导出行数，默认 10000，上限 100000")
                    @RequestParam(defaultValue = "10000") int limit) {
        Long userId = SecurityUtils.requireUserId();
        AlertQuery query = new AlertQuery();
        query.setStatus(status);
        query.setSourceType(sourceType);
        query.setSeverity(severity);
        query.setDeviceId(deviceId);
        query.setOpenOnly(openOnly);
        return toResponse(exportService.exportAlerts(userId, query, limit));
    }

    // ================= 内部 =================

    /**
     * 组装下载响应。
     * <p>
     * 文件名走 {@code ContentDisposition} 的 UTF-8 编码：文件名含中文时（如设备名），
     * 不指定编码会让部分浏览器拿到乱码文件名或直接截断。
     */
    private ResponseEntity<byte[]> toResponse(ExportService.Export export) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(X_EXPORT_TRUNCATED, String.valueOf(export.truncated()))
                .body(export.content());
    }

    private Device requireOwnedDevice(String deviceKey, Long userId) {
        Device device = deviceService.getDeviceByKey(deviceKey);
        if (!device.getOwnerId().equals(userId)) {
            throw new com.mqtt.cloud.common.exception.BusinessException(
                    com.mqtt.cloud.common.ResultCode.DEVICE_NOT_OWNED);
        }
        return device;
    }

    private List<String> splitIdentifiers(String raw) {
        List<String> result = new java.util.ArrayList<>();
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
}