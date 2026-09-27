package com.mqtt.cloud.filter;

import com.mqtt.cloud.config.AccessControlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InternalTokenFilter} 单元测试。
 * <p>
 * 用于替代计划中的运行时验证：直接以 Mock 请求验证
 * <ul>
 *     <li>{@code shouldNotFilter} 基于不含 context-path 的 servletPath 判定；</li>
 *     <li>缺失/错误令牌时返回 401 与 {@code {"result":"deny"}}，且不继续过滤链；</li>
 *     <li>令牌正确时放行。</li>
 * </ul>
 */
class InternalTokenFilterTest {

    private static final String HEADER = "X-Internal-Token";

    private AccessControlProperties properties;
    private InternalTokenFilter filter;

    @BeforeEach
    void setUp() {
        properties = new AccessControlProperties();
        properties.setInternalToken("t");
        filter = new InternalTokenFilter(properties);
    }

    private MockHttpServletRequest request(String servletPath, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", servletPath);
        request.setServletPath(servletPath);
        if (token != null) {
            request.addHeader(HEADER, token);
        }
        return request;
    }

    @Test
    void shouldNotFilter_only_for_internal_paths() throws Exception {
        assertThat(filter.shouldNotFilter(request("/internal/emqx/auth", null))).isFalse();
        assertThat(filter.shouldNotFilter(request("/devices", null))).isTrue();
    }

    @Test
    void doFilter_missing_token_returns_deny_and_stops_chain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/internal/emqx/auth", null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"result\":\"deny\"}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_wrong_token_returns_deny_and_stops_chain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/internal/emqx/auth", "wrong"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"result\":\"deny\"}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_correct_token_passes_through() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/internal/emqx/auth", "t"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}