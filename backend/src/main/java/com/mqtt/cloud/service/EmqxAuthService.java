package com.mqtt.cloud.service;

public interface EmqxAuthService {

    boolean authenticate(String username, String password, String clientId);
}