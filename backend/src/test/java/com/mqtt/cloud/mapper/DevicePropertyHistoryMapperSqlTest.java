package com.mqtt.cloud.mapper;

import com.mqtt.cloud.dto.response.PropertyHistoryAggregate;
import com.mqtt.cloud.entity.DevicePropertyHistory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 {@code DevicePropertyHistoryMapper.xml} 的 SQL 语义（T-21 设计文档 §7.2）。
 * <p>
 * 仓库当前没有数据库集成测试基建（无 H2 / Testcontainers / test resources），因此这里不做真库落数据，
 * 而是用 MyBatis 的<b>动态 SQL 渲染</b>（{@link MappedStatement#getBoundSql}）+ 结构断言，覆盖实施计划对
 * Mapper 的全部可离线验证点：
 * <ul>
 *   <li>{@code insertBatch}：多行 {@code VALUES} 由 {@code <foreach>} 展开，每行 5 个占位符，
 *       {@code reported_at} 显式绑定 {@code TIMESTAMP}；</li>
 *   <li>{@code aggregateByBucket}：{@code <choose>} 按 {@code numeric} 分流 —— 数值型输出
 *       {@code MIN/MAX/AVG(CAST(...))}，非数值型三列为 {@code NULL} 且不含 {@code CAST}；</li>
 *   <li>分桶表达式 {@code FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(...)/?)?)}、半开区间
 *       {@code reported_at >= ?} / {@code < ?}（不含 {@code <=}）、两处 {@code IN (...)} 展开、
 *       {@code GROUP BY} / {@code ORDER BY} 口径；</li>
 *   <li>{@code purgeBefore}：{@code DELETE ... reported_at < ? LIMIT ?} 单批硬删；</li>
 *   <li>聚合查询<strong>不分页</strong>：无 {@code countQuery} 语句。</li>
 * </ul>
 * 真库落数据下的分桶边界与索引命中，由 P6 端到端实测 A1~A5 兜底。
 */
class DevicePropertyHistoryMapperSqlTest {

