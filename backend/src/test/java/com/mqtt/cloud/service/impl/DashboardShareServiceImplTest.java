package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DashboardProperties;
import com.mqtt.cloud.dto.request.DashboardShareRequest;
import com.mqtt.cloud.dto.response.DashboardSharePublicVO;
import com.mqtt.cloud.dto.response.DashboardShareVO;
import com.mqtt.cloud.entity.Dashboard;
import com.mqtt.cloud.entity.DashboardShare;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DashboardMapper;
import com.mqtt.cloud.mapper.DashboardShareMapper;
import com.mqtt.cloud.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardShareServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long DASHBOARD_ID = 10L;

    private DashboardShareMapper shareMapper;
    private DashboardMapper dashboardMapper;
    private DeviceMapper deviceMapper;
    private DashboardProperties properties;
    private DashboardShareServiceImpl service;

    @BeforeEach
    void setUp() {
        shareMapper = mock(DashboardShareMapper.class);
        dashboardMapper = mock(DashboardMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        properties = new DashboardProperties();
        service = new DashboardShareServiceImpl(shareMapper, dashboardMapper, deviceMapper,
                properties, new ObjectMapper());
    }

    @Test
    void create_should_return_token_once_and_store_hash() {
        when(shareMapper.selectCount(any())).thenReturn(0L);
        when(dashboardMapper.selectBatchIds(anyCollection())).thenReturn(List.of(dashboard(DASHBOARD_ID, USER_ID)));
        when(shareMapper.insert(any(DashboardShare.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, DashboardShare.class).setId(100L);
            return 1;
        });
        DashboardShareRequest request = request("大屏", List.of(DASHBOARD_ID), 30);

        DashboardShareVO result = service.create(USER_ID, request);

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getPath()).isEqualTo("/display/" + result.getToken());
        assertThat(result.getTokenPreview()).hasSize(8);
        ArgumentCaptor<DashboardShare> captor = ArgumentCaptor.forClass(DashboardShare.class);
        verify(shareMapper).insert(captor.capture());
        DashboardShare share = captor.getValue();
        assertThat(share.getTokenHash()).isEqualTo(sha256(result.getToken()));
        assertThat(share.getTokenHash()).isNotEqualTo(result.getToken());
        assertThat(share.getDashboardIds()).isEqualTo("[10]");
        assertThat(share.getEnabled()).isEqualTo(1);
        assertThat(share.getIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void create_should_reject_foreign_dashboard() {
        when(shareMapper.selectCount(any())).thenReturn(0L);
        when(dashboardMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(dashboard(DASHBOARD_ID, OTHER_USER_ID)));

        assertInvalidThrown(() -> service.create(USER_ID, request("大屏", List.of(DASHBOARD_ID), 15)));
    }

    @Test
    void create_should_reject_when_share_limit_exceeded() {
        properties.setMaxShares(1);
        when(shareMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(USER_ID, request("大屏", List.of(DASHBOARD_ID), 15)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DASHBOARD_SHARE_LIMIT_EXCEEDED.getCode());
    }

    @Test
    void create_should_reject_invalid_interval() {
        when(shareMapper.selectCount(any())).thenReturn(0L);
        when(dashboardMapper.selectBatchIds(anyCollection())).thenReturn(List.of(dashboard(DASHBOARD_ID, USER_ID)));

        assertInvalidThrown(() -> service.create(USER_ID, request("大屏", List.of(DASHBOARD_ID), 1)));
        assertInvalidThrown(() -> service.create(USER_ID, request("大屏", List.of(DASHBOARD_ID), 7200)));
    }

    @Test
    void rotate_should_change_hash_and_return_new_token() {
        DashboardShare share = share("old-hash", "oldprevi", USER_ID);
        when(shareMapper.selectById(100L)).thenReturn(share);

        DashboardShareVO result = service.rotate(USER_ID, 100L);

        assertThat(result.getToken()).isNotBlank();
        assertThat(share.getTokenHash()).isEqualTo(sha256(result.getToken()));
        assertThat(share.getTokenHash()).isNotEqualTo("old-hash");
        verify(shareMapper).updateById(share);
    }

    @Test
    void list_should_not_return_plaintext_token() {
        when(shareMapper.selectList(any())).thenReturn(List.of(share("hash", "preview1", USER_ID)));

        List<DashboardShareVO> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getToken()).isNull();
        assertThat(result.get(0).getPath()).isNull();
        assertThat(result.get(0).getTokenPreview()).isEqualTo("preview1");
    }

    @Test
    void publicView_should_return_enabled_share_with_owned_devices_only() {
        DashboardShare share = share(sha256("token"), "tokenpre", USER_ID);
        share.setDashboardIds("[10]");
        share.setIntervalSeconds(20);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(dashboardMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                dashboard(DASHBOARD_ID, USER_ID, "{\"panels\":[{\"title\":\"温度\","
                        + "\"deviceIds\":[100,200],\"identifier\":\"temperature\",\"bucket\":\"5m\","
                        + "\"rangeDays\":1,\"chartType\":\"line\",\"aggregation\":\"avg\"}]}")));
        when(deviceMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                device(100L, USER_ID, "设备A"), device(200L, OTHER_USER_ID, "外部设备")));

        DashboardSharePublicVO result = service.publicView("token");

        assertThat(result.getName()).isEqualTo("产线大屏");
        assertThat(result.getIntervalSeconds()).isEqualTo(20);
        assertThat(result.getDashboards()).hasSize(1);
        assertThat(result.getDashboards().get(0).getConfig().getPanels().get(0).getDeviceIds())
                .containsExactly(100L);
        assertThat(result.getDeviceNames()).containsEntry("100", "设备A")
                .doesNotContainKey("200");
    }

    @Test
    void publicView_should_reject_disabled_or_unknown_token() {
        when(shareMapper.selectOne(any())).thenReturn(null);
        assertShareNotFound(() -> service.publicView("unknown"));

        DashboardShare disabled = share(sha256("token"), "tokenpre", USER_ID);
        disabled.setEnabled(0);
        when(shareMapper.selectOne(any())).thenReturn(disabled);
        assertShareNotFound(() -> service.publicView("token"));
    }

    @Test
    void publicView_should_reject_when_all_dashboards_missing() {
        DashboardShare share = share(sha256("token"), "tokenpre", USER_ID);
        share.setDashboardIds("[10]");
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(dashboardMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        assertShareNotFound(() -> service.publicView("token"));
    }

    private void assertInvalidThrown(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DASHBOARD_SHARE_INVALID.getCode());
    }

    private void assertShareNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DASHBOARD_SHARE_NOT_FOUND.getCode());
    }

    private DashboardShareRequest request(String name, List<Long> ids, int interval) {
        DashboardShareRequest request = new DashboardShareRequest();
        request.setName(name);
        request.setDashboardIds(ids);
        request.setIntervalSeconds(interval);
        return request;
    }

    private DashboardShare share(String hash, String preview, Long userId) {
        DashboardShare share = new DashboardShare();
        share.setId(100L);
        share.setUserId(userId);
        share.setName("产线大屏");
        share.setTokenHash(hash);
        share.setTokenPreview(preview);
        share.setDashboardIds("[10]");
        share.setIntervalSeconds(15);
        share.setEnabled(1);
        return share;
    }

    private Dashboard dashboard(Long id, Long userId) {
        return dashboard(id, userId, "{\"panels\":[]}");
    }

    private Dashboard dashboard(Long id, Long userId, String config) {
        Dashboard dashboard = new Dashboard();
        dashboard.setId(id);
        dashboard.setUserId(userId);
        dashboard.setName("看板" + id);
        dashboard.setConfig(config);
        return dashboard;
    }

    private Device device(Long id, Long ownerId, String name) {
        Device device = new Device();
        device.setId(id);
        device.setOwnerId(ownerId);
        device.setDeviceName(name);
        device.setDeviceKey("dev-" + id);
        return device;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
