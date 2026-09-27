package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.CreateDeviceDTO;
import com.mqtt.cloud.dto.request.DeviceQueryDTO;
import com.mqtt.cloud.dto.request.UpdateDeviceDTO;
import com.mqtt.cloud.dto.response.DeviceCreatedDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.DeviceService;
import com.mqtt.cloud.service.DeviceStatusHistoryService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final DeviceStatusHistoryService deviceStatusHistoryService;
    private final DeviceCredentialService deviceCredentialService;
    private final DeviceSecretService deviceSecretService;
    private final ProductService productService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCreatedDTO createDevice(Long userId, CreateDeviceDTO dto) {
        Product product = productService.requireById(dto.getProductId());

        // deviceKey 唯一性收敛到产品维度，与 uk_product_device 保持一致
        LambdaQueryWrapper<Device> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(Device::getProductId, product.getId())
                  .eq(Device::getDeviceKey, dto.getDeviceKey())
                  .eq(Device::getDeleted, 0);
        if (this.count(keyWrapper) > 0) {
            throw new BusinessException(ResultCode.DEVICE_KEY_EXISTS);
        }

        Device device = new Device();
        device.setProductId(product.getId());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceKey(dto.getDeviceKey());
        device.setDeviceType(dto.getDeviceType());
        device.setTopic(dto.getTopic());
        device.setDescription(dto.getDescription());
        device.setOwnerId(userId);
        device.setStatus(DeviceStatusValue.INACTIVE);
        device.setLastSeen(null);
        device.setEnabled(1);
        device.setDeleted(0);

        this.save(device);

        // 与设备创建同事务签发凭据：签发失败则设备创建一并回滚，避免产生无凭据的“半成品”设备
        String plaintext = deviceCredentialService.issueSecret(device.getId());

        DeviceCreatedDTO response = new DeviceCreatedDTO();
        response.setId(device.getId());
        response.setDeviceKey(device.getDeviceKey());
        response.setProductKey(product.getProductKey());
        response.setUsername(deviceSecretService.buildUsername(product.getProductKey(), device.getDeviceKey()));
        response.setDeviceSecret(plaintext);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device updateDevice(Long deviceId, UpdateDeviceDTO dto) {
        Device device = this.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }

        if (dto.getDeviceName() != null) device.setDeviceName(dto.getDeviceName());
        if (dto.getDeviceType() != null) device.setDeviceType(dto.getDeviceType());
        if (dto.getTopic() != null) device.setTopic(dto.getTopic());
        if (dto.getDescription() != null) device.setDescription(dto.getDescription());

        this.updateById(device);
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long deviceId) {
        Device device = this.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        // 显式 SQL 更新，确保 deleted 标记落库（MyBatis-Plus 逻辑删除对 updateById 有额外语义）
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, deviceId)
               .set(Device::getDeleted, 1)
               .set(Device::getStatus, DeviceStatusValue.OFFLINE);
        this.baseMapper.update(null, wrapper);
    }

    @Override
    public IPage<Device> getDevices(Long userId, DeviceQueryDTO dto) {
        Page<Device> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        if (dto.getOwnerId() == null) {
            dto.setOwnerId(userId);
        }
        return this.baseMapper.pageQuery(page, dto, dto.getOwnerId());
    }

    @Override
    public Device getDeviceById(Long deviceId) {
        Device device = this.getById(deviceId);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    @Override
    public Device getDeviceByKey(String deviceKey) {
        Device device = this.baseMapper.findByDeviceKey(deviceKey);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    @Override
    public List<Device> getOnlineDevices() {
        return this.baseMapper.findOnlineDevices(null);
    }

    @Override
    public List<Device> getOnlineDevices(Long ownerId) {
        return this.baseMapper.findOnlineDevices(ownerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceStatus(Long deviceId, String status) {
        Device device = this.getById(deviceId);
        if (device == null) {
            return;
        }

        String oldStatus = device.getStatus();
        boolean statusChanged = !Objects.equals(oldStatus, status);

        if (statusChanged) {
            device.setStatus(status);
            if (DeviceStatusValue.ONLINE.equals(status)) {
                device.setLastSeen(LocalDateTime.now());
            }
            this.updateById(device);
            deviceStatusHistoryService.recordStatusChange(deviceId, status);
        } else if (DeviceStatusValue.ONLINE.equals(status)) {
            // 状态未变化但设备仍在上报：仅刷新 lastSeen，避免被离线巡检误判
            LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Device::getId, deviceId)
                   .set(Device::getLastSeen, LocalDateTime.now());
            this.baseMapper.update(null, wrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceStatusByKey(String deviceKey, String status) {
        Device device = this.baseMapper.findByDeviceKey(deviceKey);
        if (device == null) return;
        updateDeviceStatus(device.getId(), status);
    }
}
