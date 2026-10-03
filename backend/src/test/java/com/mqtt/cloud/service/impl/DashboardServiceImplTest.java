package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.config.DashboardProperties;
import com.mqtt.cloud.config.PropertyHistoryProperties;
import com.mqtt.cloud.dto.request.DashboardSaveDTO;
import com.mqtt.cloud.dto.response.DashboardConfig;
import com.mqtt.cloud.dto.response.DashboardDetailVO;
import com.mqtt.cloud.dto.response.DashboardSummaryVO;
import com.mqtt.cloud.entity.Dashboard;
import com.mqtt.cloud.mapper.DashboardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 可保存看板服务单测（T-21 实施计划 P5）。
 * <p>
 * 覆盖四条职责：
 * <ul>
 *   <li><b>归属口径</b>：{@code id == null} / 不存在 / 非本人一律 {@code 6228}（不区分，避免探测）；</li>
 *   <li><b>数量上限</b>：单用户看板数达上限 → {@code 6230}；</li>
 *   <li><b>配置校验与归一化</b>：名称 / 配置空或超长 / JSON 非法 / 面板数超限 → {@code 6229}；
 *       面板缺设备或缺属性 → {@code 6229}；桶 / 图型 / 聚合白名单与默认值（{@code 5m / line / avg}）、
 *       标题默认 {@code 未命名面板}、跨度默认 {@code 1} 天；设备集合去重保序；</li>
 *   <li><b>服务端注入</b>：{@code user_id} 由服务端写入，忽略客户端传值；列表脏配置回退空面板不阻断，
 *       详情脏配置按 {@code 6229} 暴露。</li>
 * </ul>
 * 仓库既有测试不使用 Mockito 静态桩，配置类直接 {@code new}，不 mock。
 */
