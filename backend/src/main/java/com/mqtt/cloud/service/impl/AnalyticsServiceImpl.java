package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.AnalyticsMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.AnalyticsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsMapper analyticsMapper;
    private final DeviceMapper deviceMapper;

    public AnalyticsServiceImpl(AnalyticsMapper analyticsMapper, DeviceMapper deviceMapper) {
        this.analyticsMapper = analyticsMapper;
        this.deviceMapper = deviceMapper;
    }

    @Override
    public List<Map<String, Object>> getDeviceStatusDistribution(Long userId) {
        Map<String, Long> counts = new HashMap<>();
        for (Device device : deviceMapper.findByOwnerId(userId)) {
            String status = device.getStatus() != null ? device.getStatus() : DeviceStatusValue.INACTIVE;
            counts.merge(status, 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String status : List.of(DeviceStatusValue.ONLINE, DeviceStatusValue.OFFLINE, DeviceStatusValue.INACTIVE)) {
            result.add(countEntry("status", status, counts.getOrDefault(status, 0L)));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getDeviceTypeDistribution(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Device device : deviceMapper.findByOwnerId(userId)) {
            String type = device.getDeviceType() != null ? device.getDeviceType() : "other";
            counts.merge(type, 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((type, count) -> result.add(countEntry("device_type", type, count)));
        return result;
    }

    @Override
    public List<Map<String, Object>> getMessageTrend(int days, Long userId) {
        List<Long> deviceIds = findDeviceIds(userId);
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return analyticsMapper.countMessagesByDevices(days, deviceIds);
    }

    @Override
    public List<Map<String, Object>> getMessageTrendByDay(int days) {
        return analyticsMapper.countMessagesByDay(days);
    }

    @Override
    public Map<String, Object> getOverviewStats(Long userId) {
        List<Device> devices = deviceMapper.findByOwnerId(userId);

        long onlineDevices = devices.stream()
                .filter(d -> DeviceStatusValue.ONLINE.equals(d.getStatus()))
                .count();
        long offlineDevices = devices.stream()
                .filter(d -> DeviceStatusValue.OFFLINE.equals(d.getStatus()))
                .count();

        long todayMessages = 0L;
        List<Long> deviceIds = new ArrayList<>();
        for (Device device : devices) {
            deviceIds.add(device.getId());
        }
        if (!deviceIds.isEmpty()) {
            for (Map<String, Object> row : analyticsMapper.countMessagesByDevices(1, deviceIds)) {
                if (row.get("count") instanceof Number count) {
                    todayMessages += count.longValue();
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDevices", (long) devices.size());
        stats.put("onlineDevices", onlineDevices);
        stats.put("offlineDevices", offlineDevices);
        stats.put("todayMessages", todayMessages);
        return stats;
    }

    private List<Long> findDeviceIds(Long userId) {
        List<Long> deviceIds = new ArrayList<>();
        for (Device device : deviceMapper.findByOwnerId(userId)) {
            deviceIds.add(device.getId());
        }
        return deviceIds;
    }

    private Map<String, Object> countEntry(String key, String label, Long count) {
        Map<String, Object> entry = new HashMap<>();
        entry.put(key, label);
        entry.put("count", count);
        return entry;
    }
}