package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceNodeType;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.config.DeviceTopologyProperties;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceTopologyService;
import com.mqtt.cloud.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 设备拓扑服务实现（T-26 设计文档 §7 / §8.2）。
 * <p>
 * 校验矩阵集中在 {@link #attach(Long, Long)}：自环 {@code 6254}、父不存在 {@code 6255}、
 * 父非网关 {@code 6256}、跨产品 {@code 6258}、深度超限 {@code 6257}、数量超限 {@code 6260}。
 * 挂载成功后子设备角色置 {@code SUB} 并指向网关；网关当前为 {@code DIRECT} 时一并提升为
 * {@code GATEWAY}。解绑不回落网关角色 —— 角色代表「具备网关能力」。
 * <p>
 * 拓扑变更（挂载 / 解绑 / 角色变更）失效两侧认证缓存，使父网关键的授权委托结果立即生效。
 */
@Slf4j
@Service
public class DeviceTopologyServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceTopologyService {

    /** 允许子设备挂载的最小拓扑深度：低于此值表示仅允许 {@code DIRECT} 设备。 */
    private static final int MIN_SUPPORTED_DEPTH = 2;

    private final ProductService productService;
    private final DeviceAuthCacheService authCacheService;
    private final DeviceTopologyProperties topologyProperties;

    public DeviceTopologyServiceImpl(ProductService productService,
                                     DeviceAuthCacheService authCacheService,
                                     DeviceTopologyProperties topologyProperties) {
        this.productService = productService;
        this.authCacheService = authCacheService;
        this.topologyProperties = topologyProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device attach(Long gatewayId, Long subDeviceId) {
        // 自环检查前置于查库：即便父设备不存在，自环本身也属非法配置
        if (gatewayId == null || subDeviceId == null || gatewayId.equals(subDeviceId)) {
            throw invalid("子设备不能挂载到自身");
        }

        Device gateway = baseMapper.findActiveById(gatewayId);
        if (gateway == null) {
            throw new BusinessException(ResultCode.DEVICE_PARENT_NOT_FOUND);
        }
        // 父不得为子设备（本期两层拓扑）；DIRECT 允许，首次挂载时自动提升为 GATEWAY（§8.2）
        if (DeviceNodeType.SUB.equals(gateway.getNodeType())) {
            throw new BusinessException(ResultCode.DEVICE_PARENT_KIND_INVALID);
        }

        Device child = baseMapper.findActiveById(subDeviceId);
        if (child == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (!Objects.equals(gateway.getProductId(), child.getProductId())) {
            throw new BusinessException(ResultCode.DEVICE_PRODUCT_MISMATCH);
        }

        // 本期两层拓扑：子设备不得已有下级
        if (topologyProperties.getMaxDepth() < MIN_SUPPORTED_DEPTH
                || baseMapper.countByParentId(subDeviceId) > 0L) {
            throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED);
        }
        if (baseMapper.countByParentId(gatewayId) >= topologyProperties.getMaxSubDevicesPerGateway()) {
            throw new BusinessException(ResultCode.DEVICE_SUBDEVICE_LIMIT_EXCEEDED);
        }

        child.setNodeType(DeviceNodeType.SUB);
        child.setParentId(gatewayId);
        baseMapper.updateById(child);

        // 首次挂载自动收敛角色：DIRECT 网关提升为 GATEWAY；已是网关则不动
        if (DeviceNodeType.DIRECT.equals(gateway.getNodeType())) {
            gateway.setNodeType(DeviceNodeType.GATEWAY);
            baseMapper.updateById(gateway);
        }

        evictAuthCache(child);
        evictAuthCache(gateway);
        return child;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void detach(Long gatewayId, Long subDeviceId) {
        if (gatewayId == null || subDeviceId == null) {
            throw invalid("网关与子设备标识不能为空");
        }

        Device gateway = baseMapper.findActiveById(gatewayId);
        if (gateway == null) {
            throw new BusinessException(ResultCode.DEVICE_PARENT_NOT_FOUND);
        }
        Device child = baseMapper.findActiveById(subDeviceId);
        if (child == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (!gatewayId.equals(child.getParentId())) {
            throw invalid("该设备未挂载到指定网关");
        }

        // parent_id 的 updateStrategy=ALWAYS，置空可真正写入 NULL
        child.setParentId(null);
        child.setNodeType(DeviceNodeType.DIRECT);
        baseMapper.updateById(child);

        // 网关角色不回落：失去最后一个子设备后仍是 GATEWAY
        evictAuthCache(child);
        evictAuthCache(gateway);
    }

    @Override
    public IPage<Device> listSubDevices(Long gatewayId, DeviceQueryDTO query) {
        DeviceQueryDTO condition = query == null ? new DeviceQueryDTO() : query;
        int pageNum = condition.getPageNum() == null ? 1 : condition.getPageNum();
        int pageSize = condition.getPageSize() == null ? 10 : condition.getPageSize();
        Page<Device> page = new Page<>(pageNum, pageSize);
        return baseMapper.findByParentId(page, gatewayId, condition);
    }

    @Override
    public Device findParent(Long subDeviceId) {
        if (subDeviceId == null) {
            return null;
        }
        Device child = baseMapper.findActiveById(subDeviceId);
        if (child == null || child.getParentId() == null) {
            return null;
        }
        return baseMapper.findActiveById(child.getParentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device changeNodeType(Long deviceId, String nodeType) {
        if (!DeviceNodeType.DIRECT.equals(nodeType) && !DeviceNodeType.GATEWAY.equals(nodeType)) {
            throw invalid("拓扑角色仅支持 DIRECT 与 GATEWAY");
        }

        Device device = baseMapper.findActiveById(deviceId);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (DeviceNodeType.SUB.equals(device.getNodeType())) {
            throw invalid("子设备角色由挂载关系决定，不能直接变更");
        }
        if (Objects.equals(device.getNodeType(), nodeType)) {
            // 幂等：角色未变不写库、不失效缓存
            return device;
        }
        // 降级前确认名下无子设备
        if (DeviceNodeType.DIRECT.equals(nodeType) && baseMapper.countByParentId(deviceId) > 0L) {
            throw new BusinessException(ResultCode.DEVICE_HAS_SUBDEVICES);
        }

        device.setNodeType(nodeType);
        baseMapper.updateById(device);
        evictAuthCache(device);
        return device;
    }

    /**
     * 失效设备认证缓存，与 {@code DeviceServiceImpl#evictAuthCache} 同构：
     * 取产品键后按 {@code (productKey, deviceKey)} 失效，失败只告警不阻断业务（依赖 TTL 自然过期）。
     */
    private void evictAuthCache(Device device) {
        try {
            Product product = productService.requireById(device.getProductId());
            authCacheService.evict(product.getProductKey(), device.getDeviceKey());
        } catch (Exception e) {
            log.warn("拓扑变更后失效认证缓存失败，将依赖 TTL 自然过期: deviceId={}", device.getId(), e);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResultCode.DEVICE_TOPOLOGY_INVALID, message);
    }
}
