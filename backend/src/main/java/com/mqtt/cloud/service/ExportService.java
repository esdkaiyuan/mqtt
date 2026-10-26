package com.mqtt.cloud.service;

import com.mqtt.cloud.common.CsvWriter;
import com.mqtt.cloud.dto.request.AuditLogQueryDTO;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;
import com.mqtt.cloud.dto.response.OtaRecordVO;
import com.mqtt.cloud.dto.response.PropertySeriesVO;
import com.mqtt.cloud.entity.AlertRecord;
import com.mqtt.cloud.entity.AuditLog;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV 导出服务（T-36 · X-2）。
 * <p>
 * 覆盖「运营需要把数据拿出去做二次分析」的四类高频场景：设备日志、属性时序、命令记录、告警记录。
 * 编码细节统一收敛在 {@link CsvWriter}（UTF-8 BOM / 公式注入防护 / RFC 4180 转义），本类只负责取数与投影。
 * <p>
 * <b>为什么导出要有行数上限</b>：不分页的导出天然是无界的。十万行日志一次性拼进
 * {@code StringBuilder} 再编码成 byte[]，峰值内存是结果的数倍 —— 一个手滑的时间窗就能把进程打挂。
 * 因此所有导出都在服务层截断（见 {@link #DEFAULT_LIMIT}），并把是否截断如实回传给控制器写进响应头，
 * 让用户知道「导出的不是全部」而不是以为导完了。
 * <p>
 * 本接口位于 {@code service} 包，按 ArchUnit 分层规则**不得依赖 {@code mapper} 包**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    /** 单次导出的默认最大行数；超出部分丢弃并在响应头标注。 */
    public static final int DEFAULT_LIMIT = 10000;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DeviceLogService deviceLogService;
    private final PropertyHistoryService propertyHistoryService;
    private final DeviceCommandService deviceCommandService;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    private final DeviceService deviceService;
    private final ProductService productService;
    private final DeviceGroupTagAssembler deviceGroupTagAssembler;

    /** 导出结果：字节内容 + 文件名 + 是否因行数上限被截断。 */
    public record Export(byte[] content, String fileName, boolean truncated) {
    }

    // ================= 设备日志 =================

    public Export exportDeviceLogs(Long userId, Long deviceId, DeviceLogQueryDTO query, int limit) {
        // 用「最后一页」的方式取尾部若干行：日志按时间倒序，最新的数据才是运营最常看的
        DeviceLogQueryDTO exportQuery = copyLogQuery(query);
        exportQuery.setPageNum(1);
        exportQuery.setPageSize(clampLimit(limit));
        List<DeviceLogItem> items = deviceLogService.page(userId, deviceId, exportQuery).getRecords();

        List<String> headers = List.of("时间", "类型", "方向", "标识符", "子类型", "状态", "标题", "载荷", "主题");
        List<List<String>> rows = items.stream()
                .map(item -> List.of(
                        CsvWriter.time(item.getOccurredAt()),
                        nullToEmpty(item.getLogType()),
                        nullToEmpty(item.getDirection()),
                        nullToEmpty(item.getIdentifier()),
                        nullToEmpty(item.getSubType()),
                        nullToEmpty(item.getStatus()),
                        nullToEmpty(item.getTitle()),
                        nullToEmpty(item.getPayload()),
                        nullToEmpty(item.getTopic())))
                .toList();
        return finish("device-logs", headers, rows, limit);
    }

    // ================= 属性时序 =================

    public Export exportPropertyHistory(Long userId, PropertyHistoryQueryDTO query, int limit) {
        List<PropertySeriesVO> series = propertyHistoryService.query(userId, query);

        // 序列 → 扁平长表：多序列 CSV 的通用形态（一行一个桶）。
        // 数据源是**分桶聚合**结果（min/max/avg/count），不是原始样本，所以列按聚合口径给，
        // 不要臆造「value」列 —— 非数值型的桶里它根本不存在。
        List<String> headers = List.of("设备ID", "标识符", "数据类型", "桶起点", "样本数", "最小值", "最大值", "均值");
        List<List<String>> rows = series.stream()
                .flatMap(item -> {
                    String dataType = item.getDataType() == null ? "" : item.getDataType();
                    return item.getPoints().stream().map(point -> List.of(
                            String.valueOf(item.getDeviceId()),
                            nullToEmpty(item.getIdentifier()),
                            dataType,
                            nullToEmpty(point.getTime()),
                            String.valueOf(point.getCount()),
                            number(point.getMin()),
                            number(point.getMax()),
                            number(point.getAvg())));
                })
                .limit(clampLimit(limit))
                .collect(Collectors.toList());

        String stamp = fileStamp();
        return new Export(CsvWriter.encode(headers, rows),
                "property-history-" + stamp + ".csv", rows.size() >= clampLimit(limit));
    }

    // ================= 命令记录 =================

    /**
     * 导出命令记录。
     * <p>
     * 刻意只暴露 {@code (deviceId, page, limit)} 三个入参：{@code size} 就是行数上限，
     * 让调用方同时传 {@code size} 与 {@code limit} 会出现「两个上限不一致」的歧义。
     */
    public Export exportCommands(Long deviceId, long page, int limit) {
        List<DeviceCommandRecord> records = deviceCommandService.listCommands(
                deviceId, Math.max(page, 1), clampLimit(limit)).getRecords();

        List<String> headers = List.of("命令ID", "命令类型", "标识符", "调用类型", "状态", "参数", "下发时间", "终态时间", "失败原因");
        List<List<String>> rows = records.stream()
                .map(item -> List.of(
                        nullToEmpty(item.getCommandId()),
                        nullToEmpty(item.getCommandType()),
                        nullToEmpty(item.getIdentifier()),
                        nullToEmpty(item.getCallType()),
                        nullToEmpty(item.getStatus()),
                        nullToEmpty(item.getParams()),
                        CsvWriter.time(item.getCreatedAt()),
                        CsvWriter.time(item.getFinishedAt()),
                        nullToEmpty(item.getErrorMessage())))
                .toList();
        return finish("device-commands", headers, rows, limit);
    }

    // ================= 告警记录 =================

    public Export exportAlerts(Long userId, com.mqtt.cloud.dto.request.AlertQuery query, int limit) {
        List<AlertRecord> records = alertService.page(userId, query).getRecords();

        List<String> headers = List.of("告警ID", "设备ID", "设备标识", "规则ID", "规则名", "级别", "状态",
                "标识符", "标题", "触发值", "触发次数", "最近触发", "恢复时间", "处理时间", "处理人", "分派人");
        List<List<String>> rows = records.stream()
                .map(item -> List.of(
                        String.valueOf(item.getId()),
                        String.valueOf(item.getDeviceId()),
                        nullToEmpty(item.getDeviceKey()),
                        String.valueOf(item.getRuleId()),
                        nullToEmpty(item.getRuleName()),
                        nullToEmpty(item.getSeverity()),
                        nullToEmpty(item.getStatus()),
                        nullToEmpty(item.getIdentifier()),
                        nullToEmpty(item.getTitle()),
                        nullToEmpty(item.getTriggerValue()),
                        String.valueOf(item.getTriggerCount()),
                        CsvWriter.time(item.getLastTriggeredAt()),
                        CsvWriter.time(item.getRecoveredAt()),
                        CsvWriter.time(item.getAcknowledgedAt()),
                        String.valueOf(item.getAcknowledgedBy()),
                        String.valueOf(item.getAssigneeId())))
                .toList();
        return finish("alerts", headers, rows, limit);
    }

    // ================= 设备台账 =================

    /**
     * 导出设备台账（不含任何密钥）。
     * <p>
     * 与「导出全部设备凭据」（ADMIN、会重置密钥）严格区分：本接口只导出登记信息，
     * 任何角色都可导出自己可见的设备范围，不会触碰凭据。
     */
    public Export exportDevices(DeviceQueryDTO query, int limit) {
        List<Device> devices = deviceService.listForExport(query, clampLimit(limit));
        deviceGroupTagAssembler.assemble(devices);
        java.util.Map<Long, Product> products = productService.list().stream()
                .collect(Collectors.toMap(Product::getId, product -> product, (a, b) -> a));

        List<String> headers = List.of("设备ID", "设备标识", "设备名称", "产品标识", "产品名称",
                "设备类型", "状态", "连接许可", "拓扑角色", "固件版本", "父网关ID", "分组", "标签",
                "最近心跳", "创建时间", "描述");
        List<List<String>> rows = devices.stream()
                .map(device -> {
                    Product product = products.get(device.getProductId());
                    return List.of(
                            String.valueOf(device.getId()),
                            nullToEmpty(device.getDeviceKey()),
                            nullToEmpty(device.getDeviceName()),
                            product == null ? "" : nullToEmpty(product.getProductKey()),
                            product == null ? "" : nullToEmpty(product.getProductName()),
                            nullToEmpty(device.getDeviceType()),
                            nullToEmpty(device.getStatus()),
                            Integer.valueOf(1).equals(device.getEnabled()) ? "允许" : "禁止",
                            nullToEmpty(device.getNodeType()),
                            nullToEmpty(device.getFirmwareVersion()),
                            device.getParentId() == null ? "" : String.valueOf(device.getParentId()),
                            joinNames(device.getGroups()),
                            joinNames(device.getTags()),
                            CsvWriter.time(device.getLastSeen()),
                            CsvWriter.time(device.getCreatedAt()),
                            nullToEmpty(device.getDescription()));
                })
                .toList();
        return finish("devices", headers, rows, limit);
    }

    // ================= 审计日志 =================

    public Export exportAuditLogs(AuditLogQueryDTO query, int limit) {
        List<AuditLog> logs = auditLogService.listForExport(query, clampLimit(limit));
        List<String> headers = List.of("时间", "用户ID", "用户名", "角色", "来源", "方法", "路径",
                "操作", "资源类型", "资源标识", "状态码", "结果", "错误摘要", "IP", "耗时(ms)");
        List<List<String>> rows = logs.stream()
                .map(log -> List.of(
                        CsvWriter.time(log.getCreatedAt()),
                        log.getUserId() == null ? "" : String.valueOf(log.getUserId()),
                        nullToEmpty(log.getUsername()),
                        nullToEmpty(log.getRole()),
                        nullToEmpty(log.getSource()),
                        nullToEmpty(log.getHttpMethod()),
                        nullToEmpty(log.getPath()),
                        nullToEmpty(log.getAction()),
                        nullToEmpty(log.getResourceType()),
                        nullToEmpty(log.getResourceId()),
                        String.valueOf(log.getStatusCode()),
                        Boolean.TRUE.equals(log.getSuccess()) ? "成功" : "失败",
                        nullToEmpty(log.getErrorMessage()),
                        nullToEmpty(log.getIp()),
                        String.valueOf(log.getDurationMs())))
                .toList();
        return finish("audit-logs", headers, rows, limit);
    }

    // ================= 内部 =================

    /** 分组 / 标签摘要：导出一行一个设备，多个分组用逗号拼接。 */
    private String joinNames(List<? extends Object> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> {
                    if (item instanceof com.mqtt.cloud.entity.DeviceGroup group) {
                        return group.getName();
                    }
                    if (item instanceof com.mqtt.cloud.entity.DeviceTag tag) {
                        return tag.getName();
                    }
                    return null;
                })
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(","));
    }

    private Export finish(String prefix, List<String> headers, List<List<String>> rows, int limit) {
        boolean truncated = rows.size() >= clampLimit(limit);
        return new Export(CsvWriter.encode(headers, rows),
                prefix + "-" + fileStamp() + ".csv", truncated);
    }

    private DeviceLogQueryDTO copyLogQuery(DeviceLogQueryDTO source) {
        DeviceLogQueryDTO query = new DeviceLogQueryDTO();
        if (source == null) {
            return query;
        }
        query.setTypes(source.getTypes());
        query.setStartTime(source.getStartTime());
        query.setEndTime(source.getEndTime());
        query.setKeyword(source.getKeyword());
        return query;
    }

    /** 行数上限收敛到 [1, 100000]：0 / 负数按默认，避免调用方传 0 拿到空文件。 */
    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100000);
    }

    private String fileStamp() {
        return LocalDateTime.now().format(STAMP);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 聚合值为 null（非数值型属性的桶）时导出空单元格，而不是 "null" 字面量。 */
    private static String number(Double value) {
        return value == null ? "" : String.valueOf(value);
    }
}