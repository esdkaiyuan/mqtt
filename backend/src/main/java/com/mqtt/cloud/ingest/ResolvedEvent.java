package com.mqtt.cloud.ingest;

import com.mqtt.cloud.entity.Device;

/**
 * 已解析出设备的上行事件，供下游 Webhook / 实时通道分发。
 */
public record ResolvedEvent(Device device, IngestRecord record) {
}