package com.mqtt.cloud.mapper;

import com.mqtt.cloud.entity.OtaUpgradeRecord;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 {@code OtaUpgradeRecordMapper.xml} 与 {@code OtaUpgradeTaskMapper.xml} 的 SQL 语义（T-22 实施计划 P6）。
 * <p>
 * 仓库当前没有数据库集成测试基建（无 H2 / Testcontainers），故沿用 {@code DevicePropertyHistoryMapperSqlTest}
 * 的做法：用 MyBatis <b>动态 SQL 渲染</b>（{@link MappedStatement#getBoundSql}）+ 结构断言，覆盖可离线验证点：
 * <ul>
 *   <li>{@code selectDispatchCandidates}：只取 {@code status='PENDING'} 且设备 {@code status='ONLINE'}，
 *       主键升序，受 {@code LIMIT} 约束；{@code device} 有逻辑删除列，须带 {@code AND d.deleted = 0} 过滤；</li>
 *   <li>{@code selectLatestActiveByDevice}：按 {@code device_id} 等值取「最近一条非终态」记录，
 *       状态集合不含 {@code SUCCESS/FAILED/TIMEOUT}，主键降序 {@code LIMIT 1}；</li>
 *   <li>{@code refreshCounters}：按 {@code task_id} 聚合重算五项计数与任务状态，子查询一次
 *       {@code GROUP BY task_id}，{@code taskId} 绑定两次。</li>
 * </ul>
 * 真库落数据下的索引命中与并发行为，由 P6 端到端实测兜底。
 */
class OtaUpgradeRecordMapperSqlTest {

    private static final String RECORD_NAMESPACE = "com.mqtt.cloud.mapper.OtaUpgradeRecordMapper";
    private static final String TASK_NAMESPACE = "com.mqtt.cloud.mapper.OtaUpgradeTaskMapper";
    private static final String RECORD_XML = "/com/mqtt/cloud/mapper/OtaUpgradeRecordMapper.xml";
    private static final String TASK_XML = "/com/mqtt/cloud/mapper/OtaUpgradeTaskMapper.xml";

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception {
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream in = open(RECORD_XML)) {
            new XMLMapperBuilder(in, configuration, RECORD_XML, configuration.getSqlFragments()).parse();
        }
        try (InputStream in = open(TASK_XML)) {
            new XMLMapperBuilder(in, configuration, TASK_XML, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(RECORD_NAMESPACE + ".selectDispatchCandidates")).isTrue();
        assertThat(configuration.hasStatement(RECORD_NAMESPACE + ".selectLatestActiveByDevice")).isTrue();
        assertThat(configuration.hasStatement(TASK_NAMESPACE + ".refreshCounters")).isTrue();
    }

    // ---------------------------------------------------------------- selectDispatchCandidates

    @Test
    void selectDispatchCandidates_filters_pending_and_online_with_limit() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "PENDING");
        params.put("limit", 200);

        String sql = normalize(render(RECORD_NAMESPACE, "selectDispatchCandidates", params));

        assertThat(sql)
                .contains("FROM ota_upgrade_record r")
                .contains("JOIN device d ON d.id = r.device_id AND d.deleted = 0")
                .contains("WHERE r.status = ? AND d.status = 'ONLINE'")
                .contains("ORDER BY r.id ASC")
                .endsWith("LIMIT ?");
        assertThat(countOccurrences(sql, "?")).as("status 与 limit 各一次绑定").isEqualTo(2);
    }

    @Test
    void selectDispatchCandidates_filters_deleted_devices() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "PENDING");
        params.put("limit", 200);

        String sql = normalize(render(RECORD_NAMESPACE, "selectDispatchCandidates", params));

        assertThat(sql).as("device 有逻辑删除列，须过滤 d.deleted = 0").contains("AND d.deleted = 0");
    }

    @Test
    void selectDispatchCandidates_maps_to_entity() {
        Class<?> resultType = configuration.getMappedStatement(RECORD_NAMESPACE + ".selectDispatchCandidates")
                .getResultMaps().get(0).getType();

        assertThat(resultType).isEqualTo(OtaUpgradeRecord.class);
    }

    // ---------------------------------------------------------------- selectLatestActiveByDevice

    @Test
    void selectLatestActiveByDevice_uses_active_status_set_desc_limit_one() {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", 101L);

        String sql = normalize(render(RECORD_NAMESPACE, "selectLatestActiveByDevice", params));

        assertThat(sql)
                .contains("WHERE device_id = ?")
                .contains("status IN ('PENDING', 'DISPATCHED', 'DOWNLOADING', 'FLASHING')")
                .contains("ORDER BY id DESC")
                .endsWith("LIMIT 1");
        assertThat(countOccurrences(sql, "?")).as("deviceId 单次绑定").isEqualTo(1);
    }

    @Test
    void selectLatestActiveByDevice_excludes_terminal_statuses() {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceId", 101L);

        String sql = normalize(render(RECORD_NAMESPACE, "selectLatestActiveByDevice", params));

        assertThat(sql).as("终态记录不得被归位为进行中").doesNotContain("SUCCESS")
                .doesNotContain("FAILED").doesNotContain("TIMEOUT");
    }

    @Test
    void selectLatestActiveByDevice_maps_to_entity() {
        Class<?> resultType = configuration.getMappedStatement(RECORD_NAMESPACE + ".selectLatestActiveByDevice")
                .getResultMaps().get(0).getType();

        assertThat(resultType).isEqualTo(OtaUpgradeRecord.class);
    }

    // ---------------------------------------------------------------- refreshCounters

    @Test
    void refreshCounters_is_update_statement_aggregating_by_task() {
        MappedStatement statement = configuration.getMappedStatement(TASK_NAMESPACE + ".refreshCounters");

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.UPDATE);

        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));
        assertThat(sql)
                .startsWith("UPDATE ota_upgrade_task t")
                .contains("JOIN (")
                .contains("FROM ota_upgrade_record")
                .contains("WHERE task_id = ?")
                .contains("GROUP BY task_id")
                .contains("WHERE t.id = ?");
        assertThat(countOccurrences(sql, "?")).as("taskId 在子查询与主查询各绑定一次").isEqualTo(2);
    }

    @Test
    void refreshCounters_recomputes_five_counters() {
        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));

        assertThat(sql)
                .contains("COUNT(*) AS total_count")
                .contains("SUM(CASE WHEN status <> 'PENDING' THEN 1 ELSE 0 END) AS dispatched_count")
                .contains("SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count")
                .contains("SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS failed_count");
    }

    @Test
    void refreshCounters_derives_task_status_running_success_failed_partial() {
        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));

        assertThat(sql)
                .contains("THEN 'RUNNING'")
                .contains("THEN 'SUCCESS'")
                .contains("THEN 'FAILED'")
                .contains("ELSE 'PARTIAL'");
    }

    // ---------------------------------------------------------------- helpers

    private static InputStream open(String path) {
        return OtaUpgradeRecordMapperSqlTest.class.getResourceAsStream(path);
    }

    private static BoundSql boundSql(String namespace, String statementId, Map<String, Object> params) {
        return configuration.getMappedStatement(namespace + "." + statementId).getBoundSql(params);
    }

    private static String render(String namespace, String statementId, Map<String, Object> params) {
        return boundSql(namespace, statementId, params).getSql();
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = text.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = text.indexOf(needle, idx + needle.length());
        }
        return count;
    }
}