package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.HmiProperties;
import com.mqtt.cloud.dto.request.HmiSaveDTO;
import com.mqtt.cloud.dto.response.HmiConfig;
import com.mqtt.cloud.dto.response.HmiDetailVO;
import com.mqtt.cloud.dto.response.HmiDraftVO;
import com.mqtt.cloud.dto.response.HmiSummaryVO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.HmiView;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.HmiViewMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 快速上位机服务单测（T-38）。
 * <p>
 * 覆盖三条主线：生成器映射、保存时按物模型校验、归属隔离。
 * 生成器与校验器共用同一套规则，因此「生成的草稿一定能保存」由本类同时保证。
 */
class HmiViewServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER = 99L;
    private static final Long DEVICE_ID = 11L;
    private static final Long PRODUCT_ID = 20L;
    private static final Long HMI_ID = 5L;

    private HmiViewMapper hmiViewMapper;
    private HmiProperties properties;
    private DeviceService deviceService;
    private ThingModelService thingModelService;
    private HmiViewServiceImpl service;

    @BeforeEach
    void setUp() {
        hmiViewMapper = mock(HmiViewMapper.class);
        properties = new HmiProperties();
        deviceService = mock(DeviceService.class);
        thingModelService = mock(ThingModelService.class);
        service = new HmiViewServiceImpl(hmiViewMapper, properties, deviceService, thingModelService,
                new ObjectMapper());
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(model());
    }

    // ---------- 生成器 ----------

    @Test
    void draft_should_map_property_types_to_widgets() {
        HmiDraftVO draft = service.draft(device(), product());

        assertThat(draft.suggestedName()).isEqualTo("温湿度传感器上位机");
        assertThat(draft.config().getWidgets()).extracting(HmiConfig.Widget::getType)
                .containsExactly("value", "select", "input", "switch", "slider", "chart", "button", "events");
        assertThat(draft.config().getWidgets()).extracting(HmiConfig.Widget::getIdentifier)
                .containsExactly("temperature", "mode", "name", "power", "target", "temperature", "reboot", null);
        assertThat(draft.config().getWidgets().get(4).getMin()).isEqualByComparingTo("16");
        assertThat(draft.config().getWidgets().get(4).getMax()).isEqualByComparingTo("30");
        assertThat(draft.config().getWidgets().get(4).getStep()).isEqualByComparingTo("1");
        assertThat(draft.config().getWidgets().get(7).getSpan()).isEqualTo(12);
        assertThat(draft.notices()).isEmpty();
    }

    @Test
    void draft_should_degrade_to_events_only_when_model_missing() {
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(ThingModelDefinition.EMPTY);

        HmiDraftVO draft = service.draft(device(), product());

        assertThat(draft.config().getWidgets()).extracting(HmiConfig.Widget::getType)
                .containsExactly("events");
        assertThat(draft.notices()).anyMatch(notice -> notice.contains("尚未定义物模型"));
    }

    @Test
    void draft_should_reserve_last_slot_for_events_when_truncated() {
        properties.setMaxWidgets(3);

        HmiDraftVO draft = service.draft(device(), product());

        assertThat(draft.config().getWidgets()).hasSize(3);
        assertThat(draft.config().getWidgets().get(2).getType()).isEqualTo("events");
        assertThat(draft.notices()).anyMatch(notice -> notice.contains("超过上限"));
    }

    // ---------- 保存校验 ----------

    @Test
    void create_should_normalize_and_persist_valid_config() {
        when(hmiViewMapper.selectCount(any())).thenReturn(0L);
        when(hmiViewMapper.insert(any(HmiView.class))).thenAnswer(invocation -> {
            HmiView view = invocation.getArgument(0);
            view.setId(HMI_ID);
            view.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
            return 1;
        });
        when(hmiViewMapper.selectById(HMI_ID)).thenAnswer(invocation -> {
            HmiView view = new HmiView();
            view.setId(HMI_ID);
            view.setUserId(USER_ID);
            view.setDeviceId(DEVICE_ID);
            view.setName("面板");
            view.setCreatedAt(LocalDateTime.of(2026, 10, 21, 10, 0));
            return view;
        });

        HmiDetailVO detail = service.create(USER_ID, device(), product(),
                dto("面板", "{\"widgets\":[{\"type\":\"switch\",\"identifier\":\"power\"},{\"type\":\"events\"}]}"));

        ArgumentCaptor<HmiView> captor = ArgumentCaptor.forClass(HmiView.class);
        verify(hmiViewMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(captor.getValue().getConfig()).contains("\"id\":\"w1\"");
        assertThat(captor.getValue().getConfig()).contains("\"title\":\"power\"");
        assertThat(detail.getId()).isEqualTo(HMI_ID);
        assertThat(detail.getWidgetCount()).isEqualTo(2);
    }

    @Test
    void create_should_reject_unknown_widget_type() {
        when(hmiViewMapper.selectCount(any())).thenReturn(0L);

        assertCode(() -> service.create(USER_ID, device(), product(),
                dto("面板", "{\"widgets\":[{\"type\":\"gauge\",\"identifier\":\"temperature\"}]}")),
                ResultCode.HMI_WIDGET_TYPE_UNSUPPORTED);
        verify(hmiViewMapper, never()).insert(any(HmiView.class));
    }

    @Test
    void create_should_reject_unknown_binding() {
        when(hmiViewMapper.selectCount(any())).thenReturn(0L);

        assertCode(() -> service.create(USER_ID, device(), product(),
                dto("面板", "{\"widgets\":[{\"type\":\"value\",\"identifier\":\"not_exists\"}]}")),
                ResultCode.HMI_BINDING_INVALID);
    }

    @Test
    void create_should_reject_write_widget_on_readonly_property() {
        when(hmiViewMapper.selectCount(any())).thenReturn(0L);

        assertCode(() -> service.create(USER_ID, device(), product(),
                dto("面板", "{\"widgets\":[{\"type\":\"switch\",\"identifier\":\"temperature\"}]}")),
                ResultCode.HMI_BINDING_INVALID);
    }

    @Test
    void create_should_reject_widget_count_over_limit() {
        when(hmiViewMapper.selectCount(any())).thenReturn(0L);
        properties.setMaxWidgets(1);

        String config = "{\"widgets\":[{\"type\":\"value\",\"identifier\":\"temperature\"},{\"type\":\"events\"}]}";

        assertCode(() -> service.create(USER_ID, device(), product(), dto("面板", config)),
                ResultCode.HMI_CONFIG_INVALID);
    }

    @Test
    void create_should_reject_when_view_limit_exceeded() {
        properties.setMaxCount(1);
        when(hmiViewMapper.selectCount(any())).thenReturn(1L);

        assertCode(() -> service.create(USER_ID, device(), product(),
                dto("面板", "{\"widgets\":[{\"type\":\"events\"}]}")), ResultCode.HMI_VIEW_LIMIT_EXCEEDED);
        verify(hmiViewMapper, never()).insert(any(HmiView.class));
    }

    // ---------- 归属与列表 ----------

    @Test
    void detail_should_treat_other_users_view_as_not_found() {
        when(hmiViewMapper.selectById(HMI_ID)).thenReturn(view(OTHER_USER));

        assertCode(() -> service.detail(USER_ID, HMI_ID), ResultCode.HMI_VIEW_NOT_FOUND);
    }

    @Test
    void remove_should_reject_other_users_view() {
        when(hmiViewMapper.selectById(HMI_ID)).thenReturn(view(OTHER_USER));

        assertCode(() -> service.remove(USER_ID, HMI_ID), ResultCode.HMI_VIEW_NOT_FOUND);
        verify(hmiViewMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void list_should_attach_device_names_in_batch() {
        HmiView row = view(USER_ID);
        row.setConfig("{\"widgets\":[{\"type\":\"events\"}]}");
        when(hmiViewMapper.selectList(any())).thenReturn(List.of(row));
        when(deviceService.listByIds(any())).thenReturn(List.of(device()));

        List<HmiSummaryVO> result = service.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeviceName()).isEqualTo("温湿度传感器");
        assertThat(result.get(0).getWidgetCount()).isEqualTo(1);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ResultCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private HmiSaveDTO dto(String name, String config) {
        HmiSaveDTO dto = new HmiSaveDTO();
        dto.setName(name);
        dto.setDeviceId(DEVICE_ID);
        dto.setConfig(config);
        return dto;
    }

    private HmiView view(Long userId) {
        HmiView view = new HmiView();
        view.setId(HMI_ID);
        view.setUserId(userId);
        view.setDeviceId(DEVICE_ID);
        view.setName("面板");
        return view;
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setDeviceKey("sensor-001");
        device.setDeviceName("温湿度传感器");
        device.setProductId(PRODUCT_ID);
        return device;
    }

    private Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductKey("env-sensor");
        return product;
    }

    private ThingModelDefinition model() {
        ThingModelDefinition.PropertySpec temperature = new ThingModelDefinition.PropertySpec(
                "temperature", "float", new BigDecimal("-40"), new BigDecimal("85"), false,
                Set.of(), null, "r");
        ThingModelDefinition.PropertySpec power = new ThingModelDefinition.PropertySpec(
                "power", "bool", null, null, false, Set.of(), null, "rw");
        ThingModelDefinition.PropertySpec mode = new ThingModelDefinition.PropertySpec(
                "mode", "enum", null, null, false, Set.of("auto", "manual"), null, "rw");
        ThingModelDefinition.PropertySpec target = new ThingModelDefinition.PropertySpec(
                "target", "int", new BigDecimal("16"), new BigDecimal("30"), true,
                Set.of(), null, "rw");
        ThingModelDefinition.PropertySpec name = new ThingModelDefinition.PropertySpec(
                "name", "text", null, null, false, Set.of(), 32, "rw");
        ThingModelDefinition.ServiceSpec reboot = new ThingModelDefinition.ServiceSpec(
                "reboot", "async", Map.of(), Map.of());
        ThingModelDefinition.EventSpec overheat = new ThingModelDefinition.EventSpec("overheat", "alert");
        return new ThingModelDefinition(3,
                Map.of("temperature", temperature, "power", power, "mode", mode, "target", target, "name", name),
                Map.of("overheat", overheat),
                Map.of("reboot", reboot));
    }
}
