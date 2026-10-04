package com.mqtt.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP 上报接入配置（T-24 设计文档 §12）。
 * <p>
 * 前缀 {@code app.http-ingest}，字段全部带默认值，风格对齐 {@link SceneProperties} /
 * {@link com.mqtt.cloud.ingest.IngestProperties}。
 * <p>
 * {@code enabled} 为总开关：置 {@code false} 时 {@code HttpIngestController} 因
 * {@code @ConditionalOnProperty} 不注册，{@link com.mqtt.cloud.filter.DeviceCredentialAuthFilter}
 * 仍为 Bean 但 {@code shouldNotFilter} 直接放行，{@code /ingest/**} 因无处理器可命返回 {@code 404}；
 * MQTT 链路不受影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.http-ingest")
public class HttpIngestProperties {

    /** 是否启用 HTTP 上报端点与设备凭据过滤器；false 时 /ingest/** 返回 404，MQTT 链路不受影响 */
    private boolean enabled = true;

    /** 单次上报请求体大小上限（字节），超限返回 413 + 6248，防止大包打爆内存 */
    private int maxPayloadBytes = 65536;

    /** Basic 认证失败时 WWW-Authenticate 头的 realm 值 */
    private String realm = "mqtt-cloud-device";
}
