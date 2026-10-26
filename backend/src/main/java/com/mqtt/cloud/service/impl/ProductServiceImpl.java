package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.AuthModeValue;
import com.mqtt.cloud.common.constant.ProtocolTypeValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.util.AfterCommit;
import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.ProductMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.EmqxClientKicker;
import com.mqtt.cloud.service.PayloadCodecService;
import com.mqtt.cloud.service.ProductService;
import com.mqtt.cloud.service.TopicPrefixResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private static final String DEFAULT_PAYLOAD_FORMAT = "JSON";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String RESERVED_KEY = "platform";
    private static final int MAX_DRIVER_ID_LENGTH = 64;
    private static final int MAX_DRIVER_CONFIG_LENGTH = 4096;

    /** 支持的产品认证方式，取值集合以 {@link AuthModeValue} 为准。 */
    private static final Set<String> SUPPORTED_AUTH_MODES =
            Set.of(AuthModeValue.SECRET, AuthModeValue.X509, AuthModeValue.JWT);

    private final ProductMapper productMapper;

    private final DeviceMapper deviceMapper;

    private final DeviceAuthCacheService authCacheService;

    private final DeviceSecretService deviceSecretService;

    private final EmqxClientKicker emqxClientKicker;

    private final TopicPrefixResolver topicPrefixResolver;

    private final PayloadCodecService payloadCodecService;

    @Override
    @Transactional
    public Product create(ProductRequest request) {
        String productKey = request.getProductKey();
        if (RESERVED_KEY.equalsIgnoreCase(productKey)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品标识 " + RESERVED_KEY + " 为保留名");
        }
        // 先做写入期校验，非法入参不必再走一次查重
        String authMode = resolveAuthMode(request.getAuthMode());
        String topicPrefix = topicPrefixResolver.validateTemplate(request.getTopicPrefix());
        if (existsByKey(productKey, null)) {
            throw new BusinessException(ResultCode.PRODUCT_KEY_EXISTS);
        }
        Product product = new Product();
        product.setProductKey(productKey);
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setAuthMode(authMode);
        product.setTopicPrefix(topicPrefix);
        product.setPayloadFormat(StringUtils.hasText(request.getPayloadFormat())
                ? request.getPayloadFormat() : DEFAULT_PAYLOAD_FORMAT);
        product.setPayloadCodec(payloadCodecService.validateAndNormalize(request.getPayloadCodec()));
        product.setMetadataSchema(request.getMetadataSchema());
        applyProtocol(product, request, true);
        product.setStatus(STATUS_ENABLED);
        productMapper.insert(product);
        return product;
    }

    @Override
    public List<Product> list() {
        return productMapper.selectList(
                Wrappers.<Product>lambdaQuery().orderByAsc(Product::getId));
    }

    @Override
    public List<Product> listEnabled() {
        return productMapper.selectList(
                Wrappers.<Product>lambdaQuery()
                        .eq(Product::getStatus, STATUS_ENABLED)
                        .orderByAsc(Product::getId));
    }

    @Override
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product existing = requireById(id);
        if (existsByKey(request.getProductKey(), id)) {
            throw new BusinessException(ResultCode.PRODUCT_KEY_EXISTS);
        }
        String previousKey = existing.getProductKey();
        String previousAuthMode = existing.getAuthMode();
        String previousTopicPrefix = existing.getTopicPrefix();
        existing.setProductKey(request.getProductKey());
        existing.setProductName(request.getProductName());
        existing.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getTopicPrefix())) {
            existing.setTopicPrefix(topicPrefixResolver.validateTemplate(request.getTopicPrefix()));
        }
        if (StringUtils.hasText(request.getAuthMode())) {
            existing.setAuthMode(resolveAuthMode(request.getAuthMode()));
        }
        if (StringUtils.hasText(request.getPayloadFormat())) {
            existing.setPayloadFormat(request.getPayloadFormat());
        }
        if (request.getPayloadCodec() != null) {
            // null=保持原值，空串=清空；与 driverConfig 的「未加载不修改」语义保持一致
            existing.setPayloadCodec(payloadCodecService.validateAndNormalize(request.getPayloadCodec()));
        }
        existing.setMetadataSchema(request.getMetadataSchema());
        applyProtocol(existing, request, false);
        productMapper.updateById(existing);
        AfterCommit.run(() -> payloadCodecService.evict(id));
        // 产品标识变更会改变用户名与缓存键，旧键下的元数据不再可达，主动清理
        if (!previousKey.equals(existing.getProductKey())) {
            authCacheService.evictProduct(previousKey);
        }
        // 认证方式与主题前缀直接决定授权判定结果，变更后 TTL 内的旧快照仍会放行，须立即失效
        if (!Objects.equals(previousAuthMode, existing.getAuthMode())
                || !Objects.equals(previousTopicPrefix, existing.getTopicPrefix())) {
            authCacheService.evictProduct(existing.getProductKey());
        }
        return existing;
    }

    /**
     * 应用接入协议与驱动配置（T-31 P4）。
     * <p>
     * 更新时 {@code protocolType} 为空表示保持原值 —— 旧客户端编辑产品时不会传该字段，
     * 若按「空 = MQTT」处理，会把已配置的 MODBUS 产品静默改回 MQTT，采集直接停摆。
     * 切换到非驱动协议（MQTT / HTTP / GATEWAY_MQTT）时清空驱动字段，避免遗留配置误导排障。
     */
    private void applyProtocol(Product product, ProductRequest request, boolean creating) {
        String protocol = request.getProtocolType() == null
                ? (creating ? ProtocolTypeValue.MQTT : product.getProtocolType())
                : resolveProtocol(request.getProtocolType());
        if (!StringUtils.hasText(protocol)) {
            protocol = ProtocolTypeValue.MQTT;
        }
        product.setProtocolType(protocol);

        boolean driverProtocol = ProtocolTypeValue.MODBUS.equals(protocol)
                || ProtocolTypeValue.OPC_UA.equals(protocol);
        if (!driverProtocol) {
            product.setDriverId(null);
            product.setDriverConfig(null);
            return;
        }

        if (request.getDriverId() != null) {
            String driverId = request.getDriverId().trim();
            if (driverId.length() > MAX_DRIVER_ID_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "驱动标识不能超过 " + MAX_DRIVER_ID_LENGTH + " 字符");
            }
            // 更新时空串视为「未加载 / 不修改」：前端 driver_config 需单独拉取，
            // 若把空串当作 clear，用户打开编辑框立刻保存就会把现场连接参数抹掉。
            if (!driverId.isEmpty() || creating) {
                product.setDriverId(driverId.isEmpty() ? null : driverId);
            }
        } else if (creating) {
            product.setDriverId(null);
        }

        if (request.getDriverConfig() != null) {
            String driverConfig = request.getDriverConfig().trim();
            if (driverConfig.length() > MAX_DRIVER_CONFIG_LENGTH) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "驱动配置不能超过 " + MAX_DRIVER_CONFIG_LENGTH + " 字符");
            }
            if (!driverConfig.isEmpty() || creating) {
                product.setDriverConfig(driverConfig.isEmpty() ? null : driverConfig);
            }
        } else if (creating) {
            product.setDriverConfig(null);
        }
    }

    private String resolveProtocol(String raw) {
        String normalized = ProtocolTypeValue.normalize(raw.trim().toUpperCase(Locale.ROOT));
        if (!ProtocolTypeValue.isSupported(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的接入协议: " + raw);
        }
        return normalized;
    }

    /**
     * 校验并归一化认证方式。空值回落 {@link AuthModeValue#SECRET}，与认证回调侧口径一致：
     * 新增列虽有默认值，但存量缓存快照可能尚未回填，回落可避免整条链路不可用。
     */
    private String resolveAuthMode(String authMode) {
        if (!StringUtils.hasText(authMode)) {
            return AuthModeValue.SECRET;
        }
        String normalized = authMode.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_AUTH_MODES.contains(normalized)) {
            throw new BusinessException(ResultCode.AUTH_MODE_UNSUPPORTED, "不支持的认证方式: " + authMode);
        }
        return normalized;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = requireById(id);
        long deviceCount = deviceMapper.selectCount(
                Wrappers.<com.mqtt.cloud.entity.Device>lambdaQuery()
                        .eq(com.mqtt.cloud.entity.Device::getProductId, id));
        if (deviceCount > 0) {
            throw new BusinessException(ResultCode.PRODUCT_HAS_DEVICES);
        }
        productMapper.deleteById(id);
        payloadCodecService.evict(id);
        // 产品停用/删除后其下设备不应再通过认证；停用走状态变更时同样需要失效
        authCacheService.evictProduct(product.getProductKey());
    }

    @Override
    @Transactional
    public void disableProduct(Long id) {
        setProductStatus(id, STATUS_DISABLED);
    }

    @Override
    @Transactional
    public void enableProduct(Long id) {
        setProductStatus(id, STATUS_ENABLED);
    }

    /**
     * 统一设置产品状态。状态变更会改变旗下设备的认证判定结果，
     * 因此必须失效该产品下全部设备的认证缓存，避免 TTL 内旧元数据继续放行。
     * <p>
     * 停用时额外踢掉旗下设备的在线连接；状态未变化视为幂等空操作，不产生任何副作用。
     */
    private void setProductStatus(Long id, String status) {
        Product product = requireById(id);
        if (status.equals(product.getStatus())) {
            return;
        }
        product.setStatus(status);
        productMapper.updateById(product);
        authCacheService.evictProduct(product.getProductKey());
        if (STATUS_DISABLED.equals(status)) {
            kickProductSessions(product);
        }
    }

    /**
     * 踢掉该产品下全部设备的在线连接。事务提交后执行，避免网络 I/O 占用数据库连接；
     * 踢线失败不影响停用本身，由授权缓存 TTL 兜底收敛。
     */
    private void kickProductSessions(Product product) {
        try {
            List<Device> devices = deviceMapper.selectList(
                    Wrappers.<Device>lambdaQuery().eq(Device::getProductId, product.getId()));
            List<String> usernames = devices.stream()
                    .map(device -> deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey()))
                    .filter(StringUtils::hasText)
                    .toList();
            if (usernames.isEmpty()) {
                return;
            }
            AfterCommit.run(() -> usernames.forEach(emqxClientKicker::kickByUsername));
        } catch (Exception e) {
            log.warn("停用产品时解析踢线用户名失败，将依赖授权缓存 TTL 收敛: productKey={}",
                    product.getProductKey(), e);
        }
    }

    @Override
    public Product getByProductKey(String productKey) {
        return productMapper.selectOne(
                Wrappers.<Product>lambdaQuery().eq(Product::getProductKey, productKey));
    }

    @Override
    public Product requireById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private boolean existsByKey(String productKey, Long excludeId) {
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getProductKey, productKey)
                .ne(excludeId != null, Product::getId, excludeId)) > 0;
    }
}