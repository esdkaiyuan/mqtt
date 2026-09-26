package com.mqtt.cloud.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {

    @Select("SELECT status, COUNT(*) as count FROM device WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countDevicesByStatus();

    @Select("SELECT device_type, COUNT(*) as count FROM device WHERE deleted = 0 GROUP BY device_type")
    List<Map<String, Object>> countDevicesByType();

    @Select("SELECT DATE(sent_at) as date, COUNT(*) as count FROM message WHERE sent_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) AND device_id IN <foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY DATE(sent_at) ORDER BY date ASC")
    List<Map<String, Object>> countMessagesByDevices(@Param("days") int days, @Param("deviceIds") List<Long> deviceIds);

    @Select("SELECT DATE(sent_at) as date, COUNT(*) as count FROM message WHERE sent_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) GROUP BY DATE(sent_at) ORDER BY date ASC")
    List<Map<String, Object>> countMessagesByDay(@Param("days") int days);

    @Select("SELECT COUNT(*) as total_devices, (SELECT COUNT(*) FROM device WHERE status = 'ONLINE' AND deleted = 0) as online_devices, (SELECT COUNT(*) FROM message WHERE received_at >= CURDATE()) as today_messages FROM device WHERE deleted = 0")
    Map<String, Object> getOverviewStats();
}
