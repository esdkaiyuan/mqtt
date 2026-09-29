package com.mqtt.cloud.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsMapper {

    /**
     * 按天统计给定设备在最近 days 天内的消息量。
     * <p>
     * 注意：注解式 SQL 中的动态标签必须整体包在 {@code <script>} 里，
     * 且字符串必须以 {@code <script>} 开头（MyBatis 靠前缀判断是否按 XML 解析），
     * 否则 {@code <foreach>} 会被当成普通文本拼进 SQL，导致语法错误。
     */
    @Select("<script>"
            + "SELECT DATE(sent_at) AS date, COUNT(*) AS count FROM message "
            + "WHERE sent_at &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY) "
            + "AND device_id IN "
            + "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY DATE(sent_at) ORDER BY date ASC"
            + "</script>")
    List<Map<String, Object>> countMessagesByDevices(@Param("days") int days,
                                                    @Param("deviceIds") List<Long> deviceIds);
}