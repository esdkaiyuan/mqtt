package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCredentialResetService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceCredentialResetServiceImpl implements DeviceCredentialResetService {

    private final DeviceMapper deviceMapper;
    private final ProductService productService;
    private final DeviceSecretService deviceSecretService;
    private final DeviceAuthCacheService authCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        Product product = productService.requireById(device.getProductId());
        return resetAndEvict(device, product.getProductKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String[]> resetPage(List<Device> devices) {
        List<String[]> rows = new ArrayList<>(devices.size());
        for (Device device : devices) {
            Product product = productService.requireById(device.getProductId());
            String plaintext = resetAndEvict(device, product.getProductKey());
            String username = deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey());
            rows.add(new String[]{product.getProductKey(), device.getDeviceKey(), username, plaintext});
        }
        return rows;
    }

    /**
     * 落库新密钥后立即失效认证缓存，避免旧哈希在 TTL 内继续放行。
     */
    private String resetAndEvict(Device device, String productKey) {
        String plaintext = deviceSecretService.generateSecret();
        device.setDeviceSecretHash(deviceSecretService.hash(plaintext));
        device.setSecretUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        authCacheService.evict(productKey, device.getDeviceKey());
        return plaintext;
    }
}