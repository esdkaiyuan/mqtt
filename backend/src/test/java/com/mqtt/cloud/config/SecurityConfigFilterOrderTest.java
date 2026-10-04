package com.mqtt.cloud.config;

import com.mqtt.cloud.filter.DeviceCredentialAuthFilter;
import com.mqtt.cloud.filter.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SecurityConfig} 的过滤器登记顺序与放行规则守护测试（T-24 §10.2 / P4）。
 * <p>
 * 目的：确保 {@link DeviceCredentialAuthFilter} 被登记进过滤器链且<b>早于</b>
 * {@link JwtAuthenticationFilter}（锚点误序会在启动时抛 {@code IllegalStateException}），
 * 且 {@code /ingest/**} 命中 {@code permitAll}（业务鉴权由设备凭据过滤器独立负责，与 {@code /internal/**} 同构）。
 * <p>
 * 测试期显式关闭 {@code app.http-ingest.enabled}，使设备凭据过滤器 {@code shouldNotFilter} 直接放行、
 * 上报控制器亦不注册，从而可在「无凭据」条件下纯粹验证 Spring Security 的放行规则
 * （放行后下游过滤链会被调用，最终由无处理器映射返回 404，而非 401/403）。
 * <p>
 * 说明：本项目为 Spring Boot 4.1.1，其测试栈不含 {@code @AutoConfigureMockMvc} /
 * {@code TestRestTemplate}，故此处直接以 {@code FilterChainProxy} + spring-test 的 mock
 * servlet 对象驱动安全过滤链，不依赖 Boot 的 MockMvc 自动配置。
 */
@SpringBootTest
@TestPropertySource(properties = "app.http-ingest.enabled=false")
class SecurityConfigFilterOrderTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("上下文可加载：SecurityFilterChain 构建成功，未抛 IllegalStateException")
    void securityFilterChainBuilds() {
        assertThat(securityFilterChain).isNotNull();
        assertThat(resolveFilterChainProxy()).isNotNull();
    }

    @Test
    @DisplayName("DeviceCredentialAuthFilter 已登记，且在链中早于 JwtAuthenticationFilter")
    void deviceCredentialFilterPrecedesJwtFilter() {
        List<Filter> filters = securityFilterChain.getFilters();
        int device = indexOf(filters, DeviceCredentialAuthFilter.class);
        int jwt = indexOf(filters, JwtAuthenticationFilter.class);

        assertThat(device).as("DeviceCredentialAuthFilter 必须被登记进过滤器链").isNotNegative();
        assertThat(jwt).as("JwtAuthenticationFilter 必须被登记进过滤器链").isNotNegative();
        assertThat(device).as("设备凭据鉴权必须先于 JWT 鉴权").isLessThan(jwt);
    }

    @Test
    @DisplayName("/ingest/** 为 permitAll：匿名请求不被 Spring Security 以 401/403 拦截")
    void ingestPathIsPermitAll() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest/dev001/data");
        request.setServletPath("/ingest/dev001/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean reachedDownstream = new AtomicBoolean(false);
        FilterChain terminal = (req, res) -> reachedDownstream.set(true);

        resolveFilterChainProxy().doFilter(request, response, terminal);

        assertThat(response.getStatus())
                .as("/ingest/** 应匿名放行（鉴权交给 DeviceCredentialAuthFilter）")
                .isNotIn(401, 403);
        assertThat(reachedDownstream)
                .as("/ingest/** 放行后应继续向下游过滤链传递")
                .isTrue();
    }

    /** 从上下文的 Filter Bean 中定位 Spring Security 的 {@code springSecurityFilterChain}。 */
    private FilterChainProxy resolveFilterChainProxy() {
        return applicationContext.getBeansOfType(Filter.class).values().stream()
                .filter(FilterChainProxy.class::isInstance)
                .map(FilterChainProxy.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 FilterChainProxy（springSecurityFilterChain）"));
    }

    private int indexOf(List<Filter> filters, Class<? extends Filter> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
