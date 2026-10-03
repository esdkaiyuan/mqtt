package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.RuleConstants;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.RuleProperties;
import com.mqtt.cloud.dto.request.RuleRequest;
import com.mqtt.cloud.dto.request.RuleTestRequest;
import com.mqtt.cloud.dto.response.RuleTestResult;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.RuleDefinition;
import com.mqtt.cloud.mapper.RuleDefinitionMapper;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.RuleEvaluationService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 消息规则服务单测（T-19 实施计划 P7）。
 * <p>
 * 覆盖创建 / 更新的集中校验（非法统一 {@code 6219}、非法动作 {@code 6220}、越权设备 {@code 2003}、
 * 超上限 {@code 6222}）、{@code actionConfig} 序列化与反序列化、详情归属（不存在 {@code 6218} /
 * 越权 {@code 403}）、写操作后缓存失效，以及试运行的干跑无副作用。
 */
class RuleServiceImplTest {

    private static final Long USER_ID = 10L;
    private static final Long RULE_ID = 5L;
    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;

    private RuleDefinitionMapper ruleDefinitionMapper;
    private DeviceService deviceService;
    private ThingModelService thingModelService;
    private RuleEvaluationService ruleEvaluationService;
    private RuleProperties properties;
    private RuleServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleDefinitionMapper = mock(RuleDefinitionMapper.class);
        deviceService = mock(DeviceService.class);
        thingModelService = mock(ThingModelService.class);
        ruleEvaluationService = mock(RuleEvaluationService.class);
        properties = new RuleProperties();
        service = new RuleServiceImpl(deviceService, thingModelService, ruleEvaluationService,
                properties, new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseMapper", ruleDefinitionMapper);
    }

    // ---------- 创建校验 ----------

    @Test
    void create_should_reject_when_rule_limit_exceeded() {
        properties.setMaxRulesPerUser(1);
        when(ruleDefinitionMapper.countByUser(USER_ID)).thenReturn(1);

        assertCode(() -> service.create(USER_ID, propertyRequest()), ResultCode.RULE_LIMIT_EXCEEDED);
        verifyNoInteractions(deviceService);
    }

    @Test
    void create_should_reject_duplicate_name() {
        when(ruleDefinitionMapper.countByUserAndName(USER_ID, "高温联动", null)).thenReturn(1);

        assertCode(() -> service.create(USER_ID, propertyRequest()), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_unknown_source_type() {
        RuleRequest request = propertyRequest();
        request.setSourceType("SOMETHING");

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_unsupported_action_type() {
        RuleRequest request = propertyRequest();
        request.setActionType("DO_MAGIC");

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_ACTION_UNSUPPORTED);
    }

    @Test
    void create_should_reject_non_owned_device() {
        RuleRequest request = propertyRequest();
        request.setDeviceId(DEVICE_ID);
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(999L));

        assertCode(() -> service.create(USER_ID, request), ResultCode.DEVICE_NOT_OWNED);
    }

    @Test
    void create_should_reject_numeric_threshold_not_parseable() {
        RuleRequest request = propertyRequest();
        request.setThresholdValue("hot");

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_event_rule_with_operator() {
        RuleRequest request = eventRequest();
        request.setOperator(RuleConstants.OPERATOR_GT);

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_missing_action_config() {
        RuleRequest request = propertyRequest();
        request.setActionConfig(null);

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_forward_mqtt_wildcard_topic() {
        RuleRequest request = propertyRequest();
        request.setActionType(RuleConstants.ACTION_FORWARD_MQTT);
        request.setActionConfig(json("{\"topic\":\"factory/+\"}"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    @Test
    void create_should_reject_forward_http_relative_url() {
        RuleRequest request = propertyRequest();
        request.setActionType(RuleConstants.ACTION_FORWARD_HTTP);
        request.setActionConfig(json("{\"url\":\"/hook\"}"));

        assertCode(() -> service.create(USER_ID, request), ResultCode.RULE_INVALID);
    }

    // ---------- 创建成功 ----------

    @Test
    void create_should_save_normalized_rule_and_evict_cache() {
        RuleDefinition created = service.create(USER_ID, propertyRequest());

        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getSourceType()).isEqualTo(RuleConstants.SOURCE_PROPERTY);
        assertThat(created.getOperator()).isEqualTo(RuleConstants.OPERATOR_GT);
        assertThat(created.getThresholdValue()).isEqualTo("40");
        assertThat(created.getEventType()).isNull();
        assertThat(created.getEnabled()).isEqualTo(1);
        assertThat(created.getCooldownSeconds()).isZero();
        assertThat(created.getActionConfig()).contains("\"identifier\":\"switch\"");
        assertThat(created.getActionConfigView()).isInstanceOf(JsonNode.class);
        verify(ruleDefinitionMapper).insert(any(RuleDefinition.class));
        verify(ruleEvaluationService).evictCache(USER_ID);
    }

    @Test
    void create_event_rule_should_lowercase_event_type_and_clear_operator() {
        RuleRequest request = eventRequest();
        request.setEventType("ALERT");

        RuleDefinition created = service.create(USER_ID, request);

        assertThat(created.getSourceType()).isEqualTo(RuleConstants.SOURCE_EVENT);
        assertThat(created.getEventType()).isEqualTo("alert");
        assertThat(created.getOperator()).isNull();
        assertThat(created.getThresholdValue()).isNull();
    }

    // ---------- 更新 / 删除 / 启停 ----------

    @Test
    void update_should_reject_missing_rule() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(null);

        assertCode(() -> service.update(USER_ID, RULE_ID, propertyRequest()), ResultCode.RULE_NOT_FOUND);
    }

    @Test
    void update_should_reject_other_users_rule() {
        RuleDefinition existing = existingRule();
        existing.setUserId(999L);
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existing);

        assertCode(() -> service.update(USER_ID, RULE_ID, propertyRequest()), ResultCode.FORBIDDEN);
    }

    @Test
    void update_should_persist_and_evict_cache() {
        RuleDefinition existing = existingRule();
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existing);

        RuleRequest request = propertyRequest();
        request.setName("高温联动V2");
        service.update(USER_ID, RULE_ID, request);

        assertThat(existing.getName()).isEqualTo("高温联动V2");
        verify(ruleDefinitionMapper).updateById(existing);
        verify(ruleEvaluationService).evictCache(USER_ID);
    }

    @Test
    void delete_should_remove_and_evict_cache() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());

        service.delete(USER_ID, RULE_ID);

        verify(ruleDefinitionMapper).deleteById(RULE_ID);
        verify(ruleEvaluationService).evictCache(USER_ID);
    }

    @Test
    void setEnabled_should_flip_flag_and_evict_cache() {
        RuleDefinition existing = existingRule();
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existing);

        service.setEnabled(USER_ID, RULE_ID, false);

        assertThat(existing.getEnabled()).isZero();
        verify(ruleDefinitionMapper).updateById(existing);
        verify(ruleEvaluationService).evictCache(USER_ID);
    }

