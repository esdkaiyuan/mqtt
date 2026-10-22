package com.mqtt.cloud.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                                .url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("apiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key"))
                        .addSecuritySchemes("deviceBasic", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
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
     * 产品管理API分组（设备类型模板）
     */
    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("产品管理")
                .pathsToMatch("/api/products", "/api/products/**")
                .build();
    }

    /**
     * 实时数据API分组（SSE）
     */
    @Bean
    public GroupedOpenApi realtimeApi() {
        return GroupedOpenApi.builder()
                .group("实时数据")
                .pathsToMatch("/api/realtime/**")
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

    /**
     * 统计分析API分组
     */
    @Bean
    public GroupedOpenApi analyticsApi() {
        return GroupedOpenApi.builder()
                .group("统计分析")
                .pathsToMatch("/api/analytics/**")
                .build();
    }

    /**
     * API密钥管理分组
     */
    @Bean
    public GroupedOpenApi apiKeyApi() {
        return GroupedOpenApi.builder()
                .group("API密钥")
                .pathsToMatch("/api/api-keys/**")
                .build();
    }

    /**
     * Webhook管理分组
     */
    @Bean
    public GroupedOpenApi webhookApi() {
        return GroupedOpenApi.builder()
                .group("Webhook")
                .pathsToMatch("/api/webhooks/**")
                .build();
    }

    /**
     * 外部开放API分组（X-API-Key 认证）
     */
    @Bean
    public GroupedOpenApi externalApi() {
        return GroupedOpenApi.builder()
                .group("外部API")
                .pathsToMatch("/api/external/v1/**")
                .build();
    }

    /**
     * 健康检查分组
     */
    @Bean
    public GroupedOpenApi healthApi() {
        return GroupedOpenApi.builder()
                .group("健康检查")
                .pathsToMatch("/api/health", "/api/health/**")
                .build();
    }

    /**
     * 告警中心API分组
     */
    @Bean
    public GroupedOpenApi alertApi() {
        return GroupedOpenApi.builder()
                .group("告警中心")
                .pathsToMatch("/api/alerts", "/api/alerts/**")
                .build();
    }

    @Bean
    public GroupedOpenApi deviceGroupApi() {
        return GroupedOpenApi.builder()
                .group("分组与标签")
                .pathsToMatch("/api/device-groups", "/api/device-groups/**",
                        "/api/device-tags", "/api/device-tags/**")
                .build();
    }

    @Bean
    public GroupedOpenApi automationApi() {
        return GroupedOpenApi.builder()
                .group("规则与场景")
                .pathsToMatch("/api/rules", "/api/rules/**", "/api/scenes", "/api/scenes/**")
                .build();
    }

    @Bean
    public GroupedOpenApi otaApi() {
        return GroupedOpenApi.builder()
                .group("OTA升级")
                .pathsToMatch("/api/ota/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationApi() {
        return GroupedOpenApi.builder()
                .group("通知与升级策略")
                .pathsToMatch("/api/notifications/**", "/api/notification-channels/**",
                        "/api/alert-escalations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi dashboardApi() {
        return GroupedOpenApi.builder()
                .group("看板与大屏")
                .pathsToMatch("/api/dashboards", "/api/dashboards/**",
                        "/api/public/dashboard-shares/**")
                .build();
    }

    @Bean
    public GroupedOpenApi hmiApi() {
        return GroupedOpenApi.builder()
                .group("快速上位机")
                .pathsToMatch("/api/hmi-views", "/api/hmi-views/**")
                .build();
    }

    @Bean
    public GroupedOpenApi propertiesApi() {
        return GroupedOpenApi.builder()
                .group("属性与设备日志")
                .pathsToMatch("/api/properties/**")
                .build();
    }

    @Bean
    public GroupedOpenApi deviceRegistrationApi() {
        return GroupedOpenApi.builder()
                .group("设备注册与激活")
                .pathsToMatch("/api/device-registration/**")
                .build();
    }

    @Bean
    public GroupedOpenApi ingestApi() {
        return GroupedOpenApi.builder()
                .group("设备HTTP上报")
                .pathsToMatch("/api/ingest/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("运维管理")
                .pathsToMatch("/api/admin/**")
                .build();
    }
}