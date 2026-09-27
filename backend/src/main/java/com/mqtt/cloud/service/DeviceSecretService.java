package com.mqtt.cloud.service;

public interface DeviceSecretService {

    String generateSecret();

    String hash(String rawSecret);

    boolean matches(String rawSecret, String hash);

    String buildUsername(String productKey, String deviceKey);

    String[] parseUsername(String username);

    boolean isPlatformUsername(String username);
}