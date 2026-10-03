package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.config.PropertyHistoryProperties;
import com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO;
import com.mqtt.cloud.dto.response.PropertyHistoryAggregate;
import com.mqtt.cloud.dto.response.PropertySeriesVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyHistory;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DevicePropertyHistoryMapper;
import com.mqtt.cloud.service.PropertyHistoryService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 属性时序服务单测（T-21 实施计划 P5）。
 * <p>
 * 覆盖三条职责：
 * <ul>
 *   <li><b>写入旁路</b>：总开关 / 空样本短路不触达 Mapper；组行字段逐一映射后单次 {@code insertBatch}；</li>
 *   <li><b>只读校验链</b>：设备集合为空 → {@code 6225}，序列数超限 → {@code 6225}，设备级鉴权
 *       （不存在 / 逻辑删除 → {@code 2002}，非本人且非 ADMIN → {@code 2003}，ADMIN 旁路放行）、
 *       标识符物模型缺失 → {@code 6226}，时间格式 / 区间 / 跨度（→ {@code 6225}）、桶白名单与桶数（→ {@code 6227}）；</li>
 *   <li><b>聚合分流与组装</b>：按 {@code numeric} 拆两次 {@code aggregateByBucket}；默认桶 {@code 5m}；
 *       无数据组合仍返回空 {@code points}；投影改名（{@code sampleCount→count}、{@code BigDecimal→Double}、
 *       时间格式化为 {@code yyyy-MM-dd HH:mm:ss}）；越界聚合行安全忽略。</li>
 * </ul>
 * 仓库既有测试不使用 Mockito 静态桩，ADMIN 旁路通过真实 {@code SecurityContextHolder} 注入主体后断言。
 */
