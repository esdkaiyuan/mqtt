package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceNodeType;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DeviceTopologyProperties;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.ProductService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备拓扑服务单测（T-26 实施计划 P2 步骤 ③）。
 * <p>
 * 覆盖设计 §7 六条挂载校验（父不存在 {@code 6255} / 父非网关 {@code 6256} / 跨产品 {@code 6258} /
 * 深度超限 {@code 6257} / 数量超限 {@code 6260} / 自环 {@code 6254}）与 §8.2 角色收敛两条路径
 * （首次挂载把 {@code DIRECT} 网关提升为 {@code GATEWAY}、解绑后网关角色保持 {@code GATEWAY}），
 * 另覆盖角色变更的幂等、降级拒绝与查父、列子设备的分页透传。
 */
@ExtendWith(MockitoExtension.class)
class DeviceTopologyServiceImplTest {

    private static final String PRODUCT_KEY = "PK-100";
    private static final Long PRODUCT_ID = 100L;
    private static final Long OTHER_PRODUCT_ID = 200L;
    private static final Long GATEWAY_ID = 1L;
    private static final Long CHILD_ID = 2L;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private ProductService productService;

    @Mock
    private DeviceAuthCacheService authCacheService;

    /** 用真实实例做间谍：构造注入拿到的是带默认值（maxDepth=5 / 上限 200）的属性对象，无需逐例打桩。 */
    @Spy
    private DeviceTopologyProperties topologyProperties = new DeviceTopologyProperties();

    @InjectMocks
    private DeviceTopologyServiceImpl service;

