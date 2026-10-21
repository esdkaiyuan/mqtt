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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 设备拓扑服务实现（T-26 设计文档 §7 / §8.2，T-47 多级扩展）。
 * <p>
 * 校验矩阵集中在 {@link #attach(Long, Long)}：自环 {@code 6254}、父不存在 {@code 6255}、
 * 跨产品 {@code 6258}、深度超限 {@code 6257}、数量超限 {@code 6260}。父可以是
 * {@code GATEWAY} 或已挂载的 {@code SUB}（子网关）；{@code DIRECT} 首次挂载时提升为
 * {@code GATEWAY}。只允许挂载叶子设备，不支持整棵子树移动。
 * <p>
 * 解绑：叶子设备回落 {@code DIRECT}；带子设备的子网关收敛为根 {@code GATEWAY}，
 * 保住其下级子树。网关角色不回落 —— 角色代表「具备网关能力」。
 * <p>
 * 拓扑变更（挂载 / 解绑 / 角色变更 / 重挂载）失效相关设备认证缓存，使多级委托结果立即生效。
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
        // 父可以是 GATEWAY，也可以是已挂载的 SUB（子网关，T-47）；DIRECT 首次挂载时自动提升
        Device child = baseMapper.findActiveById(subDeviceId);
        if (child == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        if (!Objects.equals(gateway.getProductId(), child.getProductId())) {
            throw new BusinessException(ResultCode.DEVICE_PRODUCT_MISMATCH);
        }

        // 只允许挂载叶子设备：不支持整棵子树移动，避免一次操作重排多级拓扑
        if (baseMapper.countByParentId(subDeviceId) > 0L) {
            throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED,
                    "仅允许挂载无下级的设备（不支持整棵子树移动）");
        }
        int maxDepth = topologyProperties.getMaxDepth();
        int parentDepth = depthOf(gateway);
        if (maxDepth < MIN_SUPPORTED_DEPTH || parentDepth + 1 > maxDepth) {
            throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED,
                    "拓扑深度不能超过 " + maxDepth + " 层");
        }
        if (baseMapper.countByParentId(gatewayId) >= topologyProperties.getMaxSubDevicesPerGateway()) {
            throw new BusinessException(ResultCode.DEVICE_SUBDEVICE_LIMIT_EXCEEDED);
        }

        Long previousParentId = child.getParentId();
        child.setNodeType(DeviceNodeType.SUB);
        child.setParentId(gatewayId);
        baseMapper.updateById(child);

        // 首次挂载自动收敛角色：DIRECT 提升为 GATEWAY；已是 SUB/GATEWAY 则保持
        if (DeviceNodeType.DIRECT.equals(gateway.getNodeType())) {
            gateway.setNodeType(DeviceNodeType.GATEWAY);
            baseMapper.updateById(gateway);
        }

        evictAuthCache(child);
        evictAuthCache(gateway);
        // 重挂载：旧父设备不再拥有该子设备，委托缓存必须一并失效
        if (previousParentId != null && !previousParentId.equals(gatewayId)) {
            Device previousParent = baseMapper.findActiveById(previousParentId);
            if (previousParent != null) {
                evictAuthCache(previousParent);
            }
        }
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

        // 带子设备的子网关解绑后收敛为根 GATEWAY，保住其下级子树；叶子设备回 DIRECT
        boolean hasChildren = baseMapper.countByParentId(subDeviceId) > 0L;
        child.setParentId(null);
        child.setNodeType(hasChildren ? DeviceNodeType.GATEWAY : DeviceNodeType.DIRECT);
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
     * 计算设备在拓扑中的深度（根网关为 1）。父链最多 {@code maxDepth} 层，
     * 同时用 visited 集合防御脏数据造成的环。
     */
    private int depthOf(Device device) {
        int depth = 1;
        Set<Long> visited = new HashSet<>();
        Device current = device;
        int limit = Math.max(1, topologyProperties.getMaxDepth());
        while (current.getParentId() != null) {
            if (!visited.add(current.getId())) {
                throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED, "拓扑存在环");
            }
            current = baseMapper.findActiveById(current.getParentId());
            if (current == null) {
                throw new BusinessException(ResultCode.DEVICE_TOPOLOGY_DEPTH_EXCEEDED, "拓扑父链不完整");
            }
            depth++;
            if (depth > limit) {
                return depth;
            }
        }
        return depth;
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