@SuppressWarnings("unchecked")
class PropertyHistoryServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER = 999L;
    private static final Long DEVICE_ID = 100L;
    private static final Long OTHER_DEVICE_ID = 888L;
    private static final Long PRODUCT_ID = 1L;

    private static final String START = "2026-09-01 00:00:00";
    private static final String END = "2026-09-01 01:00:00";

    private DeviceMapper deviceMapper;
    private DevicePropertyHistoryMapper historyMapper;
    private ThingModelService thingModelService;
    private PropertyHistoryProperties properties;
    private PropertyHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceMapper = mock(DeviceMapper.class);
        historyMapper = mock(DevicePropertyHistoryMapper.class);
        thingModelService = mock(ThingModelService.class);
        properties = new PropertyHistoryProperties();
        service = new PropertyHistoryServiceImpl(deviceMapper, historyMapper, thingModelService, properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 写入旁路 ----------

    @Test
    void append_should_short_circuit_when_disabled() {
        properties.setEnabled(false);

        service.append(DEVICE_ID, List.of(sample("temp")));

        verifyNoInteractions(historyMapper);
    }

    @Test
    void append_should_short_circuit_on_empty_input() {
        service.append(DEVICE_ID, List.of());
        service.append(DEVICE_ID, null);
        service.append(null, List.of(sample("temp")));

        verifyNoInteractions(historyMapper);
    }

    @Test
    void append_should_map_sample_fields_and_batch_once() {
        LocalDateTime reportedAt = LocalDateTime.of(2026, 9, 1, 12, 30, 15);

        service.append(DEVICE_ID, List.of(new PropertyHistoryService.Sample(
                "temp", "double", "23.5", reportedAt)));

        ArgumentCaptor<List<DevicePropertyHistory>> rows = ArgumentCaptor.forClass(List.class);
        verify(historyMapper).insertBatch(rows.capture());
        assertThat(rows.getValue()).hasSize(1);
        DevicePropertyHistory row = rows.getValue().get(0);
        assertThat(row.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(row.getIdentifier()).isEqualTo("temp");
        assertThat(row.getDataType()).isEqualTo("double");
        assertThat(row.getValueText()).isEqualTo("23.5");
        assertThat(row.getReportedAt()).isEqualTo(reportedAt);
    }

    // ---------- 只读：参数归一化与序列数 ----------

    @Test
    void query_should_reject_empty_device_ids() {
        PropertyHistoryQueryDTO query = query(null, List.of("temp"), START, END, null);

        assertCode(() -> service.query(USER_ID, query), ResultCode.PROPERTY_HISTORY_RANGE_INVALID);
        verifyNoInteractions(deviceMapper, historyMapper, thingModelService);
    }

    @Test
    void query_should_reject_empty_identifiers() {
        // Arrays.asList 容忍 null 元素（List.of 会直接抛 NPE），用于验证空白 / 空标识符被剔除
        PropertyHistoryQueryDTO query = query(List.of(DEVICE_ID), Arrays.asList("  ", null), START, END, null);

        assertCode(() -> service.query(USER_ID, query), ResultCode.PROPERTY_HISTORY_IDENTIFIER_UNSUPPORTED);
        verifyNoInteractions(deviceMapper, historyMapper, thingModelService);
    }

    @Test
    void query_should_reject_series_count_over_limit() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= PropertyHistoryPropertiesDefaults.MAX_SERIES; i++) {
            many.add("p" + i);
        }

        PropertyHistoryQueryDTO query = query(List.of(DEVICE_ID), many, START, END, null);

        assertCode(() -> service.query(USER_ID, query), ResultCode.PROPERTY_HISTORY_RANGE_INVALID);
        // 序列数校验先于归属校验，不得触碰 Mapper
        verifyNoInteractions(deviceMapper, historyMapper);
    }

    // ---------- 只读：设备级鉴权 ----------

    @Test
    void query_should_reject_missing_device() {
        givenProperty("temp", "double");
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        assertCode(() -> service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null)),
                ResultCode.DEVICE_NOT_FOUND);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_logically_deleted_device() {
        Device device = device(USER_ID);
        device.setDeleted(1);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        assertCode(() -> service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null)),
                ResultCode.DEVICE_NOT_FOUND);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_non_owner_without_admin() {
        givenDeviceOf(OTHER_USER);

        assertCode(() -> service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null)),
                ResultCode.DEVICE_NOT_OWNED);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_allow_admin_to_read_other_owner_device() {
        authenticateAsAdmin();
        givenDeviceOf(OTHER_USER);
        givenProperty("temp", "double");

        List<PropertySeriesVO> result =
                service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceId()).isEqualTo(DEVICE_ID);
    }

    // ---------- 只读：标识符与时间窗 ----------

    @Test
    void query_should_reject_identifier_missing_in_thing_model() {
        givenOwnedDevice();
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition(spec("humidity", "double")));

        assertCode(() -> service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null)),
                ResultCode.PROPERTY_HISTORY_IDENTIFIER_UNSUPPORTED);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_bad_time_format() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        assertCode(() -> service.query(USER_ID,
                        query(List.of(DEVICE_ID), List.of("temp"), "2026/09/01", END, null)),
                ResultCode.PROPERTY_HISTORY_RANGE_INVALID);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_start_not_before_end() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        assertCode(() -> service.query(USER_ID,
                        query(List.of(DEVICE_ID), List.of("temp"), START, START, null)),
                ResultCode.PROPERTY_HISTORY_RANGE_INVALID);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_range_over_max_days() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        // 默认跨度上限 31 天，这里给 32 天
        assertCode(() -> service.query(USER_ID,
                        query(List.of(DEVICE_ID), List.of("temp"),
                                "2026-09-01 00:00:00", "2026-10-03 00:00:00", "1d")),
                ResultCode.PROPERTY_HISTORY_RANGE_INVALID);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_accept_range_exactly_at_max_days() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        // 恰好 31 天（start + 31d == end），且 1d 桶仅 31 点，不越点上限
        List<PropertySeriesVO> result = service.query(USER_ID,
                query(List.of(DEVICE_ID), List.of("temp"), "2026-09-01 00:00:00", "2026-10-02 00:00:00", "1d"));

        assertThat(result).hasSize(1);

        ArgumentCaptor<Long> bucketSeconds = ArgumentCaptor.forClass(Long.class);
        verify(historyMapper).aggregateByBucket(anyList(), anyList(), any(), any(), bucketSeconds.capture(), anyBoolean());
        assertThat(bucketSeconds.getValue()).isEqualTo(86400L);
    }

    // ---------- 只读：桶 ----------

    @Test
    void query_should_reject_unsupported_bucket() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        assertCode(() -> service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, "2m")),
                ResultCode.PROPERTY_HISTORY_BUCKET_UNSUPPORTED);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_reject_too_many_points() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        // 1 天跨度、1m 桶 → 1440 点，超过默认 500
        assertCode(() -> service.query(USER_ID,
                        query(List.of(DEVICE_ID), List.of("temp"),
                                "2026-09-01 00:00:00", "2026-09-02 00:00:00", "1m")),
                ResultCode.PROPERTY_HISTORY_BUCKET_UNSUPPORTED);
        verifyNoInteractions(historyMapper);
    }

    @Test
    void query_should_default_bucket_to_5m() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, "  "));

        ArgumentCaptor<Long> bucketSeconds = ArgumentCaptor.forClass(Long.class);
        verify(historyMapper).aggregateByBucket(anyList(), anyList(), any(), any(), bucketSeconds.capture(), anyBoolean());
        assertThat(bucketSeconds.getValue()).isEqualTo(300L);
    }

    // ---------- 只读：numeric 分流 ----------

    @Test
    void query_should_split_numeric_and_non_numeric_into_two_calls() {
        givenOwnedDevice();
        when(thingModelService.getForProduct(PRODUCT_ID))
                .thenReturn(definition(spec("temp", "double"), spec("status", "text")));

        service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp", "status"), START, END, null));

        ArgumentCaptor<List<String>> identifiers = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> numeric = ArgumentCaptor.forClass(Boolean.class);
        verify(historyMapper, times(2))
                .aggregateByBucket(anyList(), identifiers.capture(), any(), any(), anyLong(), numeric.capture());
        assertThat(identifiers.getAllValues()).containsExactly(List.of("temp"), List.of("status"));
        assertThat(numeric.getAllValues()).containsExactly(true, false);
    }

    @Test
    void query_should_skip_non_numeric_call_when_all_numeric() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        service.query(USER_ID, query(List.of(DEVICE_ID), List.of("temp"), START, END, null));

        ArgumentCaptor<Boolean> numeric = ArgumentCaptor.forClass(Boolean.class);
        verify(historyMapper, times(1))
                .aggregateByBucket(anyList(), anyList(), any(), any(), anyLong(), numeric.capture());
        assertThat(numeric.getValue()).isTrue();
    }

    @Test
    void query_should_only_call_non_numeric_when_no_numeric_identifier() {
        givenOwnedDevice();
        when(thingModelService.getForProduct(PRODUCT_ID))
                .thenReturn(definition(spec("status", "enum")));

        service.query(USER_ID, query(List.of(DEVICE_ID), List.of("status"), START, END, null));

        ArgumentCaptor<Boolean> numeric = ArgumentCaptor.forClass(Boolean.class);
        verify(historyMapper, times(1))
                .aggregateByBucket(anyList(), anyList(), any(), any(), anyLong(), numeric.capture());
        assertThat(numeric.getValue()).isFalse();
    }

    // ---------- 只读：组装 ----------

    @Test
    void query_should_dedupe_device_ids_and_identifiers() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        List<PropertySeriesVO> result = service.query(USER_ID,
                query(Arrays.asList(DEVICE_ID, DEVICE_ID, null), List.of("temp", " temp ", " "), START, END, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIdentifier()).isEqualTo("temp");

        ArgumentCaptor<List<Long>> deviceIds = ArgumentCaptor.forClass(List.class);
        verify(historyMapper).aggregateByBucket(deviceIds.capture(), anyList(), any(), any(), anyLong(), anyBoolean());
        assertThat(deviceIds.getValue()).containsExactly(DEVICE_ID);
    }

    @Test
    void query_should_return_empty_points_for_combination_without_data() {
        givenOwnedDevice();
        givenProperty("temp", "double");
        when(historyMapper.aggregateByBucket(anyList(), anyList(), any(), any(), anyLong(), anyBoolean()))
                .thenReturn(List.of());

        List<PropertySeriesVO> result = service.query(USER_ID,
                query(List.of(DEVICE_ID), List.of("temp"), START, END, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isNumeric()).isTrue();
        assertThat(result.get(0).getDataType()).isEqualTo("double");
        assertThat(result.get(0).getPoints()).isEmpty();
    }

    @Test
    void query_should_project_aggregate_row_to_point() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        PropertyHistoryAggregate row = new PropertyHistoryAggregate();
        row.setDeviceId(DEVICE_ID);
        row.setIdentifier("temp");
        row.setTime(LocalDateTime.of(2026, 9, 1, 0, 0, 0));
        row.setSampleCount(4);
        row.setMinValue(new BigDecimal("1.5"));
        row.setMaxValue(new BigDecimal("3.5"));
        row.setAvgValue(new BigDecimal("2.5"));
        when(historyMapper.aggregateByBucket(anyList(), anyList(), any(), any(), anyLong(), anyBoolean()))
                .thenReturn(List.of(row));

        List<PropertySeriesVO> result = service.query(USER_ID,
                query(List.of(DEVICE_ID), List.of("temp"), START, END, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoints()).hasSize(1);
        assertThat(result.get(0).getPoints().get(0).getTime()).isEqualTo("2026-09-01 00:00:00");
        assertThat(result.get(0).getPoints().get(0).getCount()).isEqualTo(4);
        assertThat(result.get(0).getPoints().get(0).getMin()).isEqualTo(1.5);
        assertThat(result.get(0).getPoints().get(0).getMax()).isEqualTo(3.5);
        assertThat(result.get(0).getPoints().get(0).getAvg()).isEqualTo(2.5);
    }

    @Test
    void query_should_ignore_aggregate_rows_outside_requested_combinations() {
        givenOwnedDevice();
        givenProperty("temp", "double");

        PropertyHistoryAggregate stray = new PropertyHistoryAggregate();
        stray.setDeviceId(OTHER_DEVICE_ID);
        stray.setIdentifier("temp");
        stray.setTime(LocalDateTime.of(2026, 9, 1, 0, 0, 0));
        stray.setSampleCount(9);
        when(historyMapper.aggregateByBucket(anyList(), anyList(), any(), any(), anyLong(), anyBoolean()))
                .thenReturn(List.of(stray));

        List<PropertySeriesVO> result = service.query(USER_ID,
                query(List.of(DEVICE_ID), List.of("temp"), START, END, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoints()).isEmpty();
    }

    // ---------- 辅助 ----------

    private PropertyHistoryQueryDTO query(List<Long> deviceIds, List<String> identifiers,
                                          String start, String end, String bucket) {
        PropertyHistoryQueryDTO query = new PropertyHistoryQueryDTO();
        query.setDeviceIds(deviceIds);
        query.setIdentifiers(identifiers);
        query.setStartTime(start);
        query.setEndTime(end);
        query.setBucket(bucket);
        return query;
    }

    private PropertyHistoryService.Sample sample(String identifier) {
        return new PropertyHistoryService.Sample(identifier, "double", "1.0",
                LocalDateTime.of(2026, 9, 1, 0, 0, 0));
    }

    private void givenOwnedDevice() {
        givenDeviceOf(USER_ID);
    }

    private void givenDeviceOf(Long ownerId) {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(ownerId));
    }

    private void givenProperty(String identifier, String type) {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(definition(spec(identifier, type)));
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        device.setDeleted(0);
        device.setProductId(PRODUCT_ID);
        return device;
    }

    private ThingModelDefinition definition(ThingModelDefinition.PropertySpec... specs) {
        Map<String, ThingModelDefinition.PropertySpec> properties = new LinkedHashMap<>();
        for (ThingModelDefinition.PropertySpec spec : specs) {
            properties.put(spec.identifier(), spec);
        }
        return new ThingModelDefinition(1, properties, Map.of(), Map.of());
    }

    private ThingModelDefinition.PropertySpec spec(String identifier, String type) {
        return new ThingModelDefinition.PropertySpec(identifier, type, null, null, false, Set.of(), null, "r");
    }

    private void authenticateAsAdmin() {
        UserPrincipal principal = new UserPrincipal(1L, "admin", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(com.mqtt.cloud.common.exception.BusinessException.class)
                .extracting(e -> ((com.mqtt.cloud.common.exception.BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    /** 常量镜像：避免直接依赖实现类的兜底常量，保持断言口径独立。 */
    private static final class PropertyHistoryPropertiesDefaults {
        private static final int MAX_SERIES = 20;
    }
}