@SuppressWarnings("unchecked")
class DashboardServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER = 999L;
    private static final Long DASHBOARD_ID = 5L;

    private DashboardMapper dashboardMapper;
    private DashboardProperties properties;
    private PropertyHistoryProperties propertyHistoryProperties;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        dashboardMapper = mock(DashboardMapper.class);
        properties = new DashboardProperties();
        propertyHistoryProperties = new PropertyHistoryProperties();
        service = new DashboardServiceImpl(dashboardMapper, properties,
                propertyHistoryProperties, new ObjectMapper());
    }

    // ---------- 列表 ----------

    @Test
    void list_should_project_summary_and_parse_panel_count() {
        Dashboard row = dashboard(DASHBOARD_ID, USER_ID, panelsJson(2));
        row.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 12, 0, 0));
        when(dashboardMapper.selectList(any())).thenReturn(List.of(row));

        List<DashboardSummaryVO> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(DASHBOARD_ID);
        assertThat(result.get(0).getName()).isEqualTo("board");
        assertThat(result.get(0).getPanelCount()).isEqualTo(2);
        assertThat(result.get(0).getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0, 0));
    }

    @Test
    void list_should_fall_back_to_zero_panels_when_config_dirty() {
        when(dashboardMapper.selectList(any()))
                .thenReturn(List.of(dashboard(1L, USER_ID, "{not-json")));

        List<DashboardSummaryVO> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPanelCount()).isZero();
    }

    @Test
    void list_should_treat_null_panels_as_empty() {
        when(dashboardMapper.selectList(any()))
                .thenReturn(List.of(dashboard(1L, USER_ID, "{\"panels\":null}")));

        List<DashboardSummaryVO> result = service.list(USER_ID);

        assertThat(result.get(0).getPanelCount()).isZero();
    }

    // ---------- 新建：数量上限 ----------

    @Test
    void create_should_reject_when_limit_exceeded() {
        // mybatis-plus selectCount 返回包装型 Long，Mockito 默认 null —— 上限用例必须显式打桩
        when(dashboardMapper.selectCount(any())).thenReturn(20L);

        assertCode(() -> service.create(USER_ID, dto("board", panelsJson(1))),
                ResultCode.DASHBOARD_LIMIT_EXCEEDED);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_pass_when_below_limit() {
        when(dashboardMapper.selectCount(any())).thenReturn(19L);

        DashboardDetailVO detail = service.create(USER_ID, dto("board", panelsJson(1)));

        assertThat(detail.getPanelCount()).isEqualTo(1);
        verify(dashboardMapper).insert(any(Dashboard.class));
    }

    // ---------- 新建：名称与配置 ----------

    @Test
    void create_should_reject_blank_name() {
        assertCode(() -> service.create(USER_ID, dto("   ", panelsJson(1))),
                ResultCode.DASHBOARD_INVALID);
        // 数量上限校验先于名称校验，故 selectCount 会发生；此处只断言未落库
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_name_over_max_length() {
        String tooLong = "a".repeat(65);

        assertCode(() -> service.create(USER_ID, dto(tooLong, panelsJson(1))),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_blank_config() {
        assertCode(() -> service.create(USER_ID, dto("board", "  ")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_invalid_json() {
        assertCode(() -> service.create(USER_ID, dto("board", "{not-json")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_allow_empty_panels() {
        DashboardDetailVO detail = service.create(USER_ID, dto("board", "{}"));

        assertThat(detail.getPanelCount()).isZero();
        verify(dashboardMapper).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_panels_over_limit() {
        // 默认面板上限 12，这里给 13
        assertCode(() -> service.create(USER_ID, dto("board", panelsJson(13))),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    // ---------- 新建：服务端注入 ----------

    @Test
    void create_should_inject_user_id_and_trim_name() {
        service.create(USER_ID, dto("  board  ", panelsJson(1)));

        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardMapper).insert(captor.capture());
        Dashboard saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getName()).isEqualTo("board");
    }

    // ---------- 新建：面板归一化 ----------

    @Test
    void create_should_normalize_panel_defaults() {
        service.create(USER_ID, dto("board",
                "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\"}]}"));

        DashboardConfig.Panel panel = savedConfig().getPanels().get(0);
        assertThat(panel.getTitle()).isEqualTo("未命名面板");
        assertThat(panel.getBucket()).isEqualTo("5m");
        assertThat(panel.getChartType()).isEqualTo("line");
        assertThat(panel.getAggregation()).isEqualTo("avg");
        assertThat(panel.getRangeDays()).isEqualTo(1);
        assertThat(panel.getIdentifier()).isEqualTo("temp");
    }

    @Test
    void create_should_lowercase_whitelisted_enum_fields() {
        service.create(USER_ID, dto("board",
                "{\"panels\":[{\"title\":\"T\",\"deviceIds\":[100],\"identifier\":\"temp\","
                        + "\"bucket\":\"1H\",\"chartType\":\"BAR\",\"aggregation\":\"MAX\"}]}"));

        DashboardConfig.Panel panel = savedConfig().getPanels().get(0);
        assertThat(panel.getBucket()).isEqualTo("1h");
        assertThat(panel.getChartType()).isEqualTo("bar");
        assertThat(panel.getAggregation()).isEqualTo("max");
    }

    @Test
    void create_should_dedupe_panel_device_ids_keeping_order() {
        service.create(USER_ID, dto("board",
                "{\"panels\":[{\"deviceIds\":[100,100,200,100],\"identifier\":\"temp\"}]}"));

        assertThat(savedConfig().getPanels().get(0).getDeviceIds()).containsExactly(100L, 200L);
    }

    @Test
    void create_should_reject_panel_without_device() {
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[],\"identifier\":\"temp\"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_panel_without_identifier() {
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"  \"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_panel_title_over_max_length() {
        String tooLong = "t".repeat(65);
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"title\":\"" + tooLong + "\",\"deviceIds\":[100],\"identifier\":\"temp\"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_unsupported_bucket() {
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\",\"bucket\":\"2m\"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_unsupported_chart_type() {
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\",\"chartType\":\"pie\"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_unsupported_aggregation() {
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\",\"aggregation\":\"sum\"}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_reject_range_days_over_limit() {
        // 默认跨度上限 31 天（读 property-history 配置），这里给 32
        assertCode(() -> service.create(USER_ID, dto("board",
                        "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\",\"rangeDays\":32}]}")),
                ResultCode.DASHBOARD_INVALID);
        verify(dashboardMapper, never()).insert(any(Dashboard.class));
    }

    @Test
    void create_should_default_non_positive_range_days_to_one() {
        service.create(USER_ID, dto("board",
                "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\",\"rangeDays\":0}]}"));

        assertThat(savedConfig().getPanels().get(0).getRangeDays()).isEqualTo(1);
    }

    // ---------- 详情 ----------

    @Test
    void detail_should_reject_null_id() {
        assertCode(() -> service.detail(USER_ID, null), ResultCode.DASHBOARD_NOT_FOUND);
        verifyNoInteractions(dashboardMapper);
    }

    @Test
    void detail_should_reject_missing_row() {
        when(dashboardMapper.selectById(DASHBOARD_ID)).thenReturn(null);

        assertCode(() -> service.detail(USER_ID, DASHBOARD_ID), ResultCode.DASHBOARD_NOT_FOUND);
    }

    @Test
    void detail_should_reject_non_owner() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, OTHER_USER, panelsJson(1)));

        assertCode(() -> service.detail(USER_ID, DASHBOARD_ID), ResultCode.DASHBOARD_NOT_FOUND);
    }

    @Test
    void detail_should_return_parsed_config() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, USER_ID, panelsJson(3)));

        DashboardDetailVO detail = service.detail(USER_ID, DASHBOARD_ID);

        assertThat(detail.getId()).isEqualTo(DASHBOARD_ID);
        assertThat(detail.getPanelCount()).isEqualTo(3);
        assertThat(detail.getConfig().getPanels()).hasSize(3);
    }

    @Test
    void detail_should_expose_strict_parse_failure() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, USER_ID, "{not-json"));

        assertCode(() -> service.detail(USER_ID, DASHBOARD_ID), ResultCode.DASHBOARD_INVALID);
    }

    // ---------- 更新 ----------

    @Test
    void update_should_require_ownership() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, OTHER_USER, panelsJson(1)));

        assertCode(() -> service.update(USER_ID, DASHBOARD_ID, dto("board", panelsJson(1))),
                ResultCode.DASHBOARD_NOT_FOUND);
        verify(dashboardMapper, never()).updateById(any(Dashboard.class));
    }

    @Test
    void update_should_write_normalized_config() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, USER_ID, panelsJson(1)));

        service.update(USER_ID, DASHBOARD_ID, dto("  renamed  ",
                "{\"panels\":[{\"deviceIds\":[100],\"identifier\":\"temp\"}]}"));

        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardMapper).updateById(captor.capture());
        Dashboard updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("renamed");

        DashboardConfig config = read(updated.getConfig());
        assertThat(config.getPanels().get(0).getBucket()).isEqualTo("5m");
    }

    // ---------- 删除 ----------

    @Test
    void remove_should_require_ownership() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, OTHER_USER, panelsJson(1)));

        assertCode(() -> service.remove(USER_ID, DASHBOARD_ID), ResultCode.DASHBOARD_NOT_FOUND);
        verify(dashboardMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void remove_should_hard_delete_owned_row() {
        when(dashboardMapper.selectById(DASHBOARD_ID))
                .thenReturn(dashboard(DASHBOARD_ID, USER_ID, panelsJson(1)));

        service.remove(USER_ID, DASHBOARD_ID);

        verify(dashboardMapper).deleteById(DASHBOARD_ID);
    }

    // ---------- 辅助 ----------

    private DashboardSaveDTO dto(String name, String config) {
        DashboardSaveDTO dto = new DashboardSaveDTO();
        dto.setName(name);
        dto.setConfig(config);
        return dto;
    }

    private Dashboard dashboard(Long id, Long userId, String config) {
        Dashboard dashboard = new Dashboard();
        dashboard.setId(id);
        dashboard.setUserId(userId);
        dashboard.setName("board");
        dashboard.setConfig(config);
        return dashboard;
    }

    /** 生成含 {@code count} 个合法面板的配置 JSON。 */
    private String panelsJson(int count) {
        StringBuilder sb = new StringBuilder("{\"panels\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"title\":\"p").append(i)
                    .append("\",\"deviceIds\":[100],\"identifier\":\"temp\"}");
        }
        return sb.append("]}").toString();
    }

    /** 取最近一次 {@code insert} 落库的配置模型（已规范化）。 */
    private DashboardConfig savedConfig() {
        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardMapper).insert(captor.capture());
        return read(captor.getValue().getConfig());
    }

    private DashboardConfig read(String json) {
        return new ObjectMapper().readValue(json, DashboardConfig.class);
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(com.mqtt.cloud.common.exception.BusinessException.class)
                .extracting(e -> ((com.mqtt.cloud.common.exception.BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}
