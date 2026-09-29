package com.mqtt.cloud.service;

import java.util.List;
import java.util.Map;

public interface AnalyticsService {

    List<Map<String, Object>> getDeviceStatusDistribution(Long userId);

    List<Map<String, Object>> getDeviceTypeDistribution(Long userId);

    List<Map<String, Object>> getMessageTrend(int days, Long userId);

    /** 按天统计全平台消息量（不按用户过滤，对应 `/messages/trend`）。 */
    List<Map<String, Object>> getMessageTrendByDay(int days);

    Map<String, Object> getOverviewStats(Long userId);
}
