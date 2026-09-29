package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.service.DeviceSecretService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class DeviceSecretServiceImpl implements DeviceSecretService {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 32;
    public static final String PLATFORM_USERNAME = "PLATFORM";

    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String generateSecret() {
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    @Override
    public String hash(String rawSecret) {
        return encoder.encode(rawSecret);
    }

    @Override
    public boolean matches(String rawSecret, String hash) {
        if (rawSecret == null || hash == null || hash.isBlank()) {
            return false;
        }
        return encoder.matches(rawSecret, hash);
    }

    @Override
    public String buildUsername(String productKey, String deviceKey) {
        return productKey + "." + deviceKey;
    }

    @Override
    public String[] parseUsername(String username) {
        if (username == null) {
            return null;
        }
        int idx = username.indexOf('.');
        if (idx <= 0 || idx != username.lastIndexOf('.') || idx == username.length() - 1) {
            return null;
        }
        return new String[]{username.substring(0, idx), username.substring(idx + 1)};
    }

    @Override
    public boolean isPlatformUsername(String username) {
        return PLATFORM_USERNAME.equalsIgnoreCase(username);
    }
}