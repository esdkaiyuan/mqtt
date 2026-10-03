package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mqtt.cloud.common.DeviceLogConstants;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DeviceLogProperties;
import com.mqtt.cloud.dto.request.DeviceLogQueryDTO;
import com.mqtt.cloud.dto.response.DeviceLogItem;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.mapper.DeviceLogMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备统一日志查询服务单测（T-20 实施计划 P6）。
 * <p>
 * 覆盖三段职责：
 * <ul>
 *   <li><b>设备级鉴权</b>：不存在 / 逻辑删除 → {@code 2002}，非本人且非 ADMIN → {@code 2003}，ADMIN 旁路放行；</li>
 *   <li><b>参数归一化与边界</b>：类型白名单（去空白 / 大写 / 去重 / 全空白不过滤 / 非法 {@code 6224}）、
 *       时间解析（{@code yyyy-MM-dd HH:mm:ss} 与 ISO-8601）、时间窗（{@code start < end} 且跨度上限，否则 {@code 6223}）、
 *       分页夹取（配置上限与兜底）、{@code total == 0} 短路；</li>
 *   <li><b>展示回填</b>：标题生成（命令 / 事件 / 上线下线 / 上行下行报文）、关联键
 *       （COMMAND→REQUEST，{@code /reply} 报文→REPLY，非 JSON 载荷安全留空）。</li>
 * </ul>
 * 仓库既有测试不使用 Mockito 静态桩，ADMIN 旁路通过真实 {@code SecurityContextHolder} 注入主体后断言。
 */
