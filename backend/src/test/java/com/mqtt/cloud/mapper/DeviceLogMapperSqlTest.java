package com.mqtt.cloud.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 {@code DeviceLogMapper.xml} 的聚合 SQL 语义（T-20 设计文档 §5 / §7.2）。
 * <p>
 * 仓库当前没有数据库集成测试基建（无 H2 / Testcontainers / test resources），因此这里不做真库落数据，
 * 而是用 MyBatis 的<b>动态 SQL 渲染</b>（{@link MappedStatement#getBoundSql}）+ XML 结构断言，
 * 覆盖实施计划对 Mapper 的全部可离线验证点：
 * <ul>
 *   <li>四类来源投影到<strong>同一列集且列顺序一致</strong>，一次查询按 {@code occurred_at DESC, seq DESC} 倒序归并；</li>
 *   <li>类型过滤（{@code log_type IN}）与关键字过滤只在该参数存在时拼接；</li>
 *   <li>时间窗为<strong>半开区间</strong> [startTime, endTime)：{@code >=} 闭、{@code <} 开，四张表各自成立；</li>
 *   <li>{@code countQuery} 与 {@code pageQuery} 复用同一 {@code logFilter}，{@code total} 口径一致；</li>
 *   <li>{@code LIMIT #{offset}, #{size}} 分页拼接正确（仅 pageQuery 带 LIMIT）。</li>
 * </ul>
 * 真库落数据下的行级归并/边界取数，由 P6 端到端实测 A1~A5 兜底。
 */
class DeviceLogMapperSqlTest {

    private static final String NAMESPACE = "com.mqtt.cloud.mapper.DeviceLogMapper";
    private static final String MAPPER_XML = "/com/mqtt/cloud/mapper/DeviceLogMapper.xml";

    /** 四段 UNION ALL 必须投影出的 20 列，顺序严格一致。 */
    private static final List<String> UNION_COLUMNS = List.of(
            "log_id", "log_type", "direction", "occurred_at", "topic", "identifier", "sub_type", "status",
            "payload", "command_id", "call_type", "source", "operator_id", "result", "error_message",
            "attempt_count", "received_at", "finished_at", "qos", "seq");

    /** 各来源表的时间列，半开区间谓词必须逐表成立。 */
    private static final List<String> SOURCE_TIME_COLUMNS =
            List.of("m.sent_at", "c.created_at", "e.reported_at", "s.timestamp");

    private static final Pattern ALIAS = Pattern.compile("\\bAS\\s+([A-Za-z_][A-Za-z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    private static Configuration configuration;

    @BeforeAll
    static void parseMapperXml() throws Exception {
        // 用最小 Configuration 解析同一份 XML，后续按参数渲染动态 SQL（不建立数据库连接）。
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream in = open()) {
            new XMLMapperBuilder(in, configuration, MAPPER_XML, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(NAMESPACE + ".countQuery")).isTrue();
        assertThat(configuration.hasStatement(NAMESPACE + ".pageQuery")).isTrue();
    }

    // ---------------------------------------------------------------- 结构：四段投影列一致

    @Test
    void logUnion_projects_identical_ordered_columns_across_all_four_sources() {
        List<List<String>> segments = unionProjections();

        assertThat(segments)
                .as("四类来源（message / command / event / status）必须各投影为一段")
                .hasSize(4);
        for (int i = 0; i < segments.size(); i++) {
            assertThat(segments.get(i))
                    .as("第 %d 段 UNION 投影列必须与统一列集完全一致且顺序相同", i + 1)
                    .containsExactlyElementsOf(UNION_COLUMNS);
        }
    }

    @Test
    void logUnion_scopes_every_source_to_the_device_and_uses_half_open_window() {
        String union = normalize(sqlFragmentText("logUnion"));

        for (String column : SOURCE_TIME_COLUMNS) {
            assertThat(union)
                    .as("%s 的时间窗下界必须为闭区间（>= startTime）", column)
                    .contains(column + " >= #{startTime}");
            assertThat(union)
                    .as("%s 的时间窗上界必须为开区间（< endTime），否则会重复纳入边界秒", column)
                    .contains(column + " < #{endTime}");
        }
        // 半开区间：不得出现 <= endTime
        assertThat(union).doesNotContain("<=");

        assertThat(union.matches("(?s).*device_id = #\\{deviceId\\}.*"))
                .as("每段都必须按 device_id 限定，方可命中各表既有索引")
                .isTrue();
        assertThat(countOccurrences(union, "device_id = #{deviceId}"))
                .as("四段各一次设备级限定")
                .isEqualTo(4);
        assertThat(countOccurrences(union, "UNION ALL"))
                .as("四段来源由 3 个 UNION ALL 连接")
                .isEqualTo(3);
    }

    // ---------------------------------------------------------------- 渲染：动态条件

    @Test
    void type_filter_only_renders_when_types_present() {
        String withTypes = normalize(render("pageQuery", params(b -> b.types = List.of("MESSAGE", "COMMAND"))));
        assertThat(withTypes)
                .as("<foreach> 应按类型个数展开占位符")
                .contains("t.log_type IN (?,?)");

        String singleType = normalize(render("pageQuery", params(b -> b.types = List.of("EVENT"))));
        assertThat(singleType).contains("t.log_type IN (?)");

        String withoutTypes = normalize(render("pageQuery", params(b -> b.types = null)));
        assertThat(withoutTypes)
                .as("未传类型时不得残留 IN 子句")
                .doesNotContain("log_type IN");

        String emptyTypes = normalize(render("pageQuery", params(b -> b.types = List.of())));
        assertThat(emptyTypes)
                .as("空集合按未传处理，不得拼出 IN () 造成语法错误")
                .doesNotContain("log_type IN");
    }

    @Test
    void keyword_filter_only_renders_when_keyword_present() {
        String sql = normalize(render("pageQuery", params(b -> b.keyword = "%reboot%")));

        assertThat(sql).contains("t.topic LIKE ?")
                .contains("t.identifier LIKE ?")
                .contains("t.error_message LIKE ?")
                .contains("t.payload LIKE ?");

        String withoutKeyword = normalize(render("pageQuery", params(b -> b.keyword = null)));
        assertThat(withoutKeyword).doesNotContain("LIKE");
    }

    @Test
    void time_window_predicates_are_omitted_when_bounds_absent() {
        String sql = normalize(render("pageQuery", params(b -> {
            b.startTime = null;
            b.endTime = null;
        })));

        assertThat(sql).as("未传时间窗时不得残留时间谓词").doesNotContain(">= ?").doesNotContain("sent_at <");
        for (String column : SOURCE_TIME_COLUMNS) {
            assertThat(sql).doesNotContain(column + " >=");
            assertThat(sql).doesNotContain(column + " <");
        }
    }

    @Test
    void both_queries_render_the_same_window_and_ordering_applies_to_page_query() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 2, 0, 0, 0);

        String count = normalize(render("countQuery", params(b -> {
            b.startTime = start;
            b.endTime = end;
        })));
        String page = normalize(render("pageQuery", params(b -> {
            b.startTime = start;
            b.endTime = end;
        })));

        // 复用同一 logUnion/logFilter：计数与明细的 WHERE 口径必须一致
        assertThat(count).contains("SELECT COUNT(*)").contains("UNION ALL");
        assertThat(page).contains("UNION ALL");
        assertThat(countOccurrences(count, "UNION ALL")).isEqualTo(countOccurrences(page, "UNION ALL"));
        for (String column : SOURCE_TIME_COLUMNS) {
            assertThat(count).contains(column + " >= ?").contains(column + " < ?");
            assertThat(page).contains(column + " >= ?").contains(column + " < ?");
        }

        // 排序 + 分页：仅 pageQuery 携带 ORDER BY / LIMIT
        assertThat(page).contains("ORDER BY t.occurred_at DESC, t.seq DESC").contains("LIMIT ?, ?");
        assertThat(count).as("计数语句不得带排序/分页").doesNotContain("ORDER BY").doesNotContain("LIMIT");
    }

    @Test
    void limit_uses_offset_and_size_from_parameters() {
        BoundSql boundSql = boundSql("pageQuery", params(b -> {
            b.offset = 40L;
            b.size = 20L;
        }));
        String sql = normalize(boundSql.getSql());

        assertThat(sql).as("分页必须由 #{offset}, #{size} 提供").endsWith("LIMIT ?, ?");
        // offset 与 size 是两个独立占位符，末尾两位依次绑定
        assertThat(boundSql.getParameterMappings())
                .as("offset/size 各占一个占位符")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private static InputStream open() {
        return DeviceLogMapperSqlTest.class.getResourceAsStream(MAPPER_XML);
    }

    private static Document parse() throws Exception {
        try (InputStream in = open()) {
            assertThat(in).as("未找到 %s", MAPPER_XML).isNotNull();
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }

    /** 取 {@code <sql id=...>} 的纯文本（不含注释节点，故注释中的列名不参与判定）。 */
    private static String sqlFragmentText(String id) {
        try {
            NodeList nodes = parse().getElementsByTagName("sql");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (id.equals(el.getAttribute("id"))) {
                    return el.getTextContent();
                }
            }
            throw new IllegalStateException("未找到 sql#" + id);
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + MAPPER_XML + " 失败", e);
        }
    }

    /** 按 UNION ALL 切分 logUnion，返回每段 SELECT...FROM 之间的投影列别名（保序）。 */
    private static List<List<String>> unionProjections() {
        String union = normalize(sqlFragmentText("logUnion"));
        String[] segments = union.split("(?i)UNION ALL");
        List<List<String>> result = new ArrayList<>();
        for (String segment : segments) {
            String upper = segment.toUpperCase();
            int selectIdx = upper.indexOf("SELECT");
            int fromIdx = upper.indexOf("FROM");
            assertThat(selectIdx).as("每段必须以 SELECT 起始").isGreaterThanOrEqualTo(0);
            assertThat(fromIdx).as("每段必须含 FROM").isGreaterThan(selectIdx);
            String projection = segment.substring(selectIdx + "SELECT".length(), fromIdx);
            List<String> columns = new ArrayList<>();
            Matcher matcher = ALIAS.matcher(projection);
            while (matcher.find()) {
                columns.add(matcher.group(1).toLowerCase());
            }
            result.add(columns);
        }
        return result;
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

    /** 构造一整套查询参数，未显式覆盖的字段取一份「全量」默认值。 */
    private static Map<String, Object> params(java.util.function.Consumer<Params> customizer) {
        Params p = new Params();
        p.deviceId = 7L;
        p.types = List.of("MESSAGE");
        p.startTime = LocalDateTime.of(2026, 10, 1, 0, 0, 0);
        p.endTime = LocalDateTime.of(2026, 10, 2, 0, 0, 0);
        p.keyword = "%kw%";
        p.offset = 0L;
        p.size = 20L;
        customizer.accept(p);

        Map<String, Object> map = new HashMap<>();
        map.put("deviceId", p.deviceId);
        map.put("types", p.types);
        map.put("startTime", p.startTime);
        map.put("endTime", p.endTime);
        map.put("keyword", p.keyword);
        map.put("offset", p.offset);
        map.put("size", p.size);
        return map;
    }

    /** 可写的参数载体，便于在用例内单点覆盖。 */
    private static final class Params {
        private Long deviceId;
        private List<String> types;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String keyword;
        private long offset;
        private long size;
    }
}
