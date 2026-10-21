package com.mqtt.cloud.mapper;

import com.mqtt.cloud.entity.DevicePointTable;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 点位 Mapper 注解 SQL 守卫（T-31 P4）。
 * <p>
 * 仓库没有数据库集成测试基建，沿用既有 {@code *MapperSqlTest} 的动态 SQL 渲染做法，
 * 守住「只轮询 PLATFORM 点位」与「采集周期下限钳制」两个最容易写错的语义。
 */
class DevicePointTableMapperSqlTest {

    private static final String NAMESPACE = "com.mqtt.cloud.mapper.DevicePointTableMapper";

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() {
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(DevicePointTableMapper.class);
        assertThat(configuration.hasStatement(NAMESPACE + ".findDuePlatformPoints")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + ".findEnabledByDeviceAndExecutor")).isTrue();
    }

    @Test
    void due_query_should_only_pick_platform_points_and_apply_min_interval() {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", 200);
        params.put("minIntervalMs", 1000);

        String sql = normalize(render("findDuePlatformPoints", params));

        assertThat(sql)
                .contains("p.enabled = 1")
                .contains("p.executor = 'PLATFORM'")
                .contains("TIMESTAMPADD(MICROSECOND")
                .contains("GREATEST(COALESCE(p.read_interval_ms, 60000), ?) * 1000")
                .contains("LIMIT ?");
        assertThat(countOccurrences(sql, "?")).as("minIntervalMs 与 limit 各一次绑定").isEqualTo(2);
    }

    @Test
    void due_query_should_order_by_last_collected_time() {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", 200);
        params.put("minIntervalMs", 1000);

        String sql = normalize(render("findDuePlatformPoints", params));

        assertThat(sql).contains("ORDER BY COALESCE(r.last_at, p.created_at) ASC");
    }

    @Test
    void due_query_should_map_to_point_entity() {
        Class<?> resultType = configuration.getMappedStatement(NAMESPACE + ".findDuePlatformPoints")
                .getResultMaps().get(0).getType();

        assertThat(resultType).isEqualTo(DevicePointTable.class);
    }

    @Test
    void executor_isolation_query_should_filter_executor() {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", 11L);
        params.put("executor", "PLATFORM");

        String sql = normalize(render("findEnabledByDeviceAndExecutor", params));

        assertThat(sql)
                .contains("device_id = ?")
                .contains("executor = ?")
                .contains("enabled = 1");
        assertThat(countOccurrences(sql, "?")).isEqualTo(2);
    }

    private String render(String statement, Map<String, Object> params) {
        MappedStatement mapped = configuration.getMappedStatement(NAMESPACE + "." + statement);
        BoundSql boundSql = mapped.getBoundSql(params);
        return boundSql.getSql();
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
