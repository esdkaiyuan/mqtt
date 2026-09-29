package com.mqtt.cloud.service;

import com.mqtt.cloud.entity.Device;

import java.util.List;

/**
 * 一机一密密钥重置的独立事务边界。
 * <p>
 * 单独成 Bean 而非在 {@link DeviceCredentialService} 内自调用：Spring 事务基于代理实现，
 * 同类自调用不经过代理、事务不生效。批量导出需要「按页独立事务」，必须跨 Bean 调用。
 */
public interface DeviceCredentialResetService {

    /** 重置单台设备密钥，返回一次性明文（平台不落库明文）。 */
    String resetSecret(Long deviceId);

    /** 按页重置一批设备密钥，每页一个独立事务，返回每行 {@code [productKey, deviceKey, username, secret]}。 */
    List<String[]> resetPage(List<Device> devices);
}