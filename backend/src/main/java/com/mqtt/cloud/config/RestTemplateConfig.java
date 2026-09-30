package com.mqtt.cloud.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端配置
 * <p>
 * Webhook 回调必须有超时限制，否则线程会长时间挂起。
 * <p>
 * 显式指定 {@link SimpleClientHttpRequestFactory}（HttpURLConnection / HTTP/1.1）：Spring Boot 4
 * 起 RestTemplate 默认改用 JDK {@code java.net.http.HttpClient}，其明文请求会先发 HTTP/2（h2c）升级，
 * 而 EMQX 5.0 的 Dashboard（Cowboy）对此直接断连，表现为 {@code EOF reached while reading} ——
 * 踢线接口与 EMQX 管理面调用会全部失败。退回 HttpURLConnection 与 {@code emqx-init.sh} 的 curl 行为一致。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return builder.requestFactory(() -> factory).build();
    }
}