    // ---------- 试运行 ----------

    @Test
    void test_should_report_match_and_diagnosis_without_side_effects() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithSwitch());

        RuleTestResult result = service.test(USER_ID, RULE_ID, testRequest("temperature", "41"));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.isDeviceModeled()).isTrue();
        assertThat(result.isIdentifierModeled()).isTrue();
        assertThat(result.isActionExecutable()).isTrue();
        assertThat(result.getActionSummary()).contains("switch");
        verify(ruleDefinitionMapper, never()).insert(any(RuleDefinition.class));
        verify(ruleEvaluationService, never()).evictCache(any());
    }

    @Test
    void test_should_report_not_matched_when_condition_fails() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithSwitch());

        RuleTestResult result = service.test(USER_ID, RULE_ID, testRequest("temperature", "30"));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getReason()).contains("未满足");
    }

    @Test
    void test_should_flag_action_not_executable_when_target_readonly() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(USER_ID));
        when(thingModelService.getForProduct(PRODUCT_ID)).thenReturn(modelWithReadonlySwitch());

        RuleTestResult result = service.test(USER_ID, RULE_ID, testRequest("temperature", "41"));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.isActionExecutable()).isFalse();
        assertThat(result.getActionSummary()).contains("不可写");
    }

    @Test
    void test_should_reject_non_owned_device() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device(999L));

        assertCode(() -> service.test(USER_ID, RULE_ID, testRequest("temperature", "41")),
                ResultCode.DEVICE_NOT_OWNED);
    }

    @Test
    void test_should_reject_unparseable_numeric_value() {
        when(ruleDefinitionMapper.selectById(RULE_ID)).thenReturn(existingRule());

        assertCode(() -> service.test(USER_ID, RULE_ID, testRequest("temperature", "hot")),
                ResultCode.RULE_INVALID);
    }

    // ---------- 辅助 ----------

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    private RuleRequest propertyRequest() {
        RuleRequest request = new RuleRequest();
        request.setName("高温联动");
        request.setSourceType(RuleConstants.SOURCE_PROPERTY);
        request.setIdentifier("temperature");
        request.setOperator(RuleConstants.OPERATOR_GT);
        request.setThresholdValue("40");
        request.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        request.setActionConfig(json("{\"identifier\":\"switch\",\"value\":\"on\"}"));
        return request;
    }

    private RuleRequest eventRequest() {
        RuleRequest request = new RuleRequest();
        request.setName("跌倒事件联动");
        request.setSourceType(RuleConstants.SOURCE_EVENT);
        request.setIdentifier("fall");
        request.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        request.setActionConfig(json("{\"identifier\":\"switch\",\"value\":\"on\"}"));
        return request;
    }

    private RuleTestRequest testRequest(String identifier, String value) {
        RuleTestRequest request = new RuleTestRequest();
        request.setDeviceId(DEVICE_ID);
        request.setIdentifier(identifier);
        request.setValueText(value);
        return request;
    }

    private RuleDefinition existingRule() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("高温联动");
        rule.setSourceType(RuleConstants.SOURCE_PROPERTY);
        rule.setIdentifier("temperature");
        rule.setOperator(RuleConstants.OPERATOR_GT);
        rule.setThresholdValue("40");
        rule.setActionType(RuleConstants.ACTION_UPDATE_PROPERTY);
        rule.setActionConfig("{\"identifier\":\"switch\",\"value\":\"on\"}");
        rule.setCooldownSeconds(0);
        rule.setEnabled(1);
        return rule;
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceKey("dev-1");
        device.setOwnerId(ownerId);
        return device;
    }

    private ThingModelDefinition modelWithSwitch() {
        return new ThingModelDefinition(1, Map.of(
                "temperature", new ThingModelDefinition.PropertySpec(
                        "temperature", "double", null, null, false, Set.of(), null, "r"),
                "switch", new ThingModelDefinition.PropertySpec(
                        "switch", "bool", null, null, false, Set.of(), null, "rw")),
                Map.of(), Map.of());
    }

    private ThingModelDefinition modelWithReadonlySwitch() {
        return new ThingModelDefinition(1, Map.of(
                "temperature", new ThingModelDefinition.PropertySpec(
                        "temperature", "double", null, null, false, Set.of(), null, "r"),
                "switch", new ThingModelDefinition.PropertySpec(
                        "switch", "bool", null, null, false, Set.of(), null, "r")),
                Map.of(), Map.of());
    }

    private JsonNode json(String raw) {
        return new ObjectMapper().readTree(raw);
    }
}