@SuppressWarnings("unchecked")
class DeviceLogServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER = 999L;
    private static final Long DEVICE_ID = 100L;

    private DeviceMapper deviceMapper;
    private DeviceLogMapper deviceLogMapper;
    private DeviceLogProperties properties;
    private DeviceLogServiceImpl service;

    @BeforeEach
    void setUp() {
        deviceMapper = mock(DeviceMapper.class);
        deviceLogMapper = mock(DeviceLogMapper.class);
        properties = new DeviceLogProperties();
        service = new DeviceLogServiceImpl(deviceMapper, deviceLogMapper, properties, new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- 设备级鉴权 ----------

    @Test
    void page_should_reject_missing_device() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(null);

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query()), ResultCode.DEVICE_NOT_FOUND);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_reject_logically_deleted_device() {
        Device device = device(USER_ID);
        device.setDeleted(1);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query()), ResultCode.DEVICE_NOT_FOUND);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_reject_non_owner_without_admin() {
        givenDeviceOf(OTHER_USER);

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query()), ResultCode.DEVICE_NOT_OWNED);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_allow_admin_to_read_other_owner_device() {
        authenticateAsAdmin();
        givenDeviceOf(OTHER_USER);
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(0L);

        IPage<DeviceLogItem> result = service.page(USER_ID, DEVICE_ID, query());

        assertThat(result.getTotal()).isZero();
    }

    // ---------- 分页与短路 ----------

    @Test
    void page_should_short_circuit_when_total_zero() {
        givenOwnedDevice();
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(0L);

        IPage<DeviceLogItem> result = service.page(USER_ID, DEVICE_ID, query());

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(deviceLogMapper, never()).pageQuery(any(), any(), any(), any(), any(), anyLong(), anyLong());
    }

    @Test
    void page_should_use_defaults_when_query_is_null() {
        givenOwnedDevice();
        givenNonEmptyPage();

        IPage<DeviceLogItem> result = service.page(USER_ID, DEVICE_ID, null);

        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(20L);
    }

    @Test
    void page_should_clamp_page_num_and_size_to_bounds() {
        givenOwnedDevice();
        givenNonEmptyPage();
        properties.setMaxPageSize(50);

        DeviceLogQueryDTO query = query();
        query.setPageNum(-5);
        query.setPageSize(999);

        IPage<DeviceLogItem> result = service.page(USER_ID, DEVICE_ID, query);

        assertThat(result.getCurrent()).isEqualTo(1L);
        assertThat(result.getSize()).isEqualTo(50L);

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> size = ArgumentCaptor.forClass(Long.class);
        verify(deviceLogMapper).pageQuery(any(), any(), any(), any(), any(), offset.capture(), size.capture());
        assertThat(offset.getValue()).isZero();
        assertThat(size.getValue()).isEqualTo(50L);
    }

    @Test
    void page_should_fall_back_to_default_max_page_size_when_config_not_positive() {
        givenOwnedDevice();
        givenNonEmptyPage();
        properties.setMaxPageSize(0);

        DeviceLogQueryDTO query = query();
        query.setPageSize(999);

        IPage<DeviceLogItem> result = service.page(USER_ID, DEVICE_ID, query);

        assertThat(result.getSize()).isEqualTo((long) DeviceLogConstants.MAX_PAGE_SIZE_FALLBACK);
    }

    @Test
    void page_should_compute_offset_from_page_number() {
        givenOwnedDevice();
        givenNonEmptyPage();

        DeviceLogQueryDTO query = query();
        query.setPageNum(3);
        query.setPageSize(20);

        service.page(USER_ID, DEVICE_ID, query);

        ArgumentCaptor<Long> offset = ArgumentCaptor.forClass(Long.class);
        verify(deviceLogMapper).pageQuery(any(), any(), any(), any(), any(), offset.capture(), anyLong());
        assertThat(offset.getValue()).isEqualTo(40L);
    }

    // ---------- 类型过滤 ----------

    @Test
    void page_should_normalize_and_dedupe_types() {
        givenOwnedDevice();
        givenNonEmptyPage();

        DeviceLogQueryDTO query = query();
        query.setTypes(Arrays.asList("message", " MESSAGE ", "", null, "command"));

        service.page(USER_ID, DEVICE_ID, query);

        ArgumentCaptor<List<String>> types = ArgumentCaptor.forClass(List.class);
        verify(deviceLogMapper).countQuery(any(), types.capture(), any(), any(), any());
        assertThat(types.getValue()).containsExactly("MESSAGE", "COMMAND");
    }

    @Test
    void page_should_drop_type_filter_when_all_blank() {
        givenOwnedDevice();
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(0L);

        DeviceLogQueryDTO query = query();
        query.setTypes(Arrays.asList("", "   "));

        service.page(USER_ID, DEVICE_ID, query);

        ArgumentCaptor<List<String>> types = ArgumentCaptor.forClass(List.class);
        verify(deviceLogMapper).countQuery(any(), types.capture(), any(), any(), any());
        assertThat(types.getValue()).isNull();
    }

    @Test
    void page_should_reject_unsupported_type() {
        givenOwnedDevice();

        DeviceLogQueryDTO query = query();
        query.setTypes(Arrays.asList("MESSAGE", "json"));

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query), ResultCode.DEVICE_LOG_TYPE_UNSUPPORTED);
        verifyNoInteractions(deviceLogMapper);
    }

    // ---------- 时间窗 ----------

    @Test
    void page_should_reject_bad_time_format() {
        givenOwnedDevice();

        DeviceLogQueryDTO query = query();
        query.setStartTime("2026/09/01");

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query), ResultCode.DEVICE_LOG_RANGE_INVALID);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_reject_start_not_before_end() {
        givenOwnedDevice();

        DeviceLogQueryDTO query = query();
        query.setStartTime("2026-09-01 12:00:00");
        query.setEndTime("2026-09-01 12:00:00");

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query), ResultCode.DEVICE_LOG_RANGE_INVALID);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_reject_range_over_max_days() {
        givenOwnedDevice();

        DeviceLogQueryDTO query = query();
        query.setStartTime("2026-09-01 00:00:00");
        query.setEndTime("2026-10-03 00:00:00");

        assertCode(() -> service.page(USER_ID, DEVICE_ID, query), ResultCode.DEVICE_LOG_RANGE_INVALID);
        verifyNoInteractions(deviceLogMapper);
    }

    @Test
    void page_should_accept_iso_time_with_trailing_z() {
        givenOwnedDevice();
        givenNonEmptyPage();

        DeviceLogQueryDTO query = query();
        query.setStartTime("2026-09-01T00:00:00Z");
        query.setEndTime("2026-09-02T00:00:00Z");

        service.page(USER_ID, DEVICE_ID, query);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deviceLogMapper).countQuery(any(), any(), start.capture(), end.capture(), any());
        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 2, 0, 0, 0));
    }

    // ---------- 关键字 ----------

    @Test
    void page_should_wrap_keyword_with_like_and_drop_blank() {
        givenOwnedDevice();
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(0L);

        DeviceLogQueryDTO blank = query();
        blank.setKeyword("   ");
        service.page(USER_ID, DEVICE_ID, blank);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(deviceLogMapper).countQuery(any(), any(), any(), any(), keyword.capture());
        assertThat(keyword.getValue()).isNull();

        DeviceLogQueryDTO padded = query();
        padded.setKeyword("  switch  ");
        service.page(USER_ID, DEVICE_ID, padded);

        ArgumentCaptor<String> wrapped = ArgumentCaptor.forClass(String.class);
        verify(deviceLogMapper, times(2)).countQuery(any(), any(), any(), any(), wrapped.capture());
        assertThat(wrapped.getValue()).isEqualTo("%switch%");
    }

    // ---------- 展示回填 ----------

    @Test
    void decorate_should_map_command_to_request_correlation() {
        DeviceLogItem item = new DeviceLogItem();
        item.setLogType(DeviceLogConstants.TYPE_COMMAND);
        item.setCommandId("cmd-1");
        item.setIdentifier("switch");

        DeviceLogItem decorated = pageSingle(item);

        assertThat(decorated.getTitle()).isEqualTo("命令：switch");
        assertThat(decorated.getCorrelationId()).isEqualTo("cmd-1");
        assertThat(decorated.getCorrelationRole()).isEqualTo(DeviceLogConstants.ROLE_REQUEST);
    }

    @Test
    void decorate_should_fall_back_to_command_id_in_command_title() {
        DeviceLogItem item = new DeviceLogItem();
        item.setLogType(DeviceLogConstants.TYPE_COMMAND);
        item.setCommandId("cmd-2");

        assertThat(pageSingle(item).getTitle()).isEqualTo("命令：cmd-2");
    }

    @Test
    void decorate_should_mark_reply_message_with_reply_correlation() {
        DeviceLogItem item = new DeviceLogItem();
        item.setLogType(DeviceLogConstants.TYPE_MESSAGE);
        item.setTopic("device/dev-1/reply");
        item.setPayload("{\"id\":\"cmd-9\",\"code\":200}");

        DeviceLogItem decorated = pageSingle(item);

        assertThat(decorated.getTitle()).isEqualTo("上行报文：reply");
        assertThat(decorated.getCorrelationId()).isEqualTo("cmd-9");
        assertThat(decorated.getCorrelationRole()).isEqualTo(DeviceLogConstants.ROLE_REPLY);
    }

    @Test
    void decorate_should_leave_message_without_reply_topic_uncorrelated() {
        DeviceLogItem item = new DeviceLogItem();
        item.setLogType(DeviceLogConstants.TYPE_MESSAGE);
        item.setTopic("device/dev-1/data");
        item.setDirection("PUBLISH");
        item.setPayload("{\"id\":\"cmd-9\"}");

        DeviceLogItem decorated = pageSingle(item);

        assertThat(decorated.getTitle()).isEqualTo("下行报文：data");
        assertThat(decorated.getCorrelationId()).isNull();
        assertThat(decorated.getCorrelationRole()).isNull();
    }

    @Test
    void decorate_should_tolerate_non_json_reply_payload() {
        DeviceLogItem notJson = new DeviceLogItem();
        notJson.setLogType(DeviceLogConstants.TYPE_MESSAGE);
        notJson.setTopic("device/dev-1/reply");
        notJson.setPayload("not-json");

        DeviceLogItem arrayPayload = new DeviceLogItem();
        arrayPayload.setLogType(DeviceLogConstants.TYPE_MESSAGE);
        arrayPayload.setTopic("device/dev-1/reply");
        arrayPayload.setPayload("[1,2]");

        assertThat(pageSingle(notJson).getCorrelationId()).isNull();
        assertThat(pageSingle(arrayPayload).getCorrelationId()).isNull();
    }

    @Test
    void decorate_should_build_event_status_and_message_titles() {
        DeviceLogItem event = new DeviceLogItem();
        event.setLogType(DeviceLogConstants.TYPE_EVENT);
        event.setEventType("alert");
        assertThat(pageSingle(event).getTitle()).isEqualTo("事件：alert");

        DeviceLogItem online = new DeviceLogItem();
        online.setLogType(DeviceLogConstants.TYPE_STATUS);
        online.setStatus("ONLINE");
        assertThat(pageSingle(online).getTitle()).isEqualTo("设备上线");

        DeviceLogItem offline = new DeviceLogItem();
        offline.setLogType(DeviceLogConstants.TYPE_STATUS);
        offline.setStatus("OFFLINE");
        assertThat(pageSingle(offline).getTitle()).isEqualTo("设备下线");

        DeviceLogItem changed = new DeviceLogItem();
        changed.setLogType(DeviceLogConstants.TYPE_STATUS);
        changed.setStatus("MAINT");
        assertThat(pageSingle(changed).getTitle()).isEqualTo("状态变更：MAINT");
    }

    // ---------- 辅助 ----------

    private DeviceLogQueryDTO query() {
        return new DeviceLogQueryDTO();
    }

    private void givenOwnedDevice() {
        givenDeviceOf(USER_ID);
    }

    private void givenDeviceOf(Long ownerId) {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(ownerId));
    }

    private void givenNonEmptyPage() {
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(1L);
        when(deviceLogMapper.pageQuery(any(), any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(List.of(new DeviceLogItem()));
    }

    private DeviceLogItem pageSingle(DeviceLogItem item) {
        givenOwnedDevice();
        when(deviceLogMapper.countQuery(any(), any(), any(), any(), any())).thenReturn(1L);
        when(deviceLogMapper.pageQuery(any(), any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(List.of(item));
        return service.page(USER_ID, DEVICE_ID, query()).getRecords().get(0);
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        device.setDeleted(0);
        return device;
    }

    private void authenticateAsAdmin() {
        UserPrincipal principal = new UserPrincipal(1L, "admin", "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}
