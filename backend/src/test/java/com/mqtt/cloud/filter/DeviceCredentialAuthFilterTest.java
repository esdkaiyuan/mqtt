package com.mqtt.cloud.filter;

import com.mqtt.cloud.config.HttpIngestProperties;
import com.mqtt.cloud.service.DeviceAccessGuard;
import com.mqtt.cloud.service.DeviceAuthCacheService;
import com.mqtt.cloud.service.DeviceSecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DeviceCredentialAuthFilter} 单元测试（T-24 实施计划 P2）。
 * <p>
 * 覆盖三类关注点：
 * <ul>
 *     <li>{@code shouldNotFilter}：仅拦截 {@code /ingest/**}，并能剥离 context-path；开关关闭时整链放行；</li>
 *     <li>失败路径：无头 / 非 Basic / base64 非法 / 用户名无点 / 路径与凭据不一致 / 设备不存在或被禁 / 密码错
 *         —— 六类统一 {@code 401} + {@code 6246} + {@code WWW-Authenticate}，且不继续过滤链；</li>
 *     <li>成功路径：写入 {@code AUTHENTICATED_DEVICE_KEY} 并放行。</li>
 * </ul>
 */
class DeviceCredentialAuthFilterTest {

    private DeviceSecretService deviceSecretService;
    private DeviceAccessGuard deviceAccessGuard;
    private HttpIngestProperties properties;
    private ObjectMapper objectMapper;
    private DeviceCredentialAuthFilter filter;

    @BeforeEach
    void setUp() {
        deviceSecretService = mock(DeviceSecretService.class);
        deviceAccessGuard = mock(DeviceAccessGuard.class);
        properties = new HttpIngestProperties();
        objectMapper = new ObjectMapper();
        filter = new DeviceCredentialAuthFilter(deviceSecretService, deviceAccessGuard, properties, objectMapper);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }

    private MockHttpServletRequest basicRequest(String path, String username, String secret) {
        MockHttpServletRequest request = request(path);
        String token = Base64.getEncoder()
                .encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + token);
        return request;
    }

    private void assertRejected(MockHttpServletResponse response, MockFilterChain chain) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Basic realm=\"mqtt-cloud-device\"");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(6246);
        assertThat(chain.getRequest()).isNull();
    }

    // ── shouldNotFilter ──────────────────────────────────────────────────

    @Test
    void shouldNotFilter_only_for_ingest_paths() {
        assertThat(filter.shouldNotFilter(request("/ingest/dev001/data"))).isFalse();
        assertThat(filter.shouldNotFilter(request("/devices/1"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/external/v1/x"))).isTrue();
    }

    @Test
    void shouldNotFilter_matches_when_context_path_present() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest/dev001/data");
        request.setContextPath("/api");
        request.setRequestURI("/api/ingest/dev001/data");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_all_when_disabled() {
        properties.setEnabled(false);

        assertThat(filter.shouldNotFilter(request("/ingest/dev001/data"))).isTrue();
    }

    // ── 失败路径（六类统一 401 + 6246）────────────────────────────────────

    @Test
    void doFilter_missing_authorization_is_rejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/ingest/dev001/data"), response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_non_basic_scheme_is_rejected() throws Exception {
        MockHttpServletRequest request = request("/ingest/dev001/data");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_invalid_base64_is_rejected() throws Exception {
        MockHttpServletRequest request = request("/ingest/dev001/data");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic @@not-base64@@");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_username_without_dot_is_rejected() throws Exception {
        when(deviceSecretService.parseUsername("PLATFORM")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(basicRequest("/ingest/dev001/data", "PLATFORM", "s3cr3t"), response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_path_device_key_mismatch_is_rejected() throws Exception {
        when(deviceSecretService.parseUsername("pk1.dev999")).thenReturn(new String[]{"pk1", "dev999"});
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(basicRequest("/ingest/dev001/data", "pk1.dev999", "s3cr3t"), response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_unknown_device_is_rejected() throws Exception {
        when(deviceSecretService.parseUsername("pk1.dev001")).thenReturn(new String[]{"pk1", "dev001"});
        when(deviceAccessGuard.resolve("pk1", "dev001")).thenReturn(null);
        when(deviceAccessGuard.isPermitted(null)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(basicRequest("/ingest/dev001/data", "pk1.dev001", "s3cr3t"), response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_not_permitted_device_is_rejected() throws Exception {
        DeviceAuthCacheService.AuthMeta meta = new DeviceAuthCacheService.AuthMeta(1L, "DISABLED", 1, "$2a$hash");
        when(deviceSecretService.parseUsername("pk1.dev001")).thenReturn(new String[]{"pk1", "dev001"});
        when(deviceAccessGuard.resolve("pk1", "dev001")).thenReturn(meta);
        when(deviceAccessGuard.isPermitted(meta)).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(basicRequest("/ingest/dev001/data", "pk1.dev001", "s3cr3t"), response, chain);

        assertRejected(response, chain);
    }

    @Test
    void doFilter_wrong_secret_is_rejected() throws Exception {
        DeviceAuthCacheService.AuthMeta meta = new DeviceAuthCacheService.AuthMeta(1L, "ENABLED", 1, "$2a$hash");
        when(deviceSecretService.parseUsername("pk1.dev001")).thenReturn(new String[]{"pk1", "dev001"});
        when(deviceAccessGuard.resolve("pk1", "dev001")).thenReturn(meta);
        when(deviceAccessGuard.isPermitted(meta)).thenReturn(true);
        when(deviceSecretService.matches("wrong", "$2a$hash")).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(basicRequest("/ingest/dev001/data", "pk1.dev001", "wrong"), response, chain);

        assertRejected(response, chain);
    }

    // ── 成功路径 ─────────────────────────────────────────────────────────

    @Test
    void doFilter_valid_credential_sets_device_key_and_continues() throws Exception {
        DeviceAuthCacheService.AuthMeta meta = new DeviceAuthCacheService.AuthMeta(1L, "ENABLED", 1, "$2a$hash");
        when(deviceSecretService.parseUsername("pk1.dev001")).thenReturn(new String[]{"pk1", "dev001"});
        when(deviceAccessGuard.resolve("pk1", "dev001")).thenReturn(meta);
        when(deviceAccessGuard.isPermitted(meta)).thenReturn(true);
        when(deviceSecretService.matches("s3cr3t", "$2a$hash")).thenReturn(true);
        MockHttpServletRequest request = basicRequest("/ingest/dev001/data", "pk1.dev001", "s3cr3t");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(DeviceCredentialAuthFilter.AUTHENTICATED_DEVICE_KEY)).isEqualTo("dev001");
        assertThat(chain.getRequest()).isNotNull();
    }
}
