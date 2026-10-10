package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.PointExecutorValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.PointTableUpsertRequest;
import com.mqtt.cloud.dto.response.PointTableDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePointRun;
import com.mqtt.cloud.entity.DevicePointTable;
import com.mqtt.cloud.mapper.DevicePointRunMapper;
import com.mqtt.cloud.mapper.DevicePointTableMapper;
import com.mqtt.cloud.service.DeviceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点位表服务用例（T-31 P1）。
 * <p>
 * 重点覆盖两条不可回退的约束：
 * <ol>
 *   <li><b>执行方一致性</b> —— 同设备混用 EDGE / PLATFORM 必须整批拒绝。这是防止同一点位
 *       被两条路线双写的唯一屏障，一旦退化为「静默改写」，问题会在下游数据里以极难定位的
 *       形式暴露出来；</li>
 *   <li><b>批内标识去重</b> —— 同批出现重复 pointKey 时若走 upsert 语义，后者会静默覆盖前者，
 *       而调用方看到的是「全部成功」。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DevicePointTableServiceImplTest {

    private static final Long OWNER_ID = 7L;
    private static final Long DEVICE_ID = 42L;

    @Mock
    private DevicePointTableMapper pointTableMapper;
    @Mock
    private DevicePointRunMapper pointRunMapper;
    @Mock
    private DeviceService deviceService;

    private DevicePointTableServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DevicePointTable.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DevicePointRun.class);
        service = new DevicePointTableServiceImpl(pointTableMapper, pointRunMapper, deviceService);
        ReflectionTestUtils.setField(service, "baseMapper", pointTableMapper);
    }

    // ---------- 执行方隔离（核心约束）----------

    @Test
    void savePoints_should_reject_mixing_platform_into_existing_edge_device() {
        givenOwnedDevice();
        // 该设备已有 EDGE 点位（网关采集）
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID))
                .thenReturn(List.of(PointExecutorValue.EDGE));

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID,
                        List.of(point("t1", PointExecutorValue.PLATFORM))),
                ResultCode.POINT_EXECUTOR_MIXED);

        // 关键：拒绝发生在落库之前，不得产生任何写入
        verify(pointTableMapper, never()).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_reject_mixing_edge_into_existing_platform_device() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID))
                .thenReturn(List.of(PointExecutorValue.PLATFORM));

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID,
                        List.of(point("t1", PointExecutorValue.EDGE))),
                ResultCode.POINT_EXECUTOR_MIXED);

        verify(pointTableMapper, never()).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_reject_when_batch_itself_mixes_executors() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());

        // 同一批内既有 EDGE 又有 PLATFORM：即使设备原本无点位，也必须拒绝
        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID, List.of(
                        point("t1", PointExecutorValue.EDGE),
                        point("t2", PointExecutorValue.PLATFORM))),
                ResultCode.POINT_EXECUTOR_MIXED);

        verify(pointTableMapper, never()).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_accept_same_executor_batch() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        int saved = service.savePoints(OWNER_ID, DEVICE_ID, List.of(
                point("t1", PointExecutorValue.PLATFORM),
                point("t2", PointExecutorValue.PLATFORM)));

        assertThat(saved).isEqualTo(2);
        verify(pointTableMapper, times(2)).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_normalize_missing_executor_to_edge() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        PointTableUpsertRequest req = point("t1", null);
        service.savePoints(OWNER_ID, DEVICE_ID, List.of(req));

        ArgumentCaptor<DevicePointTable> captor = ArgumentCaptor.forClass(DevicePointTable.class);
        verify(pointTableMapper).insertOrUpdate(captor.capture());
        assertThat(captor.getValue().getExecutor()).isEqualTo(PointExecutorValue.EDGE);
    }

    @Test
    void resolveExecutor_should_return_null_when_data_already_mixed() {
        // 脏数据（绕过服务层写入）：已存在两种执行方时不得任选其一扩散，
        // 返回 null 让写入侧按「无既有约束」处理并由本批纠正
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID))
                .thenReturn(List.of(PointExecutorValue.EDGE, PointExecutorValue.PLATFORM));

        assertThat(service.resolveExecutor(DEVICE_ID)).isNull();
    }

    @Test
    void resolveExecutor_should_return_null_when_no_points() {
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        assertThat(service.resolveExecutor(DEVICE_ID)).isNull();
    }

    @Test
    void resolveExecutor_should_normalize_existing_executor() {
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID))
                .thenReturn(List.of("platform"));
        assertThat(service.resolveExecutor(DEVICE_ID)).isEqualTo(PointExecutorValue.PLATFORM);
    }

    // ---------- 批内去重与非法值 ----------

    @Test
    void savePoints_should_reject_duplicated_point_key_in_same_batch() {
        givenOwnedDevice();
        // 不 stub findExecutorsByDevice：批内去重先于执行方一致性判定，
        // 该方法在这条路径上不会被调用（stub 了反而触发 UnnecessaryStubbing）

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID,
                        List.of(point("dup", PointExecutorValue.EDGE),
                                point("dup", PointExecutorValue.EDGE))),
                ResultCode.POINT_KEY_DUPLICATED);

        verify(pointTableMapper, never()).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_reject_empty_batch() {
        givenOwnedDevice();
        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID, List.of()),
                ResultCode.POINT_BATCH_EMPTY);
    }

    @Test
    void savePoints_should_reject_register_point_without_address() {
        givenOwnedDevice();

        PointTableUpsertRequest req = point("t1", PointExecutorValue.PLATFORM);
        req.setPointType("HOLDING_REGISTER");
        req.setAddress(null);

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID, List.of(req)),
                ResultCode.POINT_ADDRESS_REQUIRED);
    }

    @Test
    void savePoints_should_reject_non_numeric_register_address() {
        givenOwnedDevice();

        PointTableUpsertRequest req = point("t1", PointExecutorValue.PLATFORM);
        req.setPointType("HOLDING_REGISTER");
        req.setAddress("0x10A"); // 十六进制写法在现场很常见，但寄存器号按十进制解析

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID, List.of(req)),
                ResultCode.POINT_ADDRESS_INVALID);
    }

    @Test
    void savePoints_should_allow_zero_register_address() {
        // 寄存器 0 是合法地址，地址校验不得误判（现场点表从 0 起始很常见）
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        PointTableUpsertRequest req = point("t1", PointExecutorValue.PLATFORM);
        req.setPointType("HOLDING_REGISTER");
        req.setAddress("0");

        assertThat(service.savePoints(OWNER_ID, DEVICE_ID, List.of(req))).isEqualTo(1);
    }

    @Test
    void savePoints_should_allow_free_form_address_for_tag_point() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        PointTableUpsertRequest req = point("t1", PointExecutorValue.EDGE);
        req.setPointType("TAG");
        req.setAddress("Line1.Temp_PV");

        assertThat(service.savePoints(OWNER_ID, DEVICE_ID, List.of(req))).isEqualTo(1);
    }

    @Test
    void savePoints_should_reject_zero_scale() {
        // scale=0 会把所有工程值压成 0，属配置错误，显式拒绝而非静默生效
        givenOwnedDevice();

        PointTableUpsertRequest req = point("t1", PointExecutorValue.PLATFORM);
        req.setScale(BigDecimal.ZERO);

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID, List.of(req)),
                ResultCode.POINT_SCALE_INVALID);
    }

    @Test
    void savePoints_should_apply_defaults() {
        givenOwnedDevice();
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        PointTableUpsertRequest req = point("t1", PointExecutorValue.PLATFORM);
        service.savePoints(OWNER_ID, DEVICE_ID, List.of(req));

        ArgumentCaptor<DevicePointTable> captor = ArgumentCaptor.forClass(DevicePointTable.class);
        verify(pointTableMapper).insertOrUpdate(captor.capture());
        DevicePointTable saved = captor.getValue();
        assertThat(saved.getReadIntervalMs()).isEqualTo(60_000);
        assertThat(saved.getEnabled()).isEqualTo(1);
        assertThat(saved.getDataType()).isEqualTo("float");
        assertThat(saved.getPointType()).isEqualTo("HOLDING_REGISTER");
        assertThat(saved.getDeviceId()).isEqualTo(DEVICE_ID);
    }

    // ---------- 归属与删除 ----------

    @Test
    void savePoints_should_reject_device_owned_by_other_user() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(999L));

        assertCode(() -> service.savePoints(OWNER_ID, DEVICE_ID,
                        List.of(point("t1", PointExecutorValue.EDGE))),
                ResultCode.DEVICE_NOT_FOUND);

        verify(pointTableMapper, never()).insertOrUpdate(any(DevicePointTable.class));
    }

    @Test
    void savePoints_should_skip_ownership_check_for_platform_task() {
        // ownerId 为 null 表示平台侧内部任务（如从点表模板初始化），不做归属校验
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(999L));
        when(pointTableMapper.findExecutorsByDevice(DEVICE_ID)).thenReturn(List.of());
        when(pointTableMapper.insertOrUpdate(any(DevicePointTable.class))).thenReturn(true);

        assertThat(service.savePoints(null, DEVICE_ID,
                List.of(point("t1", PointExecutorValue.PLATFORM)))).isEqualTo(1);
    }

    @Test
    void deletePoint_should_remove_point_and_its_run_logs() {
        givenOwnedDevice();
        DevicePointTable existing = new DevicePointTable();
        existing.setId(9L);
        existing.setDeviceId(DEVICE_ID);
        when(pointTableMapper.selectById(9L)).thenReturn(existing);

        service.deletePoint(OWNER_ID, DEVICE_ID, 9L);

        verify(pointTableMapper).deleteById(9L);
        // 采集日志按点位每周期一行，点位删除后即为无主数据，必须连带清理
        verify(pointRunMapper).deleteByPointId(9L);
    }

    @Test
    void deletePoint_should_not_touch_points_of_other_device() {
        givenOwnedDevice();
        DevicePointTable existing = new DevicePointTable();
        existing.setId(9L);
        existing.setDeviceId(OTHER_DEVICE_ID);
        when(pointTableMapper.selectById(9L)).thenReturn(existing);

        // 跨设备删除按「不存在」处理：不泄漏该点位是否存在于另一台设备
        assertCode(() -> service.deletePoint(OWNER_ID, DEVICE_ID, 9L),
                ResultCode.POINT_KEY_DUPLICATED);

        verify(pointTableMapper, never()).deleteById(anyLong());
        verify(pointRunMapper, never()).deleteByPointId(anyLong());
    }

    // ---------- 查询 ----------

    @Test
    void listPoints_should_filter_by_executor() {
        givenOwnedDevice();
        when(pointTableMapper.findEnabledByDeviceAndExecutor(DEVICE_ID, PointExecutorValue.PLATFORM))
                .thenReturn(List.of());

        service.listPoints(OWNER_ID, DEVICE_ID, "platform");

        // 执行方归一化后传入：小写入参必须等价于 PLATFORM
        verify(pointTableMapper).findEnabledByDeviceAndExecutor(DEVICE_ID, PointExecutorValue.PLATFORM);
    }

    @Test
    void listPoints_should_expose_last_run_status_for_troubleshooting() {
        givenOwnedDevice();
        DevicePointTable point = new DevicePointTable();
        point.setId(1L);
        point.setDeviceId(DEVICE_ID);
        point.setPointKey("t1");
        point.setExecutor(PointExecutorValue.PLATFORM);
        // executor 传 null 表示不区分执行方，走 findEnabledByDevice 这一路
        when(pointTableMapper.findEnabledByDevice(DEVICE_ID)).thenReturn(List.of(point));

        DevicePointRun lastRun = new DevicePointRun();
        lastRun.setStatus(DevicePointRun.STATUS_FAILED);
        lastRun.setErrorMessage("寄存器地址越界");
        when(pointRunMapper.findLatestByPointId(1L)).thenReturn(lastRun);

        List<PointTableDTO> points = service.listPoints(OWNER_ID, DEVICE_ID, null);

        assertThat(points).hasSize(1);
        // 排障需要能区分「没配点」与「配了但采不到」，故最近状态必须透出
        assertThat(points.get(0).getLastRunStatus()).isEqualTo(DevicePointRun.STATUS_FAILED);
        assertThat(points.get(0).getLastErrorMessage()).isEqualTo("寄存器地址越界");
    }

    @Test
    void listEnabledForCollect_should_return_empty_for_null_device() {
        assertThat(service.listEnabledForCollect(null, PointExecutorValue.PLATFORM)).isEmpty();
        verify(pointTableMapper, never()).findEnabledByDeviceAndExecutor(anyLong(), any());
    }

    @Test
    void listEnabledForCollect_should_normalize_executor() {
        when(pointTableMapper.findEnabledByDeviceAndExecutor(DEVICE_ID, PointExecutorValue.PLATFORM))
                .thenReturn(List.of());

        service.listEnabledForCollect(DEVICE_ID, "PLATFORM");

        verify(pointTableMapper).findEnabledByDeviceAndExecutor(DEVICE_ID, PointExecutorValue.PLATFORM);
    }

    // ---------- 夹具 ----------

    private static final Long OTHER_DEVICE_ID = 43L;

    private void givenOwnedDevice() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device(OWNER_ID));
    }

    private Device device(Long ownerId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setOwnerId(ownerId);
        return device;
    }

    private PointTableUpsertRequest point(String key, String executor) {
        PointTableUpsertRequest req = new PointTableUpsertRequest();
        req.setPointKey(key);
        req.setExecutor(executor);
        req.setPointType("HOLDING_REGISTER");
        req.setAddress("40001");
        return req;
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}
