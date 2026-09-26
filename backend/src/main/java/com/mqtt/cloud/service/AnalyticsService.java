package com.mqtt.cloud.service;

import java.util.List;
import java.util.Map;

public interface AnalyticsService {

    List<Map<String, Object>> getDeviceStatusDistribution(Long userId);

    List<Map<String, Object>> getDeviceTypeDistribution(Long userId);

    List<Map<String, Object>> getMessageTrend(int days, Long userId);

    Map<String, Object> getOverviewStats(Long userId);
}
