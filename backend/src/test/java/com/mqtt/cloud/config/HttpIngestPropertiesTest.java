package com.mqtt.cloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 上报配置单测（T-24 实施计划 P1）。
 * <p>
 * 覆盖：默认值（{@code enabled=true} / {@code maxPayloadBytes=65536} /
 * {@code realm="mqtt-cloud-device"}）；前缀 {@code app.http-ingest} 可绑定并覆盖默认值；
 * 前缀缺省时保持默认值。
 */
class HttpIngestPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfig.class);

    @Test
    void defaults_should_match_design_doc() {
        HttpIngestProperties properties = new HttpIngestProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMaxPayloadBytes()).isEqualTo(65536);
        assertThat(properties.getRealm()).isEqualTo("mqtt-cloud-device");
    }

    @Test
    void binding_should_override_defaults_via_prefix() {
        contextRunner
                .withPropertyValues(
                        "app.http-ingest.enabled=false",
                        "app.http-ingest.max-payload-bytes=1024",
                        "app.http-ingest.realm=custom-realm")
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpIngestProperties.class);
                    HttpIngestProperties properties = context.getBean(HttpIngestProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getMaxPayloadBytes()).isEqualTo(1024);
                    assertThat(properties.getRealm()).isEqualTo("custom-realm");
                });
    }

    @Test
    void binding_should_keep_defaults_when_prefix_absent() {
        contextRunner.run(context -> {
            HttpIngestProperties properties = context.getBean(HttpIngestProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getMaxPayloadBytes()).isEqualTo(65536);
            assertThat(properties.getRealm()).isEqualTo("mqtt-cloud-device");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HttpIngestProperties.class)
    static class BindingConfig {
    }
}
