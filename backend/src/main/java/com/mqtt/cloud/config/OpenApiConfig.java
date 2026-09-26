package com.mqtt.cloud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger配置
 * 访问地址：http://localhost:8080/api/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MQTT云平台 API文档")
                        .description("MQTT云平台系统的后端API接口文档，支持设备管理、消息监控、历史查询等功能")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MQTT Cloud Team")
                                .email("dev@mqtt-cloud.local"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }

    /**
     * 认证管理API分组
     */
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("认证管理")
                .pathsToMatch("/api/auth/**")
                .build();
    }

    /**
     * 设备管理API分组
     */
    @Bean
    public GroupedOpenApi deviceApi() {
        return GroupedOpenApi.builder()
                .group("设备管理")
                .pathsToMatch("/api/devices/**")
                .build();
    }

    /**
     * 消息管理API分组
     */
    @Bean
    public GroupedOpenApi messageApi() {
        return GroupedOpenApi.builder()
                .group("消息管理")
                .pathsToMatch("/api/messages/**")
                .build();
    }

    /**
     * 历史查询API分组
     */
    @Bean
    public GroupedOpenApi historyApi() {
        return GroupedOpenApi.builder()
                .group("历史查询")
                .pathsToMatch("/api/history/**")
                .build();
    }
}
