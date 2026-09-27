package com.mqtt.cloud.service;

import java.util.List;

/**
 * 设备凭据服务：负责一机一密密钥的签发、重置与批量导出。
 */
public interface DeviceCredentialService {

    /**
     * 为设备签发一机一密密钥（覆盖既有密钥）。
     *
     * @return 一次性明文密钥，调用方需自行负责下发，平台不落库明文
     */
    String issueSecret(Long deviceId);

    /**
     * 重置设备密钥并返回一次性明文。
     */
    String resetSecret(Long deviceId);

    /**
     * 导出全部设备凭据，每行为 {@code [productKey, deviceKey, username, secret]}。
     * 导出会重置密钥，因此属于一次性操作。
     */
    List<String[]> exportCredentials();
}