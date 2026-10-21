package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.PointDispatchConstants;
import com.mqtt.cloud.common.constant.PointExecutorValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.response.PointDispatchResultDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceCommandRecord;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.service.DeviceCommandService;
import com.mqtt.cloud.service.DevicePointTableService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.ProductService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点表下发服务单测（T-31 P3，路线 A）。
 * <p>
 * 覆盖：载荷字段与 {@code device_point_table} 列的映射一致性、版本单调性与 Redis 降级、
 * 拒绝下发 PLATFORM 点表、拒绝空点表、协议与归属校验、下发历史复用命令通道。
 */
class PointTableDispatchServiceImplTest {

    private static final Long DEVICE_ID = 100L;
    private static final Long PRODUCT_ID = 7L;
    private static final Long OWNER_ID = 5L;

    private DeviceService deviceService;
    private ProductService productService;
    private DevicePointTableService pointTableService;
    private DeviceCommandService deviceCommandService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;
    private PointTableDispatchServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        deviceService = mock(DeviceService.class);
        productService = mock(ProductService.class);
        pointTableService = mock(DevicePointTableService.class);
        deviceCommandService = mock(DeviceCommandService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(OWNER_ID, 1));
        when(productService.requireById(PRODUCT_ID)).thenReturn(product("GATEWAY_MQTT"));
        when(pointTableService.listEnabledForCollect(DEVICE_ID, PointExecutorValue.EDGE))
                .thenReturn(List.of(edgePoint("temp", "40001"), edgePoint("humi", "40002")));
        when(pointTableService.resolveExecutor(DEVICE_ID)).thenReturn(PointExecutorValue.EDGE);
        when(valueOperations.increment(anyString())).thenReturn(11L);
        when(deviceCommandService.invoke(any())).thenReturn(command("SENT"));

