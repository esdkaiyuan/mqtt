package com.mqtt.cloud.config;

import com.mqtt.cloud.common.security.RestSecurityExceptionHandler;
import com.mqtt.cloud.filter.ApiKeyAuthFilter;
import com.mqtt.cloud.filter.InternalTokenFilter;
import com.mqtt.cloud.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置。
 * <p>
 * 注意：{@code server.servlet.context-path=/api}，因此此处的 requestMatcher 必须使用
 * <b>去掉 context-path 之后</b>的路径（例如 {@code /devices/**} 而不是 {@code /api/devices/**}）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 无需认证即可访问的端点（登录/注册/健康检查/接口文档/监控抓取）。
     * <p>
     * Actuator 说明：{@code /actuator/health} 供容器与负载均衡探活；
     * {@code /actuator/prometheus} 供 Prometheus 无凭证抓取（如需收紧，删掉该行并改为内网抓取）。
     * 其余 {@code /actuator/**}（info、metrics 等）仍要求 ADMIN。
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/register",
            "/health",
            "/health/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/doc.html"
    };

    private final RestSecurityExceptionHandler securityExceptionHandler;
    private final InternalTokenFilter internalTokenFilter;

    public SecurityConfig(RestSecurityExceptionHandler securityExceptionHandler,
                          InternalTokenFilter internalTokenFilter) {
        this.securityExceptionHandler = securityExceptionHandler;
        this.internalTokenFilter = internalTokenFilter;
    }

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        CorsConfiguration consoleConfig = new CorsConfiguration();
        consoleConfig.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        consoleConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        consoleConfig.setAllowedHeaders(List.of("*"));
        consoleConfig.setAllowCredentials(true);
        consoleConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", consoleConfig);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter,
                                                    ApiKeyAuthFilter apiKeyFilter,
                                                    InternalTokenFilter internalTokenFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(securityExceptionHandler)
                    .accessDeniedHandler(securityExceptionHandler))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    // 内部回调：不参与 JWT 鉴权，由 InternalTokenFilter 校验共享令牌
                    .requestMatchers("/internal/**").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                    // 开放接口：必须携带合法 API Key（由 ApiKeyAuthFilter 注入 EXTERNAL 角色）
                    .requestMatchers("/external/v1/**").hasRole("EXTERNAL")
                    // 系统管理类接口仅 ADMIN 可访问
                    .requestMatchers("/api-keys/**", "/webhooks/**").hasRole("ADMIN")
                    // 监控端点：health 与 prometheus 已在 PUBLIC_ENDPOINTS 放行，其余（info/metrics）仅 ADMIN
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    // 实时消息与统计分析：ADMIN / OPERATOR
                    .requestMatchers("/messages/**", "/analytics/**").hasAnyRole("ADMIN", "OPERATOR")
                    .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            // 注意顺序：internalTokenFilter 以 JwtAuthenticationFilter 为锚点，
            // 必须在其之前完成 jwtFilter 的注册，否则锚点无已知顺序会抛 IllegalStateException
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalTokenFilter, JwtAuthenticationFilter.class)
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}