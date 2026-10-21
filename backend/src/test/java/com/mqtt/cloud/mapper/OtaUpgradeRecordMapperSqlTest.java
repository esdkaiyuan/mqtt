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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 {@code OtaUpgradeRecordMapper.xml} / {@code OtaUpgradeTaskMapper.xml} /
 * {@code OtaUpgradeBatchMapper.xml} 的 SQL 语义（T-22 实施计划 P6，T-32 扩灰度与互斥）。
 * <p>
 * 仓库当前没有数据库集成测试基建（无 H2 / Testcontainers），故沿用 {@code DevicePropertyHistoryMapperSqlTest}
 * 的做法：用 MyBatis <b>动态 SQL 渲染</b>（{@link MappedStatement#getBoundSql}）+ 结构断言，覆盖可离线验证点：
 * <ul>
 *   <li>{@code selectDispatchCandidates}：只取 {@code status='PENDING'}、设备 {@code status='ONLINE'}
 *       且所属任务非 {@code ABORTED} 的记录，主键升序，受 {@code LIMIT} 约束；</li>
 *   <li>{@code selectLatestActiveByDevice}：按 {@code device_id} 等值取「最近一条非终态」记录，
 *       状态集合含 {@code VERIFYING}（T-32）但不含终态，主键降序 {@code LIMIT 1}；</li>
 *   <li>{@code refreshCounters}：按 {@code task_id} 聚合重算<b>六项</b>计数与任务状态，
 *       且 {@code PAUSED} / {@code ABORTED} 不被聚合覆盖；</li>
 *   <li>{@code selectDueBatches}：灰度推进的四项前置条件与间隔判定；</li>
 *   <li>{@code refreshBatchCounters}：批次级聚合与 {@code SKIPPED} 保留。</li>
 * </ul>
 * 真库落数据下的索引命中与并发行为，由 P6 端到端实测兜底。
 */
class OtaUpgradeRecordMapperSqlTest {

    private static final String RECORD_NAMESPACE = "com.mqtt.cloud.mapper.OtaUpgradeRecordMapper";
    private static final String TASK_NAMESPACE = "com.mqtt.cloud.mapper.OtaUpgradeTaskMapper";
    private static final String BATCH_NAMESPACE = "com.mqtt.cloud.mapper.OtaUpgradeBatchMapper";
    private static final String RECORD_XML = "/com/mqtt/cloud/mapper/OtaUpgradeRecordMapper.xml";
    private static final String TASK_XML = "/com/mqtt/cloud/mapper/OtaUpgradeTaskMapper.xml";
    private static final String BATCH_XML = "/com/mqtt/cloud/mapper/OtaUpgradeBatchMapper.xml";

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
        try (InputStream in = open(BATCH_XML)) {
            new XMLMapperBuilder(in, configuration, BATCH_XML, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(RECORD_NAMESPACE + ".selectDispatchCandidates")).isTrue();
        assertThat(configuration.hasStatement(RECORD_NAMESPACE + ".selectLatestActiveByDevice")).isTrue();
        assertThat(configuration.hasStatement(TASK_NAMESPACE + ".refreshCounters")).isTrue();
        assertThat(configuration.hasStatement(BATCH_NAMESPACE + ".selectDueBatches")).isTrue();
        assertThat(configuration.hasStatement(BATCH_NAMESPACE + ".refreshBatchCounters")).isTrue();
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
                .contains("status IN ('PENDING', 'DISPATCHED', 'DOWNLOADING', 'FLASHING', 'VERIFYING')")
                .contains("ORDER BY id DESC")
                .endsWith("LIMIT 1");
        assertThat(countOccurrences(sql, "?")).as("deviceId 单次绑定").isEqualTo(1);
    }

    @Test
    void selectDispatchCandidates_skips_aborted_task_records() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "PENDING");
        params.put("limit", 200);

        String sql = normalize(render(RECORD_NAMESPACE, "selectDispatchCandidates", params));

        assertThat(sql).as("终止任务的待投记录不得再被拉起下发").contains("AND t.status <> 'ABORTED'");
    }

    // ---------------------------------------------------------------- 设备级互斥（G-11-4）

    @Test
    void selectActiveDeviceIdsExcludingTask_filters_conflicting_devices() {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceIds", List.of(1L, 2L, 3L));
        params.put("excludeTaskId", 7L);

        String sql = normalize(render(RECORD_NAMESPACE, "selectActiveDeviceIdsExcludingTask", params));

        assertThat(sql)
                .contains("FROM ota_upgrade_record r")
                .contains("r.status IN ('PENDING', 'DISPATCHED', 'DOWNLOADING', 'FLASHING', 'VERIFYING')")
                .contains("AND r.task_id <> ?")
                .contains("AND t.status IN ('RUNNING', 'PAUSED')");
        assertThat(countOccurrences(sql, "?")).as("deviceIds 3 次 + excludeTaskId 1 次").isEqualTo(4);
    }

    @Test
    void selectPendingIdsByTask_filters_pending_only() {
        Map<String, Object> params = new HashMap<>();
        params.put("taskId", 5L);

        String sql = normalize(render(RECORD_NAMESPACE, "selectPendingIdsByTask", params));

        assertThat(sql)
                .contains("WHERE task_id = ? AND status = 'PENDING'")
                .contains("ORDER BY id ASC");
    }

    // ---------------------------------------------------------------- 灰度推进（批次）

    @Test
    void selectDueBatches_requires_running_batched_task_and_next_batch_no() {
        Map<String, Object> params = new HashMap<>();
        params.put("now", LocalDateTime.of(2026, 10, 10, 12, 0));
        params.put("batchIntervalMs", 600_000L);
        params.put("limit", 5);

        String sql = normalize(render(BATCH_NAMESPACE, "selectDueBatches", params));

        assertThat(sql)
                .contains("FROM ota_upgrade_batch b")
                .contains("JOIN ota_upgrade_task t ON t.id = b.task_id")
                .contains("b.status = 'PENDING'")
                .contains("t.status = 'RUNNING'")
                .contains("t.batch_mode = 'BATCHED'")
                .contains("t.batch_interval_ms > 0")
                .contains("b.batch_no = t.current_batch_no + 1");
    }

    @Test
    void selectDueBatches_requires_previous_batch_fully_settled() {
        Map<String, Object> params = new HashMap<>();
        params.put("now", LocalDateTime.of(2026, 10, 10, 12, 0));
        params.put("batchIntervalMs", 600_000L);
        params.put("limit", 5);

        String sql = normalize(render(BATCH_NAMESPACE, "selectDueBatches", params));

        assertThat(sql).as("上一批未收口时不得推进下一批").contains("NOT EXISTS (");
        assertThat(sql)
                .contains("AND r.batch_no = b.batch_no - 1")
                .contains("AND r.status IN ('PENDING', 'DISPATCHED', 'DOWNLOADING', 'FLASHING', 'VERIFYING')");
        assertThat(sql).as("间隔以毫秒换算为微秒参与 TIMESTAMPDIFF").contains(">= ? * 1000");
    }

    @Test
    void refreshBatchCounters_is_update_statement_aggregating_by_task_and_batch() {
        MappedStatement statement =
                configuration.getMappedStatement(BATCH_NAMESPACE + ".refreshBatchCounters");

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.UPDATE);

        String sql = normalize(render(BATCH_NAMESPACE, "refreshBatchCounters",
                Map.of("taskId", 5L, "batchNo", 2)));
        assertThat(sql)
                .startsWith("UPDATE ota_upgrade_batch b")
                .contains("WHERE r.task_id = ? AND r.batch_no = ?")
                .contains("GROUP BY r.task_id, r.batch_no")
                .contains("WHERE b.task_id = ? AND b.batch_no = ?");
        assertThat(countOccurrences(sql, "?")).as("taskId 与 batchNo 在子查询与主查询各一次").isEqualTo(4);
    }

    @Test
    void refreshBatchCounters_preserves_skipped_and_excludes_from_dispatched() {
        String sql = normalize(render(BATCH_NAMESPACE, "refreshBatchCounters",
                Map.of("taskId", 5L, "batchNo", 2)));

        assertThat(sql)
                .as("批次随任务终止后不应被改写成 FAILED")
                .contains("WHEN b.status = 'SKIPPED' THEN 'SKIPPED'")
                .contains("r.status <> 'PENDING' AND r.status <> 'SKIPPED' THEN 1 ELSE 0 END) AS dispatched_count");
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
    void refreshCounters_recomputes_six_counters() {
        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));

        assertThat(sql)
                .contains("COUNT(*) AS total_count")
                .contains("SUM(CASE WHEN status <> 'PENDING' AND status <> 'SKIPPED' "
                        + "THEN 1 ELSE 0 END) AS dispatched_count")
                .contains("SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count")
                .contains("SUM(CASE WHEN status IN ('FAILED', 'TIMEOUT') THEN 1 ELSE 0 END) AS failed_count")
                .contains("SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped_count");
    }

    @Test
    void refreshCounters_preserves_operator_held_status() {
        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));

        assertThat(sql).as("暂停 / 终止是运维意图，不得被聚合结果覆盖")
                .contains("WHEN t.status IN ('PAUSED', 'ABORTED') THEN t.status");
    }

    @Test
    void refreshCounters_treats_verify_as_non_terminal() {
        String sql = normalize(render(TASK_NAMESPACE, "refreshCounters", Map.of("taskId", 5L)));

        assertThat(sql).as("校验中的记录必须算作进行中，否则批次会被误判收口而提前推进")
                .contains("'PENDING', 'DISPATCHED', 'DOWNLOADING', 'FLASHING', 'VERIFYING'");
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