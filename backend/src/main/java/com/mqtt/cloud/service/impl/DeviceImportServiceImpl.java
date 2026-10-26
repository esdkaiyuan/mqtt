package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.CsvReader;
import com.mqtt.cloud.common.CsvWriter;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DeviceImportProperties;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.dto.response.DeviceImportResultDTO;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceImportService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 设备 CSV 导入实现（T-53）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportServiceImpl implements DeviceImportService {

    static final String COL_DEVICE_KEY = "devicekey";
    static final String COL_DEVICE_NAME = "devicename";
    static final String COL_PRODUCT_KEY = "productkey";
    static final String COL_DEVICE_TYPE = "devicetype";
    static final String COL_TOPIC = "topic";
    static final String COL_DESCRIPTION = "description";
    static final String COL_FIRMWARE_VERSION = "firmwareversion";

    private static final String DEFAULT_DEVICE_TYPE = "sensor";
    private static final String DEVICE_KEY_PATTERN = "^[A-Za-z0-9._-]{1,100}$";

    private final DeviceService deviceService;
    private final ProductService productService;
    private final DeviceImportProperties properties;

    @Override
    public DeviceImportResultDTO importCsv(Long userId, byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "导入文件为空");
        }
        if (content.length > properties.getMaxFileBytes()) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                    "导入文件超过大小上限（" + properties.getMaxFileBytes() + " 字节）");
        }

        List<List<String>> parsed;
        try {
            parsed = CsvReader.parse(content);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                    "CSV 解析失败：" + e.getMessage());
        }
        List<List<String>> rows = parsed.stream()
                .filter(row -> row.stream().anyMatch(cell -> cell != null && !cell.isBlank()))
                .toList();
        if (rows.size() < 2) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                    "CSV 至少需要表头与一行数据");
        }

        Map<String, Integer> headers = parseHeader(rows.get(0));
        requireHeaders(headers);
        List<List<String>> dataRows = rows.subList(1, rows.size());
        if (dataRows.size() > properties.getMaxRows()) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                    "数据行数超过上限（" + properties.getMaxRows() + " 行）");
        }

        Map<String, Product> productCache = new HashMap<>();
        DeviceImportResultDTO result = new DeviceImportResultDTO();
        result.setTotalCount(dataRows.size());

        for (int i = 0; i < dataRows.size(); i++) {
            List<String> row = dataRows.get(i);
            int rowNumber = i + 2;
            String deviceKey = cell(row, headers, COL_DEVICE_KEY);
            try {
                if (deviceKey == null || deviceKey.isBlank()) {
                    throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "deviceKey 不能为空");
                }
                if (!deviceKey.matches(DEVICE_KEY_PATTERN)) {
                    throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                            "deviceKey 仅允许字母、数字、点、中划线、下划线，长度 1~100");
                }
                String productKey = cell(row, headers, COL_PRODUCT_KEY);
                if (productKey == null || productKey.isBlank()) {
                    throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "productKey 不能为空");
                }
                Product product;
                if (productCache.containsKey(productKey)) {
                    // 显式缓存 null：同一份文件里多行引用同一个不存在的产品时不必反复查库
                    product = productCache.get(productKey);
                } else {
                    product = productService.getByProductKey(productKey);
                    productCache.put(productKey, product);
                }
                if (product == null) {
                    throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                            "产品不存在或已删除：" + productKey);
                }

                String deviceName = blankToDefault(cell(row, headers, COL_DEVICE_NAME), deviceKey);
                String deviceType = blankToDefault(cell(row, headers, COL_DEVICE_TYPE), DEFAULT_DEVICE_TYPE);
                String topic = blankToDefault(cell(row, headers, COL_TOPIC),
                        "device/" + deviceKey + "/data");
                String description = cell(row, headers, COL_DESCRIPTION);
                String firmwareVersion = cell(row, headers, COL_FIRMWARE_VERSION);
                validateLengths(deviceName, deviceType, topic, description, firmwareVersion);

                CreateDeviceDTO dto = new CreateDeviceDTO();
                dto.setProductId(product.getId());
                dto.setDeviceKey(deviceKey);
                dto.setDeviceName(deviceName);
                dto.setDeviceType(deviceType);
                dto.setTopic(topic);
                dto.setDescription(description);

                DeviceCreatedDTO created = deviceService.createDevice(userId, dto);
                if (firmwareVersion != null && !firmwareVersion.isBlank()) {
                    // T-54：导入时允许带上已知固件版本，避免迁移后降级拦截对所有设备失效。
                    // 版本回写是尽力而为：设备已创建，不能因附加字段失败把整行判失败。
                    try {
                        deviceService.updateFirmwareVersion(created.getId(), firmwareVersion);
                    } catch (Exception e) {
                        log.warn("设备导入回写固件版本失败 deviceKey={} version={} err={}",
                                deviceKey, firmwareVersion, e.getMessage());
                    }
                }
                DeviceImportResultDTO.Credential credential = new DeviceImportResultDTO.Credential();
                credential.setDeviceKey(created.getDeviceKey());
                credential.setUsername(created.getUsername());
                credential.setSecret(created.getDeviceSecret());
                result.getCredentials().add(credential);
                result.setSucceededCount(result.getSucceededCount() + 1);
            } catch (BusinessException e) {
                addFailure(result, rowNumber, deviceKey, e.getMessage());
            } catch (Exception e) {
                // 单行异常隔离：不暴露堆栈，不影响其余行
                log.warn("设备导入单行失败 row={} deviceKey={} err={}", rowNumber, deviceKey, e.getMessage());
                addFailure(result, rowNumber, deviceKey, "内部错误");
            }
        }
        result.setFailedCount(result.getFailedItems().size());
        log.info("设备导入完成 file={} total={} ok={} fail={}",
                fileName, result.getTotalCount(), result.getSucceededCount(), result.getFailedCount());
        return result;
    }

    @Override
    public byte[] template() {
        List<String> headers = List.of("deviceKey", "deviceName", "productKey",
                "deviceType", "topic", "description", "firmwareVersion");
        List<String> example = List.of("temp-001", "温度传感器-01", "your-product-key",
                "sensor", "device/temp-001/data", "一号车间", "1.0.0");
        return CsvWriter.encode(headers, List.of(example));
    }

    private Map<String, Integer> parseHeader(List<String> headerRow) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String name = headerRow.get(i);
            if (name != null && !name.isBlank()) {
                headers.put(name.trim().toLowerCase(Locale.ROOT), i);
            }
        }
        return headers;
    }

    private void requireHeaders(Map<String, Integer> headers) {
        List<String> required = List.of(COL_DEVICE_KEY, COL_DEVICE_NAME, COL_PRODUCT_KEY);
        List<String> missing = required.stream()
                .filter(column -> !headers.containsKey(column))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID,
                    "缺少必需列：" + String.join(", ", missing));
        }
    }

    private String cell(List<String> row, Map<String, Integer> headers, String column) {
        Integer index = headers.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return value == null ? null : value.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void validateLengths(String deviceName, String deviceType, String topic,
                                 String description, String firmwareVersion) {
        if (deviceName.length() > 100) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "deviceName 长度不能超过 100");
        }
        if (deviceType.length() > 50) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "deviceType 长度不能超过 50");
        }
        if (topic.length() > 255) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "topic 长度不能超过 255");
        }
        if (description != null && description.length() > 500) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "description 长度不能超过 500");
        }
        if (firmwareVersion != null && firmwareVersion.length() > 64) {
            throw new BusinessException(ResultCode.DEVICE_IMPORT_INVALID, "firmwareVersion 长度不能超过 64");
        }
    }

    private void addFailure(DeviceImportResultDTO result, int rowNumber, String deviceKey, String reason) {
        DeviceImportResultDTO.FailedItem item = new DeviceImportResultDTO.FailedItem();
        item.setRowNumber(rowNumber);
        item.setDeviceKey(deviceKey);
        item.setReason(reason);
        result.getFailedItems().add(item);
    }
}