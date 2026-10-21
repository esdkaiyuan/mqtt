package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.collect.PointValue;
import com.mqtt.cloud.entity.DevicePointRun;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.mapper.DevicePointRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 点位采集日志统一落库（T-31 P4）。
 * <p>
 * 无论采集成功、失败还是跳过，都必须留痕：现场「值没更新」时，
 * 无记录 = 点位没配，FAILED = 配了但采不到，SKIPPED = 未到周期 / 设备停用。
 * 三者在监控上看起来一样，只有日志能区分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointRunRecorder {

    private static final int MAX_VALUE_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int BATCH_SIZE = 500;

    private final DevicePointRunMapper pointRunMapper;

    /** 记录一轮逐点采集结果（成功 + 失败混排）。 */
    public void record(List<PointValue> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<DevicePointRun> rows = new ArrayList<>(values.size());
        for (PointValue value : values) {
            if (value == null) {
                continue;
            }
            DevicePointRun row = new DevicePointRun();
            row.setPointId(value.pointId());
            row.setDeviceId(value.deviceId());
            row.setStatus(value.success() ? DevicePointRun.STATUS_OK : DevicePointRun.STATUS_FAILED);
            row.setRawValue(truncate(value.rawValue(), MAX_VALUE_LENGTH));
            row.setValue(truncate(value.value(), MAX_VALUE_LENGTH));
            row.setErrorMessage(truncate(value.errorMessage(), MAX_ERROR_LENGTH));
            row.setCollectedAt(value.collectedAt() == null ? now : value.collectedAt());
            rows.add(row);
        }
        insert(rows);
    }

    /** 记录被跳过的点位（设备停用 / 产品协议不匹配 / 未到周期）。 */
    public void skipped(List<DevicePointTable> points, String reason) {
        recordStatus(points, DevicePointRun.STATUS_SKIPPED, reason);
    }

    /** 记录整批失败（连接失败 / 驱动未返回等）。 */
    public void failed(List<DevicePointTable> points, String reason) {
        recordStatus(points, DevicePointRun.STATUS_FAILED, reason);
    }

    private void recordStatus(List<DevicePointTable> points, String status, String reason) {
        if (points == null || points.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<DevicePointRun> rows = new ArrayList<>(points.size());
        for (DevicePointTable point : points) {
            DevicePointRun row = new DevicePointRun();
            row.setPointId(point.getId());
            row.setDeviceId(point.getDeviceId());
            row.setStatus(status);
            row.setErrorMessage(truncate(reason, MAX_ERROR_LENGTH));
            row.setCollectedAt(now);
            rows.add(row);
        }
        insert(rows);
    }

    private void insert(List<DevicePointRun> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, rows.size());
            pointRunMapper.insertBatch(rows.subList(from, to));
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
