package com.mqtt.cloud.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.filter.UserPrincipal;
import com.mqtt.cloud.service.AlertRuleService;
import com.mqtt.cloud.service.AlertService;
import com.mqtt.cloud.service.ApiKeyService;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceLogService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceShadowService;
import com.mqtt.cloud.service.HistoryService;
import com.mqtt.cloud.service.OtaFirmwareService;
import com.mqtt.cloud.service.OtaUpgradeService;
import com.mqtt.cloud.service.PropertyHistoryService;
import com.mqtt.cloud.service.RuleService;
import com.mqtt.cloud.service.SceneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开放接口控制器单测（T-35 · X-1 开放 API 补齐）。
 * <p>
 * 开放接口此前**没有任何单测**，而它是最需要守护的面：API Key 天然跨账号，
 * 一旦某处漏掉归属校验就是数据泄露。因此本类把重心放在
 * <b>「非归属设备一律 2003，且绝不触达下游服务」</b>，
 * 并验证新增的七域查询都正确透传当前密钥所属用户。
 * <p>
 * 控制器本身不含业务逻辑（全部转发既有服务），故这里不做服务内部行为的重复验证。
 */
@ExtendWith(MockitoExtension.class)
class ExternalApiControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long DEVICE_ID = 101L;
    private static final String DEVICE_KEY = "dev-001";

    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceCommandService deviceCommandService;
    @Mock
    private DeviceShadowService deviceShadowService;
    @Mock
    private AlertRuleService alertRuleService;
    @Mock
    private AlertService alertService;
    @Mock
    private DeviceDataService deviceDataService;
    @Mock
    private PropertyHistoryService propertyHistoryService;
    @Mock
    private HistoryService historyService;
    @Mock
    private DeviceLogService deviceLogService;
    @Mock
    private RuleService ruleService;
    @Mock
    private OtaFirmwareService otaFirmwareService;
    @Mock
    private OtaUpgradeService otaUpgradeService;
    @Mock
    private SceneService sceneService;

    @InjectMocks
    private ExternalApiController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ================= 归属隔离（最关键） =================

    @Test
    void property_latest_should_reject_device_of_other_user() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertThatThrownBy(() -> controller.getPropertyLatest(DEVICE_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.DEVICE_NOT_OWNED.getCode());
        // 拒绝后不得触达下游服务，否则等于「校验失败但数据已读出」
        verify(deviceDataService, never()).getPropertyLatest(anyLong());
    }

    @Test
    void messages_should_reject_device_of_other_user() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertThatThrownBy(() -> controller.getMessages(DEVICE_KEY, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
        verify(historyService, never()).getHistoryRecords(any(), anyLong());
    }

    @Test
    void device_logs_should_reject_device_of_other_user() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertThatThrownBy(() -> controller.getDeviceLogs(DEVICE_KEY, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
        verify(deviceLogService, never()).page(anyLong(), anyLong(), any());
    }

    @Test
    void property_history_should_reject_device_of_other_user() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertThatThrownBy(() -> controller.getPropertyHistory(DEVICE_KEY, "temperature", null, null, "1m"))
                .isInstanceOf(BusinessException.class);
        verify(propertyHistoryService, never()).query(anyLong(), any());
    }

    @Test
    void events_should_reject_device_of_other_user() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(OTHER_USER_ID));

        assertThatThrownBy(() -> controller.getEvents(DEVICE_KEY, 1, 20))
                .isInstanceOf(BusinessException.class);
        verify(deviceDataService, never()).getEvents(anyLong(), anyLong(), anyLong());
    }

    // ================= 正常路径与用户透传 =================

    @Test
    void property_latest_should_return_identifier_value_pairs() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(USER_ID));
        DevicePropertyLatest latest = new DevicePropertyLatest();
        latest.setIdentifier("temperature");
        latest.setDataType("float");
        latest.setValueText("26.5");
        when(deviceDataService.getPropertyLatest(DEVICE_ID)).thenReturn(List.of(latest));

        var result = controller.getPropertyLatest(DEVICE_KEY);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0))
                .containsEntry("identifier", "temperature")
                .containsEntry("value", "26.5");
    }

    @Test
    void property_history_should_pass_current_user_and_device() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(USER_ID));
        when(propertyHistoryService.query(eq(USER_ID), any())).thenReturn(List.of());

        controller.getPropertyHistory(DEVICE_KEY, "temperature,humidity", null, null, "5m");

        verify(propertyHistoryService).query(eq(USER_ID), any(
                com.mqtt.cloud.dto.request.PropertyHistoryQueryDTO.class));
    }

    @Test
    void rules_should_delegate_with_current_user() {
        authenticate();
        when(ruleService.page(eq(USER_ID), any())).thenReturn(new Page<>());

        controller.listRules("PROPERTY", null, null, null, 1, 20);

        verify(ruleService).page(eq(USER_ID), any(com.mqtt.cloud.dto.request.RuleQueryDTO.class));
    }

    @Test
    void firmwares_should_delegate_with_current_user() {
        authenticate();
        when(otaFirmwareService.list(USER_ID, null)).thenReturn(List.of());

        controller.listFirmwares(null);

        verify(otaFirmwareService).list(USER_ID, null);
    }

    @Test
    void ota_tasks_should_delegate_with_current_user() {
        authenticate();
        when(otaUpgradeService.listTasks(eq(USER_ID), anyLong(), anyLong())).thenReturn(new Page<>());

        controller.listOtaTasks(1, 20);

        verify(otaUpgradeService).listTasks(USER_ID, 1L, 20L);
    }

    @Test
    void ota_task_detail_should_delegate_with_current_user() {
        authenticate();
        when(otaUpgradeService.detail(USER_ID, 5L))
                .thenReturn(new com.mqtt.cloud.dto.response.OtaTaskDetailVO());

        controller.getOtaTask(5L);

        verify(otaUpgradeService).detail(USER_ID, 5L);
    }

    @Test
    void scenes_should_delegate_with_current_user() {
        authenticate();
        when(sceneService.listScenes(eq(USER_ID), any())).thenReturn(new Page<>());

        controller.listScenes(null, null, null, 1, 20);

        verify(sceneService).listScenes(eq(USER_ID), any(com.mqtt.cloud.dto.request.SceneQuery.class));
    }

    // ================= 分页边界 =================

    @Test
    void page_size_should_be_capped_at_100() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(USER_ID));
        when(deviceDataService.getEvents(anyLong(), anyLong(), anyLong())).thenReturn(new Page<>());

        controller.getEvents(DEVICE_KEY, 1, 9999);

        verify(deviceDataService).getEvents(DEVICE_ID, 1L, 100L);
    }

    @Test
    void page_should_be_at_least_one() {
        authenticate();
        when(deviceService.getDeviceByKey(DEVICE_KEY)).thenReturn(device(USER_ID));
        when(deviceDataService.getEvents(anyLong(), anyLong(), anyLong())).thenReturn(new Page<>());

        controller.getEvents(DEVICE_KEY, 0, 20);

        verify(deviceDataService).getEvents(DEVICE_ID, 1L, 20L);
    }

    // ================= helper =================

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        device.setDeviceKey(DEVICE_KEY);
        device.setProductId(3L);
        device.setStatus("ONLINE");
        return device;
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(USER_ID, "api-user", "EXTERNAL", 1L),
                        null, List.of()));
    }
}
