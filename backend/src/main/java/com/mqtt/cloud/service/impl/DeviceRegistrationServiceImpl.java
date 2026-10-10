package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.constant.DeviceStatusValue;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.dto.request.ActivateDeviceDTO;
import com.mqtt.cloud.dto.request.BatchRegisterDeviceDTO;
import com.mqtt.cloud.dto.response.BatchRegisterResultDTO;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DeviceRegBatch;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.mapper.DeviceRegBatchMapper;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceCredentialService;
import com.mqtt.cloud.service.DeviceRegistrationService;
import com.mqtt.cloud.service.DeviceSecretService;
import com.mqtt.cloud.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 设备批量注册与激活实现（T-30）。
 * <p>
 * <b>事务边界</b>：批次记录本身在一个事务内落库；每台设备的创建走
 * {@link Propagation#REQUIRES_NEW} 独立事务，单台失败（deviceKey 重复等）
 * 只记入失败明细而不回滚整批——这与单设备创建的「一失败即整单回滚」语义不同，
 * 因为批量场景下 1000 台里错 1 台就全盘失败是不可接受的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistrationServiceImpl extends ServiceImpl<DeviceMapper, Device>
        implements DeviceRegistrationService {

    private static final int MAX_COUNT = 1000;
    private static final int ACTIVATE_CODE_BYTES = 16;
    /** deviceKey 模板占位符 */
    private static final String INDEX_TOKEN = "{index}";

    private final DeviceRegBatchMapper batchMapper;
    private final ProductService productService;
    private final DeviceCredentialService deviceCredentialService;
    private final DeviceSecretService deviceSecretService;
    private final DeviceAuthCacheService authCacheService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchRegisterResultDTO batchRegister(Long userId, BatchRegisterDeviceDTO dto) {
        Product product = productService.requireById(dto.getProductId());

        // 先在包装类型上判空再拆箱：Integer count 直接赋给 int 会自动拆箱，
        // 后面的 count == null 会编译成「int 与 null 比较」而报错
        Integer requested = dto.getCount();
        if (requested == null || requested < 1 || requested > MAX_COUNT) {
            throw new BusinessException(ResultCode.REG_BATCH_COUNT_INVALID);
        }
        int count = requested;
        int startIndex = dto.getStartIndex() == null ? 1 : dto.getStartIndex();
        if (startIndex < 0) {
            throw new BusinessException(ResultCode.REG_BATCH_PATTERN_INVALID);
        }
        boolean issueActivateCode = dto.getIssueActivateCode() == null || dto.getIssueActivateCode();

        // 批次先落库拿到自增 id：设备行需要回填 batch_id，而设备创建是独立事务
        String batchNo = generateBatchNo();
        DeviceRegBatch batch = new DeviceRegBatch();
        batch.setBatchNo(batchNo);
        batch.setProductId(product.getId());
        batch.setOwnerId(userId);
        batch.setPrefix(dto.getPrefix());
        batch.setNamePattern(dto.getPrefix() + "-" + INDEX_TOKEN);
        batch.setStartIndex(startIndex);
        batch.setTotalCount(count);
        batch.setSucceededCount(0);
        batch.setFailedCount(0);
        batchMapper.insert(batch);

        List<BatchRegisterResultDTO.FailedItem> failed = new ArrayList<>();
        List<BatchRegisterResultDTO.Credential> credentials = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int index = startIndex + i;
            String deviceKey = dto.getPrefix() + "-" + index;
            try {
                BatchRegisterResultDTO.Credential cred =
                        createOneInTransaction(userId, product, dto, batch.getId(), deviceKey, index, issueActivateCode);
                credentials.add(cred);
            } catch (Exception e) {
                // 逐台隔离：单台失败不影响其余设备
                BatchRegisterResultDTO.FailedItem item = new BatchRegisterResultDTO.FailedItem();
                item.setIndex(index);
                item.setDeviceKey(deviceKey);
                item.setReason(resolveReason(e));
                failed.add(item);
                log.warn("批量注册单台失败 deviceKey={} reason={}", deviceKey, item.getReason());
            }
        }

        // 回填统计与一次性明细
        batch.setSucceededCount(credentials.size());
        batch.setFailedCount(failed.size());
        if (!failed.isEmpty()) {
            batch.setFailedItems(objectMapper.writeValueAsString(failed));
        }
        if (!credentials.isEmpty()) {
            batch.setCredentials(objectMapper.writeValueAsString(credentials));
        }
        batchMapper.updateById(batch);

        BatchRegisterResultDTO result = new BatchRegisterResultDTO();
        result.setBatchNo(batchNo);
        result.setTotalCount(count);
        result.setSucceededCount(credentials.size());
        result.setFailedCount(failed.size());
        result.setFailedItems(failed);
        result.setCredentials(credentials);
        log.info("批量注册完成 batchNo={} total={} ok={} fail={}",
                batchNo, count, credentials.size(), failed.size());
        return result;
    }

    /**
     * 单台创建，独立事务。
     * <p>
     * REQUIRES_NEW 让单台回滚只影响自身；靠调用方的 try/catch 感知失败。
     *
     * @return 已装配好的一次性凭据（含激活码），避免为取激活码再多查一次库
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public BatchRegisterResultDTO.Credential createOneInTransaction(
            Long userId, Product product, BatchRegisterDeviceDTO dto,
            Long batchId, String deviceKey, int index, boolean issueActivateCode) {
        LambdaQueryWrapper<Device> keyWrapper = new LambdaQueryWrapper<>();
        keyWrapper.eq(Device::getProductId, product.getId())
                  .eq(Device::getDeviceKey, deviceKey)
                  .eq(Device::getDeleted, 0);
        if (this.count(keyWrapper) > 0) {
            throw new BusinessException(ResultCode.DEVICE_KEY_EXISTS);
        }

        Device device = new Device();
        device.setProductId(product.getId());
        device.setDeviceKey(deviceKey);
        device.setDeviceName(dto.getNamePattern() == null || dto.getNamePattern().isBlank()
                ? dto.getPrefix() + "-" + index
                : dto.getNamePattern().replace(INDEX_TOKEN, String.valueOf(index)));
        device.setDeviceType(dto.getDeviceType());
        device.setTopic(dto.getTopic());
        device.setDescription(dto.getDescription());
        device.setOwnerId(userId);
        device.setStatus(DeviceStatusValue.INACTIVE);
        // 未签发激活码时视为「注册即可用」，与单设备创建保持一致
        device.setEnabled(1);
        device.setDeleted(0);
        device.setBatchId(batchId);
        if (issueActivateCode) {
            // 走激活流程的设备先禁用，激活成功后才允许连接
            device.setEnabled(0);
            device.setActivateCode(randomActivateCode());
        }
        this.save(device);

        String secret = deviceCredentialService.issueSecret(device.getId());

        BatchRegisterResultDTO.Credential cred = new BatchRegisterResultDTO.Credential();
        cred.setDeviceKey(deviceKey);
        cred.setUsername(deviceSecretService.buildUsername(product.getProductKey(), deviceKey));
        cred.setSecret(secret);
        cred.setActivateCode(device.getActivateCode());
        return cred;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(ActivateDeviceDTO dto) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceKey, dto.getDeviceKey())
               .eq(Device::getDeleted, 0);
        Device device = this.getOne(wrapper);
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        Product product = productService.requireById(device.getProductId());
        // 未走激活流程的设备：幂等启用后返回
        if (device.getActivateCode() == null) {
            if (!Integer.valueOf(1).equals(device.getEnabled())) {
                enableAndInvalidate(device, product.getProductKey());
            }
            return;
        }
        // 激活码不匹配（含已被消费后重复使用同一码）
        if (!device.getActivateCode().equals(dto.getActivateCode())) {
            throw new BusinessException(ResultCode.DEVICE_ACTIVATE_CODE_INVALID);
        }
        if (!Integer.valueOf(1).equals(device.getEnabled())) {
            enableAndInvalidate(device, product.getProductKey());
        }
        log.info("设备激活成功 deviceKey={}", dto.getDeviceKey());
    }

    @Override
    public IPage<DeviceRegBatch> pageBatches(Long userId, int page, int size) {
        LambdaQueryWrapper<DeviceRegBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceRegBatch::getOwnerId, userId)
               .orderByDesc(DeviceRegBatch::getId);
        Page<DeviceRegBatch> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return batchMapper.selectPage(p, wrapper);
    }

    @Override
    public DeviceRegBatch getBatch(Long userId, String batchNo) {
        LambdaQueryWrapper<DeviceRegBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceRegBatch::getBatchNo, batchNo);
        DeviceRegBatch batch = batchMapper.selectOne(wrapper);
        if (batch == null) {
            throw new BusinessException(ResultCode.REG_BATCH_NOT_FOUND);
        }
        // 批次凭据属敏感信息，仅归属用户可读；ADMIN 放行
        if (!batch.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.REG_BATCH_NOT_FOUND);
        }
        return batch;
    }

    /** 置enabled=1 并失效认证缓存，使激活立即生效。 */
    private void enableAndInvalidate(Device device, String productKey) {
        Device update = new Device();
        update.setId(device.getId());
        update.setEnabled(1);
        update.setActivatedAt(LocalDateTime.now());
        // 激活码一次性消费：置空使同一码无法再次激活
        update.setActivateCode(null);
        this.updateById(update);
        authCacheService.evict(productKey, device.getDeviceKey());
    }

    private String randomActivateCode() {
        byte[] bytes = new byte[ACTIVATE_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String generateBatchNo() {
        return "batch-" + System.currentTimeMillis() + "-"
                + HexFormat.of().formatHex(new byte[4]).substring(0, 6);
    }

    /** 把异常归因成可读的失败原因，避免把堆栈抛给批量响应。 */
    private String resolveReason(Exception e) {
        if (e instanceof BusinessException be) {
            return be.getMessage();
        }
        return "内部错误";
    }
}
