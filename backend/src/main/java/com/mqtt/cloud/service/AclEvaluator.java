package com.mqtt.cloud.service;

import com.mqtt.cloud.config.AccessControlProperties;
import com.mqtt.cloud.config.RealtimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AclEvaluator {

    public static final String ACTION_PUBLISH = "publish";
    public static final String ACTION_SUBSCRIBE = "subscribe";
    public static final String FRONTEND_USERNAME = "FRONTEND_READONLY";

    private static final String TOPIC_ROOT = "device/";

    private final AccessControlProperties accessControlProperties;
    private final RealtimeProperties realtimeProperties;
    private final DeviceSecretService deviceSecretService;

    public boolean allow(String username, String action, String topic) {
        if (!accessControlProperties.isEnforceAuth()) {
            return true;
        }
        if (username == null || action == null || topic == null) {
            return false;
        }
        if (deviceSecretService.isPlatformUsername(username)) {
            return allowPlatform(action, topic);
        }
        if (FRONTEND_USERNAME.equalsIgnoreCase(username)) {
            return realtimeProperties.isDirectFrontendEnabled()
                    && ACTION_SUBSCRIBE.equals(action)
                    && topic.startsWith(TOPIC_ROOT)
                    && topic.endsWith("/data");
        }
        String[] parts = deviceSecretService.parseUsername(username);
        if (parts == null) {
            return false;
        }
        return allowDevice(parts[1], action, topic);
    }

    private boolean allowPlatform(String action, String topic) {
        if (!topic.startsWith(TOPIC_ROOT)) {
            return false;
        }
        if (ACTION_SUBSCRIBE.equals(action)) {
            return true;
        }
        return ACTION_PUBLISH.equals(action) && topic.contains("/cmd/");
    }

    private boolean allowDevice(String deviceKey, String action, String topic) {
        String ownPrefix = TOPIC_ROOT + deviceKey + "/";
        if (!topic.startsWith(ownPrefix)) {
            return false;
        }
        if (ACTION_PUBLISH.equals(action)) {
            return !topic.startsWith(ownPrefix + "cmd/");
        }
        return ACTION_SUBSCRIBE.equals(action) && topic.startsWith(ownPrefix + "cmd/");
    }
}