        service = new PointTableDispatchServiceImpl(deviceService, productService, pointTableService,
                deviceCommandService, redisTemplate, objectMapper, meterRegistry);
    }

    // ---------- 正常下发 ----------

    @Test
    void dispatch_should_return_command_id_status_version_and_point_count() {
        PointDispatchResultDTO result = service.dispatch(OWNER_ID, DEVICE_ID);

        assertThat(result.getCommandId()).isEqualTo("cmd-1");
        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getVersion()).isEqualTo(11L);
        assertThat(result.getPointCount()).isEqualTo(2);
    }

    /**
     * 载荷字段名必须与点表列名一一对应（{@code point_key → pointKey}）。
     * <p>
     * 这条断言的价值在于：若日后有人为了「精简」把 {@code pointKey} 改成 {@code key}，
     * 测试立刻失败，而不是等到现场网关解析不出点位、采集全静默失败才发现。
     */
    @Test
    void dispatch_should_map_every_point_column_to_payload_field() throws Exception {
        service.dispatch(OWNER_ID, DEVICE_ID);

        JsonNode params = captureParams();
        assertThat(params.get("deviceId").asLong()).isEqualTo(DEVICE_ID);
        assertThat(params.get("version").asLong()).isEqualTo(11L);
        assertThat(params.get("dispatchedAt").asLong()).isPositive();

        JsonNode first = params.get("points").get(0);
        assertThat(fieldNamesOf(first)).containsExactlyInAnyOrder(
                "pointKey", "pointType", "address", "dataType", "unitId", "scale", "offset", "unit",
                "readIntervalMs");
        assertThat(first.get("pointKey").asText()).isEqualTo("temp");
        assertThat(first.get("pointType").asText()).isEqualTo("HOLDING_REGISTER");
        assertThat(first.get("address").asText()).isEqualTo("40001");
        assertThat(first.get("dataType").asText()).isEqualTo("float");
        assertThat(first.get("unitId").asInt()).isEqualTo(2);
        assertThat(first.get("scale").asText()).isEqualTo("0.1");
        assertThat(first.get("offset").asText()).isEqualTo("-5");
        assertThat(first.get("unit").asText()).isEqualTo("C");
        assertThat(first.get("readIntervalMs").asInt()).isEqualTo(30000);
    }

    /** 空的可选字段不下发（网关侧按缺省处理），而不是下发 null / 空串。 */
    @Test
    void dispatch_should_omit_absent_optional_fields() throws Exception {
        when(pointTableService.listEnabledForCollect(DEVICE_ID, PointExecutorValue.EDGE))
                .thenReturn(List.of(barePoint("tag1")));

        service.dispatch(OWNER_ID, DEVICE_ID);

        JsonNode first = captureParams().get("points").get(0);
        assertThat(first.has("scale")).isFalse();
        assertThat(first.has("offset")).isFalse();
        assertThat(first.has("unit")).isFalse();
        assertThat(first.has("unitId")).isFalse();
        assertThat(first.has("address")).isFalse();
        assertThat(first.get("pointKey").asText()).isEqualTo("tag1");
    }

    /** 十进制系数以字符串下发：DECIMAL(18,6) 转 double 会在网关侧产生精度表述歧义。 */
    @Test
    void dispatch_should_send_decimal_scale_as_plain_string() throws Exception {
        when(pointTableService.listEnabledForCollect(DEVICE_ID, PointExecutorValue.EDGE))
                .thenReturn(List.of(pointWithScale(new BigDecimal("0.000001"))));

        service.dispatch(OWNER_ID, DEVICE_ID);

        JsonNode scale = captureParams().get("points").get(0).get("scale");
        assertThat(scale.isTextual()).isTrue();
        assertThat(scale.asText()).isEqualTo("0.000001");
    }

    /** 下发必须走保留服务 + 异步 + POINT_TABLE 来源，历史里才能与普通服务调用区分。 */
    @Test
    void dispatch_should_invoke_command_channel_as_reserved_async_service() {
        service.dispatch(OWNER_ID, DEVICE_ID);

        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(deviceCommandService).invoke(captor.capture());
        DeviceCommandService.CommandInvoke command = captor.getValue();
        assertThat(command.commandType()).isEqualTo(DeviceCommandService.TYPE_SERVICE);
        assertThat(command.identifier()).isEqualTo(DeviceCommandService.SERVICE_POINT_TABLE_DISPATCH);
        assertThat(command.callType()).isEqualTo(DeviceCommandService.CALL_TYPE_ASYNC);
        assertThat(command.source()).isEqualTo(DeviceCommandService.SOURCE_POINT_TABLE);
        assertThat(command.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(command.productId()).isEqualTo(PRODUCT_ID);
    }

    // ---------- 版本 ----------

    @Test
    void dispatch_should_use_redis_key_scoped_by_device() {
        service.dispatch(OWNER_ID, DEVICE_ID);

        verify(valueOperations).increment(PointDispatchConstants.VERSION_KEY_PREFIX + DEVICE_ID);
    }

    @Test
    void dispatch_should_take_incremented_version_from_redis() {
        when(valueOperations.increment(anyString())).thenReturn(1L, 2L, 3L);

        assertThat(service.dispatch(OWNER_ID, DEVICE_ID).getVersion()).isEqualTo(1L);
        assertThat(service.dispatch(OWNER_ID, DEVICE_ID).getVersion()).isEqualTo(2L);
        assertThat(service.dispatch(OWNER_ID, DEVICE_ID).getVersion()).isEqualTo(3L);
    }

    /** Redis 异常不应让运维改不了配置，退化为进程内自增。 */
    @Test
    void dispatch_should_fall_back_when_redis_unavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        PointDispatchResultDTO first = service.dispatch(OWNER_ID, DEVICE_ID);
        PointDispatchResultDTO second = service.dispatch(OWNER_ID, DEVICE_ID);

        assertThat(first.getVersion()).isPositive();
        assertThat(second.getVersion()).isGreaterThan(first.getVersion());
    }

    /**
     * 退化版本号必须远大于 Redis 正常值区间。
     * <p>
     * 若退化号从 1 开始，Redis 恢复后正常的自增号（可能才 20）会被网关判定为「旧版本」丢弃，
     * 表现为「配置改了但一直不生效」。用 1e12 基数把两套号段彻底隔开。
     */
    @Test
    void fallback_version_should_not_collide_with_redis_range() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        long fallback = service.dispatch(OWNER_ID, DEVICE_ID).getVersion();

        assertThat(fallback).isGreaterThan(1_000_000_000_000L);
    }

    // ---------- 拒绝 ----------

    /** PLATFORM 点表由平台自采，下发无意义 —— 必须明确拒绝而不是静默成功。 */
    @Test
    void dispatch_should_reject_when_executor_is_platform() {
        when(pointTableService.resolveExecutor(DEVICE_ID)).thenReturn(PointExecutorValue.PLATFORM);

        assertCode(ResultCode.POINT_DISPATCH_NOT_EDGE);
        verify(deviceCommandService, never()).invoke(any());
    }

    /** 空点表在边缘侧语义是「清空所有点位」，误发破坏性远大于不下发。 */
    @Test
    void dispatch_should_reject_when_no_enabled_points() {
        when(pointTableService.listEnabledForCollect(DEVICE_ID, PointExecutorValue.EDGE)).thenReturn(List.of());

        assertCode(ResultCode.POINT_DISPATCH_NO_POINTS);
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void dispatch_should_reject_when_device_not_found() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(null);

        assertCode(ResultCode.DEVICE_NOT_FOUND);
    }

    /** 越权访问按「不存在」处理，不泄漏设备是否存在。 */
    @Test
    void dispatch_should_reject_when_not_owner() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(OWNER_ID + 1, 1));

        assertCode(ResultCode.DEVICE_NOT_FOUND);
        verify(deviceCommandService, never()).invoke(any());
    }

    @Test
    void dispatch_should_reject_when_device_disabled() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(OWNER_ID, 0));

        assertCode(ResultCode.POINT_DISPATCH_DEVICE_INVALID);
    }

    @Test
    void dispatch_should_reject_when_protocol_is_not_gateway() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product("MODBUS"));

        assertCode(ResultCode.POINT_DISPATCH_DEVICE_INVALID);
        verify(deviceCommandService, never()).invoke(any());
    }

    /**
     * 存量网关设备的 {@code protocol_type} 为 {@code NULL}，必须放行。
     * <p>
     * 若严格要求等于 {@code GATEWAY_MQTT}，上线这版代码后所有 T-30 之前建的网关会立刻无法下发。
     */
    @Test
    void dispatch_should_allow_when_protocol_is_null_for_legacy_gateway() {
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(null));

        assertThat(service.dispatch(OWNER_ID, DEVICE_ID).getStatus()).isEqualTo("SENT");
    }

    /** 平台侧任务传 ownerId=null 时跳过归属校验（即便设备属主是他人）。 */
    @Test
    void dispatch_should_skip_ownership_check_for_platform_task() {
        assertThat(service.dispatch(null, DEVICE_ID).getStatus()).isEqualTo("SENT");
    }

    // ---------- 辅助 ----------

    private void assertCode(ResultCode expected) {
        assertThatThrownBy(() -> service.dispatch(OWNER_ID, DEVICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }

    /** Jackson 3 的 {@code JsonNode} 未暴露 {@code fieldNames()}，按属性名枚举。 */
    private List<String> fieldNamesOf(JsonNode node) {
        return node.propertyNames().stream().map(String::valueOf).toList();
    }

    private JsonNode captureParams() throws Exception {        ArgumentCaptor<DeviceCommandService.CommandInvoke> captor =
                ArgumentCaptor.forClass(DeviceCommandService.CommandInvoke.class);
        verify(deviceCommandService).invoke(captor.capture());
        return objectMapper.readTree(captor.getValue().paramsJson());
    }

    private DeviceCommandRecord command(String status) {
        DeviceCommandRecord record = new DeviceCommandRecord();
        record.setCommandId("cmd-1");
        record.setStatus(status);
        return record;
    }

    private Device device(Long ownerId, int enabled) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setOwnerId(ownerId);
        device.setEnabled(enabled);
        device.setStatus("ONLINE");
        return device;
    }

    private Product product(String protocolType) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProtocolType(protocolType);
        product.setStatus("ENABLED");
        return product;
    }

    private DevicePointTable edgePoint(String pointKey, String address) {
        DevicePointTable point = new DevicePointTable();
        point.setPointKey(pointKey);
        point.setPointType("HOLDING_REGISTER");
        point.setAddress(address);
        point.setUnitId(2);
        point.setDataType("float");
        point.setScale(new BigDecimal("0.1"));
        point.setOffset(new BigDecimal("-5"));
        point.setUnit("C");
        point.setReadIntervalMs(30000);
        point.setExecutor(PointExecutorValue.EDGE);
        return point;
    }

    private DevicePointTable pointWithScale(BigDecimal scale) {
        DevicePointTable point = edgePoint("p", "1");
        point.setScale(scale);
        return point;
    }

    /** 只填必填字段的可选字段全空点位。 */
    private DevicePointTable barePoint(String pointKey) {
        DevicePointTable point = new DevicePointTable();
        point.setPointKey(pointKey);
        point.setPointType("TAG");
        point.setExecutor(PointExecutorValue.EDGE);
        return point;
    }
}