    @BeforeEach
    void setUp() {
        // 实体 TableInfo 需初始化；纯单测无 MyBatis 上下文
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Device.class);
        // 显式构造器只注入三个依赖，baseMapper 由 MyBatis-Plus 装配，单测需手动补齐
        ReflectionTestUtils.setField(service, "baseMapper", deviceMapper);
    }

    // ---------- 挂载与解绑的角色收敛 ----------

    @Test
    void attach_should_link_child_and_promote_direct_gateway() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        Device result = service.attach(GATEWAY_ID, CHILD_ID);

        assertThat(result).isSameAs(child);
        assertThat(child.getNodeType()).isEqualTo(DeviceNodeType.SUB);
        assertThat(child.getParentId()).isEqualTo(GATEWAY_ID);
        // 首次挂载自动收敛：DIRECT 网关提升为 GATEWAY
        assertThat(gateway.getNodeType()).isEqualTo(DeviceNodeType.GATEWAY);
        verify(deviceMapper, times(2)).updateById(any(Device.class));
        // 两侧缓存都要失效：子设备角色变了，网关角色也变了
        verify(authCacheService, times(2)).evict(eq(PRODUCT_KEY), anyString());
    }

    @Test
    void attach_should_keep_gateway_role_when_already_gateway() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        service.attach(GATEWAY_ID, CHILD_ID);

        assertThat(gateway.getNodeType()).isEqualTo(DeviceNodeType.GATEWAY);
        // 网关角色未变，只更新子设备一行
        verify(deviceMapper, times(1)).updateById(any(Device.class));
    }

    @Test
    void detach_should_release_child_and_keep_gateway_role() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB);
        child.setParentId(GATEWAY_ID);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(0L);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        service.detach(GATEWAY_ID, CHILD_ID);

        assertThat(child.getParentId()).isNull();
        assertThat(child.getNodeType()).isEqualTo(DeviceNodeType.DIRECT);
        // 失去最后一个子设备后角色不回落：角色代表「具备网关能力」
        assertThat(gateway.getNodeType()).isEqualTo(DeviceNodeType.GATEWAY);
        verify(deviceMapper, times(1)).updateById(child);
        verify(authCacheService, times(2)).evict(eq(PRODUCT_KEY), anyString());
    }

    @Test
    void detach_should_promote_sub_gateway_with_children() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB);
        child.setParentId(GATEWAY_ID);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(1L);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        service.detach(GATEWAY_ID, CHILD_ID);

        assertThat(child.getParentId()).isNull();
        // 带下级的子网关解绑后成为根网关，保住子树
        assertThat(child.getNodeType()).isEqualTo(DeviceNodeType.GATEWAY);
        verify(authCacheService, times(2)).evict(eq(PRODUCT_KEY), anyString());
    }

    @Test
    void detach_should_reject_when_child_not_bound_to_gateway() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB);
        child.setParentId(999L);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);

        assertCode(() -> service.detach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_TOPOLOGY_INVALID);

        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    // ---------- §7 校验矩阵 ----------

    @Test
    void attach_should_reject_self_reference() {
        assertCode(() -> service.attach(GATEWAY_ID, GATEWAY_ID), ResultCode.DEVICE_TOPOLOGY_INVALID);

        verify(deviceMapper, never()).findActiveById(any());
    }

    @Test
    void attach_should_reject_when_parent_missing() {
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(null);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_PARENT_NOT_FOUND);
    }

    @Test
    void attach_should_allow_sub_gateway_as_parent() {
        Device parent = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.SUB);
        parent.setParentId(99L);
        Device ancestor = device(99L, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(parent);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.findActiveById(99L)).thenReturn(ancestor);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(0L);
        when(deviceMapper.countByParentId(GATEWAY_ID)).thenReturn(0L);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        Device result = service.attach(GATEWAY_ID, CHILD_ID);

        assertThat(result).isSameAs(child);
        assertThat(child.getNodeType()).isEqualTo(DeviceNodeType.SUB);
        assertThat(child.getParentId()).isEqualTo(GATEWAY_ID);
        // 子网关保持 SUB，不会被降级或提升
        assertThat(parent.getNodeType()).isEqualTo(DeviceNodeType.SUB);
        verify(deviceMapper, times(1)).updateById(any(Device.class));
        verify(authCacheService, times(2)).evict(eq(PRODUCT_KEY), anyString());
    }

    @Test
    void attach_should_reject_when_depth_exceeded() {
        topologyProperties.setMaxDepth(2);
        Device parent = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.SUB);
        parent.setParentId(99L);
        Device ancestor = device(99L, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(parent);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.findActiveById(99L)).thenReturn(ancestor);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(0L);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED);

        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    @Test
    void attach_should_evict_previous_parent_when_reparented() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device previous = device(77L, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB);
        child.setParentId(77L);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.findActiveById(77L)).thenReturn(previous);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(0L);
        when(deviceMapper.countByParentId(GATEWAY_ID)).thenReturn(0L);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        service.attach(GATEWAY_ID, CHILD_ID);

        assertThat(child.getParentId()).isEqualTo(GATEWAY_ID);
        verify(authCacheService, times(3)).evict(eq(PRODUCT_KEY), anyString());
    }

    @Test
    void attach_should_reject_product_mismatch() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, OTHER_PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_PRODUCT_MISMATCH);

        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    @Test
    void attach_should_reject_when_child_already_has_sub_devices() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(1L);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED);
    }

    @Test
    void attach_should_reject_when_depth_disabled() {
        topologyProperties.setMaxDepth(1);
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED);
    }

    @Test
    void attach_should_reject_when_sub_device_limit_reached() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.countByParentId(CHILD_ID)).thenReturn(0L);
        when(deviceMapper.countByParentId(GATEWAY_ID)).thenReturn(200L);

        assertCode(() -> service.attach(GATEWAY_ID, CHILD_ID), ResultCode.DEVICE_SUBDEVICE_LIMIT_EXCEEDED);
    }

    // ---------- 角色变更 ----------

    @Test
    void changeNodeType_should_reject_unsupported_value() {
        assertCode(() -> service.changeNodeType(GATEWAY_ID, DeviceNodeType.SUB), ResultCode.DEVICE_TOPOLOGY_INVALID);
        assertCode(() -> service.changeNodeType(GATEWAY_ID, "GATE"), ResultCode.DEVICE_TOPOLOGY_INVALID);

        verify(deviceMapper, never()).findActiveById(any());
    }

    @Test
    void changeNodeType_should_reject_sub_device() {
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB));

        assertCode(() -> service.changeNodeType(CHILD_ID, DeviceNodeType.GATEWAY),
                ResultCode.DEVICE_TOPOLOGY_INVALID);
    }

    @Test
    void changeNodeType_should_reject_downgrade_with_sub_devices() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);
        when(deviceMapper.countByParentId(GATEWAY_ID)).thenReturn(1L);

        assertCode(() -> service.changeNodeType(GATEWAY_ID, DeviceNodeType.DIRECT),
                ResultCode.DEVICE_HAS_SUBDEVICES);

        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    @Test
    void changeNodeType_should_be_idempotent_when_role_unchanged() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);

        Device result = service.changeNodeType(GATEWAY_ID, DeviceNodeType.GATEWAY);

        assertThat(result).isSameAs(gateway);
        verify(deviceMapper, never()).updateById(any(Device.class));
        verify(authCacheService, never()).evict(anyString(), anyString());
    }

    @Test
    void changeNodeType_should_promote_direct_to_gateway() {
        Device device = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.DIRECT);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(device);
        when(productService.requireById(PRODUCT_ID)).thenReturn(product(PRODUCT_KEY));

        Device result = service.changeNodeType(GATEWAY_ID, DeviceNodeType.GATEWAY);

        assertThat(result.getNodeType()).isEqualTo(DeviceNodeType.GATEWAY);
        verify(deviceMapper, times(1)).updateById(device);
        verify(authCacheService, times(1)).evict(eq(PRODUCT_KEY), eq(device.getDeviceKey()));
    }

    // ---------- 查询 ----------

    @Test
    void findParent_should_return_gateway() {
        Device gateway = device(GATEWAY_ID, PRODUCT_ID, DeviceNodeType.GATEWAY);
        Device child = device(CHILD_ID, PRODUCT_ID, DeviceNodeType.SUB);
        child.setParentId(GATEWAY_ID);
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(child);
        when(deviceMapper.findActiveById(GATEWAY_ID)).thenReturn(gateway);

        assertThat(service.findParent(CHILD_ID)).isSameAs(gateway);
    }

    @Test
    void findParent_should_return_null_when_child_has_no_parent() {
        when(deviceMapper.findActiveById(CHILD_ID)).thenReturn(device(CHILD_ID, PRODUCT_ID, DeviceNodeType.DIRECT));

        assertThat(service.findParent(CHILD_ID)).isNull();
        verify(deviceMapper, times(1)).findActiveById(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSubDevices_should_page_by_parent() {
        Page<Device> page = new Page<>(2, 5);
        when(deviceMapper.findByParentId(any(), eq(GATEWAY_ID), any())).thenReturn(page);
        DeviceQueryDTO query = new DeviceQueryDTO();
        query.setPageNum(2);
        query.setPageSize(5);

        IPage<Device> result = service.listSubDevices(GATEWAY_ID, query);

        assertThat(result).isSameAs(page);
        ArgumentCaptor<Page<Device>> captor = ArgumentCaptor.forClass(Page.class);
        verify(deviceMapper).findByParentId(captor.capture(), eq(GATEWAY_ID), eq(query));
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(5);
    }

    // ---------- 辅助 ----------

    private Device device(Long id, Long productId, String nodeType) {
        Device device = new Device();
        device.setId(id);
        device.setProductId(productId);
        device.setDeviceKey("key-" + id);
        device.setDeviceType("sensor");
        device.setNodeType(nodeType);
        device.setDeleted(0);
        return device;
    }

    private Product product(String productKey) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductKey(productKey);
        return product;
    }

    private void assertCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(expected.getCode());
    }
}
