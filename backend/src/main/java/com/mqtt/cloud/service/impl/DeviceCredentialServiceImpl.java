package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceCredentialService;
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
public class DeviceCredentialServiceImpl implements DeviceCredentialService {

    private final DeviceMapper deviceMapper;
    private final ProductService productService;
    private final DeviceSecretService deviceSecretService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issueSecret(Long deviceId) {
        return resetSecret(deviceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(Long deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        String plaintext = deviceSecretService.generateSecret();
        device.setDeviceSecretHash(deviceSecretService.hash(plaintext));
        device.setSecretUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        return plaintext;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String[]> exportCredentials() {
        List<Device> devices = deviceMapper.selectList(
                Wrappers.<Device>lambdaQuery().orderByAsc(Device::getId));
        List<String[]> rows = new ArrayList<>(devices.size());
        for (Device device : devices) {
            Product product = productService.requireById(device.getProductId());
            String username = deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey());
            String plaintext = resetSecret(device.getId());
            rows.add(new String[]{product.getProductKey(), device.getDeviceKey(), username, plaintext});
        }
        return rows;
    }
}