    private static final String NAMESPACE = "com.mqtt.cloud.mapper.DevicePropertyHistoryMapper";
    private static final String MAPPER_XML = "/com/mqtt/cloud/mapper/DevicePropertyHistoryMapper.xml";

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 9, 1, 1, 0, 0);

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception {
        // 用最小 Configuration 解析同一份 XML，后续按参数渲染动态 SQL（不建立数据库连接）。
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream in = open()) {
            new XMLMapperBuilder(in, configuration, MAPPER_XML, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(NAMESPACE + ".insertBatch")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + ".aggregateByBucket")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + ".purgeBefore")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + ".countQuery"))
                .as("聚合查询不分页，不应存在 countQuery 语句")
                .isFalse();
    }

    // ---------------------------------------------------------------- insertBatch

    @Test
    void insertBatch_expands_one_value_tuple_per_row() {
        BoundSql boundSql = boundSql("insertBatch", insertParams(2));
        String sql = normalize(boundSql.getSql());

        assertThat(sql)
                .startsWith("INSERT INTO device_property_history")
                .contains("(device_id, identifier, data_type, value_text, reported_at)")
                .contains("VALUES (?, ?, ?, ?, ?) , (?, ?, ?, ?, ?)");
        assertThat(countOccurrences(sql, "?")).as("两行共 10 个占位符").isEqualTo(10);
    }

    @Test
    void insertBatch_renders_single_tuple_for_single_row() {
        String sql = normalize(render("insertBatch", insertParams(1)));

        assertThat(sql).endsWith("VALUES (?, ?, ?, ?, ?)");
        assertThat(countOccurrences(sql, "?")).isEqualTo(5);
    }

    @Test
    void insertBatch_binds_reported_at_as_timestamp() {
        BoundSql boundSql = boundSql("insertBatch", insertParams(2));

        long timestampBindings = boundSql.getParameterMappings().stream()
                .filter(mapping -> mapping.getJdbcType() == JdbcType.TIMESTAMP)
                .count();
        assertThat(timestampBindings)
                .as("每行的 reported_at 都是显式 TIMESTAMP 绑定")
                .isEqualTo(2);
    }

    // ---------------------------------------------------------------- aggregateByBucket 结构

    @Test
    void aggregateByBucket_maps_to_projection_type() {
        Class<?> resultType = configuration.getMappedStatement(NAMESPACE + ".aggregateByBucket")
                .getResultMaps().get(0).getType();

        assertThat(resultType).isEqualTo(PropertyHistoryAggregate.class);
    }

    @Test
    void aggregateByBucket_buckets_time_with_seconds_parameter() {
        String sql = normalize(render("aggregateByBucket", aggregateParams(300L, true)));

        assertThat(sql)
                .as("分桶不做字符串解析，桶宽由 bucketSeconds 参数注入")
                .contains("FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(h.reported_at) / ?) * ?) AS time");
    }

    @Test
    void aggregateByBucket_uses_half_open_window() {
        String sql = normalize(render("aggregateByBucket", aggregateParams(300L, true)));

        assertThat(sql).contains("h.reported_at >= ?").contains("h.reported_at < ?");
        assertThat(sql).as("半开区间不得出现 <= 上界").doesNotContain("<=");
    }

    @Test
    void aggregateByBucket_expands_device_and_identifier_in_clauses() {
        Map<String, Object> params = aggregateParams(300L, true);
        params.put("deviceIds", List.of(1L, 2L, 3L));
        params.put("identifiers", List.of("temp"));

        String sql = normalize(render("aggregateByBucket", params));

        // MyBatis <foreach> 渲染时会在括号与分隔符两侧补空格，这里按真实渲染形态断言
        assertThat(sql).contains("h.device_id IN ( ? , ? , ? )").contains("h.identifier IN ( ? )");
    }

    @Test
    void aggregateByBucket_groups_and_orders_by_device_identifier_time() {
        String sql = normalize(render("aggregateByBucket", aggregateParams(300L, true)));

        assertThat(sql)
                .contains("GROUP BY h.device_id, h.identifier, time")
                .contains("ORDER BY h.device_id ASC, h.identifier ASC, time ASC");
    }

    @Test
    void aggregateByBucket_counts_samples_in_both_branches() {
        assertThat(normalize(render("aggregateByBucket", aggregateParams(300L, true))))
                .contains("COUNT(*) AS sampleCount");
        assertThat(normalize(render("aggregateByBucket", aggregateParams(300L, false))))
                .contains("COUNT(*) AS sampleCount");
    }

    // ---------------------------------------------------------------- aggregateByBucket 数值分流

    @Test
    void numeric_branch_emits_cast_aggregates() {
        String sql = normalize(render("aggregateByBucket", aggregateParams(300L, true)));

        assertThat(sql)
                .contains("MIN(CAST(h.value_text AS DECIMAL(30,10))) AS minValue")
                .contains("MAX(CAST(h.value_text AS DECIMAL(30,10))) AS maxValue")
                .contains("AVG(CAST(h.value_text AS DECIMAL(30,10))) AS avgValue");
    }

    @Test
    void non_numeric_branch_emits_nulls_without_cast() {
        String sql = normalize(render("aggregateByBucket", aggregateParams(300L, false)));

        assertThat(sql)
                .contains("NULL AS minValue, NULL AS maxValue, NULL AS avgValue");
        assertThat(sql)
                .as("非数值型不得对文本列做无谓 CAST")
                .doesNotContain("CAST");
    }

    // ---------------------------------------------------------------- purgeBefore

    @Test
    void purgeBefore_deletes_old_rows_in_batches() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold", END);
        params.put("batchSize", 1000);

        String sql = normalize(render("purgeBefore", params));

        assertThat(sql)
                .startsWith("DELETE FROM device_property_history")
                .contains("reported_at < ?")
                .endsWith("LIMIT ?");
        assertThat(countOccurrences(sql, "?")).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private static InputStream open() {
        return DevicePropertyHistoryMapperSqlTest.class.getResourceAsStream(MAPPER_XML);
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

    private static BoundSql boundSql(String statementId, Map<String, Object> params) {
        return configuration.getMappedStatement(NAMESPACE + "." + statementId).getBoundSql(params);
    }

    private static String render(String statementId, Map<String, Object> params) {
        return boundSql(statementId, params).getSql();
    }

    /** {@code rows} 集合，长度即 VALUES 元组个数。 */
    private static Map<String, Object> insertParams(int rowCount) {
        List<DevicePropertyHistory> rows = new java.util.ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            DevicePropertyHistory row = new DevicePropertyHistory();
            row.setDeviceId(100L + i);
            row.setIdentifier("temp");
            row.setDataType("double");
            row.setValueText("1.0");
            row.setReportedAt(START);
            rows.add(row);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("rows", rows);
        return params;
    }

    /** 聚合参数：默认 2 台设备 × 2 个属性，便于断言 IN 展开。 */
    private static Map<String, Object> aggregateParams(long bucketSeconds, boolean numeric) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceIds", List.of(1L, 2L));
        params.put("identifiers", List.of("temp", "status"));
        params.put("startTime", START);
        params.put("endTime", END);
        params.put("bucketSeconds", bucketSeconds);
        params.put("numeric", numeric);
        return params;
    }
}
