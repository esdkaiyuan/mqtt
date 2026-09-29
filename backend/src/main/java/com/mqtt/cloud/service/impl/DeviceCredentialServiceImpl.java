package com.mqtt.cloud.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.mapper.DeviceMapper;
import com.mqtt.cloud.service.DeviceCredentialResetService;
import com.mqtt.cloud.service.DeviceCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceCredentialServiceImpl implements DeviceCredentialService {

    /** 批量导出的单页大小：按页独立事务，避免全表长事务 */
    private static final int EXPORT_PAGE_SIZE = 500;

    private final DeviceMapper deviceMapper;
    private final DeviceCredentialResetService credentialResetService;

    @Override
    public String issueSecret(Long deviceId) {
        return credentialResetService.resetSecret(deviceId);
    }

    @Override
    public String resetSecret(Long deviceId) {
        return credentialResetService.resetSecret(deviceId);
    }

    /**
     * 导出全部设备凭据：按页查询、每页在独立事务中重置，替代原「全表一个长事务」。
     * <p>
     * 高危操作：重置会立即让存量设备旧凭据失效（见 DEPLOYMENT.md）。
     * 已逻辑删除的设备不参与导出（其密钥无意义，且重置会抛 DEVICE_NOT_FOUND 中断整批）。
     */
    @Override
    public List<String[]> exportCredentials() {
        List<String[]> rows = new ArrayList<>();
        long pageNum = 1;
        while (true) {
            Page<Device> page = new Page<>(pageNum, EXPORT_PAGE_SIZE);
            IPage<Device> devices = deviceMapper.selectPage(page,
                    Wrappers.<Device>lambdaQuery()
                            .eq(Device::getDeleted, 0)
                            .orderByAsc(Device::getId));
            if (devices.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(credentialResetService.resetPage(devices.getRecords()));
            if (pageNum >= devices.getPages()) {
                break;
            }
            pageNum++;
        }
        return rows;
    }
}