package com.mqtt.cloud.dto.response;

import java.time.LocalDateTime;

/**
 * 模拟上行结果（T-37）。
 */
public record DebugSimulateResponse(String deviceKey,
                                    String messageType,
                                    String topic,
                                    int size,
                                    boolean submitted,
                                    LocalDateTime acceptedAt) {
}
