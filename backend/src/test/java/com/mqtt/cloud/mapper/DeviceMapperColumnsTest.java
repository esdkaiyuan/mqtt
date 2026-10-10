package com.mqtt.cloud.mapper;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫 Mapper 查询列与消费方期望的一致性。
 * <p>
 * 背景：{@code EmqxAuthServiceImpl.loadMeta} 回源认证时依赖 {@code product_id}、{@code enabled}、
 * {@code device_secret_hash} 三个字段。若 {@code findByDeviceKey} 的查询列遗漏其一，字段会被静默映射为
 * null，认证回源即恒失败（全部设备无法连接），而单元测试因 mock 了 Mapper 无法发现。
 * 此测试在默认构建中运行，防止查询列再次漂移。
 */
class DeviceMapperColumnsTest {

    private static final List<String> AUTH_REQUIRED_COLUMNS =
            List.of("product_id", "enabled", "device_secret_hash");

    /** 拓扑两列：授权元数据（AuthMeta）判定委托收发、以及按父列子设备都依赖它们。 */
    private static final List<String> TOPOLOGY_COLUMNS =
            List.of("node_type", "parent_id");

    @Test
    void findByDeviceKey_must_select_columns_required_by_auth_loading() throws Exception {
        List<String> columns = selectColumnsOf("findByDeviceKey");

        assertThat(columns)
                .as("findByDeviceKey 必须选出认证回源所需列，否则认证恒失败")
                .containsAll(AUTH_REQUIRED_COLUMNS);
    }

    @Test
    void findByDeviceKey_must_select_topology_columns() throws Exception {
        assertThat(selectColumnsOf("findByDeviceKey"))
                .as("findByDeviceKey 必须选出拓扑两列，否则 AuthMeta 的 nodeType/parentId 恒为空，委托判定恒失败")
                .containsAll(TOPOLOGY_COLUMNS);
    }

    @Test
    void findByParentId_must_select_topology_columns() throws Exception {
        assertThat(stripTableAliases(selectColumnsOf("findByParentId")))
                .as("findByParentId 必须选出拓扑两列，否则子设备列表无法回显角色与父网关")
                .containsAll(TOPOLOGY_COLUMNS);
    }

    /** 去掉 {@code d.} 之类表别名前缀，使带别名的查询列可与裸列名同一口径比对。 */
    private List<String> stripTableAliases(List<String> columns) {
        List<String> stripped = new ArrayList<>();
        for (String column : columns) {
            int dot = column.lastIndexOf('.');
            stripped.add(dot < 0 ? column : column.substring(dot + 1));
        }
        return stripped;
    }

    private List<String> selectColumnsOf(String statementId) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/com/mqtt/cloud/mapper/DeviceMapper.xml")) {
            assertThat(in).as("未找到 DeviceMapper.xml").isNotNull();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList selects = doc.getElementsByTagName("select");
            for (int i = 0; i < selects.getLength(); i++) {
                Element el = (Element) selects.item(i);
                if (statementId.equals(el.getAttribute("id"))) {
                    // getTextContent 不含注释节点，故注释中出现的列名不会造成误判
                    return splitColumns(el.getTextContent());
                }
            }
            throw new IllegalStateException("未找到 select#" + statementId);
        }
    }

    private List<String> splitColumns(String sql) {
        int selectIdx = sql.toUpperCase().indexOf("SELECT");
        int fromIdx = sql.toUpperCase().indexOf("FROM");
        String columnPart = sql.substring(selectIdx + "SELECT".length(), fromIdx);
        List<String> columns = new ArrayList<>();
        for (String raw : columnPart.split(",")) {
            String name = raw.trim().toLowerCase();
            if (!name.isEmpty()) {
                columns.add(name);
            }
        }
        return columns;
    }
}