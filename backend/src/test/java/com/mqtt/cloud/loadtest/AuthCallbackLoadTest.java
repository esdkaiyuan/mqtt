package com.mqtt.cloud.loadtest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证回调压测工具（默认不执行，surefire 已排除 loadtest 标签）。
 * <p>
 * 用途：为 R3 验收「认证回调 P99 较 R0 基线下降 ≥ 50%、DB 查询次数 2 → 0~1」提供可复现的测量。
 * 直连内部认证回调 {@code /api/internal/emqx/auth}，不经 EMQX 与 MQTT 握手，把被测对象收敛到
 * 「后端处理一次认证回调」本身。
 * <p>
 * 后端不发布宿主机端口（R2-4 多副本形态），且 nginx 对 {@code /api/internal/} 直接 403，
 * 故需用 compose override 临时把 backend 的 8080 映射到宿主机调试端口（用完即撤）。
 * <p>
 * 运行方式（surefire 默认排除 loadtest 组，需显式关闭排除）：
 * <pre>
 * mvn -B test -Dtest=AuthCallbackLoadTest -Dloadtest.excludedGroups=none \
 *     -Dauthload.url=http://127.0.0.1:18080/api/internal/emqx/auth \
 *     -Dauthload.baseUrl=http://127.0.0.1/api \
 *     -Dauthload.internalToken=$env:INTERNAL_TOKEN \
 *     -Dauthload.requests=2000 -Dauthload.threads=8
 * </pre>
 * 对比口径：把后端 {@code ACCESS_CONTROL_CACHE_TTL_SECONDS} 置 {@code 0}（关闭缓存）重建后再跑一次，
 * 即为「R0 等价」基线 —— 此时每次认证回源查库 2 次（产品 + 设备）。缓存开启时稳态为 0 次。
 * <p>
 * DB 查询次数不在本工具内统计（避免把计数逻辑混进被测链路）：跑前跑后取 MySQL
 * {@code SHOW GLOBAL STATUS LIKE 'Com_select'} 差值再除以请求数即可。
 * <p>
 * 需 {@code ACCESS_CONTROL_ENFORCE_AUTH=true}：置 false 时认证回调直接放行、不触及缓存与 DB，
 * 测不出差异。设备凭据由本工具经网关现场创建（一机一密），不复用任何固定凭据。
 */
@Tag("loadtest")
class AuthCallbackLoadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String authUrl = System.getProperty("authload.url",
            "http://127.0.0.1:18080/api/internal/emqx/auth");
    private final String baseUrl = System.getProperty("authload.baseUrl", "http://127.0.0.1/api");
    private final String internalToken = resolveInternalToken();
    private final String adminUser = System.getProperty("authload.adminUser", "admin");
    private final String adminPassword = System.getProperty("authload.adminPassword", "admin123");
    private final int requests = Integer.getInteger("authload.requests", 2000);
    private final int threads = Integer.getInteger("authload.threads", 8);
    /** 预热请求数：填充后端认证缓存，使正式测量处于稳态（缓存关闭时该步骤无副作用） */
    private final int warmup = Integer.getInteger("authload.warmup", 50);

    private final RestTemplate rest = new RestTemplate();
    private final String productKey = "authload-" + Long.toString(System.currentTimeMillis(), 36);
    private final String deviceKey = "d-" + Long.toString(System.currentTimeMillis(), 36);

    private String token;
    private long productId;
    private long deviceId;
    private String deviceUsername;
    private String deviceSecret;

    @Test
    void runAuthCallbackLoadTest() throws Exception {
        token = login();
        createProductAndDevice();

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        warmUp(client);

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger allow = new AtomicInteger();
        AtomicInteger deny = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        ConcurrentLinkedQueue<String> errorSamples = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);
        long wallStart = System.nanoTime();
        for (int i = 0; i < requests; i++) {
            final int seq = i;
            pool.submit(() -> {
                long start = System.nanoTime();
                try {
                    String body = authBody("authload-" + seq);
                    HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl))
                            .header("Content-Type", "application/json")
                            .header("X-Internal-Token", internalToken)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    latencies.add(System.nanoTime() - start);
                    if (response.statusCode() != 200) {
                        errors.incrementAndGet();
                        if (errorSamples.size() < 5) {
                            errorSamples.add("HTTP " + response.statusCode() + ": " + response.body());
                        }
                    } else if (response.body().contains("allow")) {
                        allow.incrementAndGet();
                    } else {
                        deny.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    if (errorSamples.size() < 5) {
                        errorSamples.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean finished = latch.await(10, TimeUnit.MINUTES);
        pool.shutdownNow();
        double wallSeconds = (System.nanoTime() - wallStart) / 1_000_000_000.0;

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        System.out.println();
        System.out.println("==================== 认证回调压测结果 ====================");
        System.out.printf("目标地址    : %s%n", authUrl);
        System.out.printf("并发 / 请求 : %d / %d（预热 %d）%n", threads, requests, warmup);
        System.out.printf("耗时 / QPS  : %.2f s / %.0f%n", wallSeconds, requests / Math.max(wallSeconds, 0.001));
        System.out.printf("allow/deny  : %d / %d%n", allow.get(), deny.get());
        System.out.printf("P50 / P95   : %.2f / %.2f ms%n", ms(sorted, 0.50), ms(sorted, 0.95));
        System.out.printf("P99 / max   : %.2f / %.2f ms%n", ms(sorted, 0.99), ms(sorted, 1.0));
        System.out.printf("平均        : %.2f ms%n", sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0);
        System.out.printf("错误        : %d%n", errors.get());
        errorSamples.forEach(s -> System.out.println("  ! " + s));
        System.out.printf("AUTHLOAD requests=%d threads=%d p50=%.2fms p95=%.2fms p99=%.2fms avg=%.2fms max=%.2fms errors=%d%n",
                requests, threads, ms(sorted, 0.50), ms(sorted, 0.95), ms(sorted, 0.99),
                sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0,
                ms(sorted, 1.0), errors.get());
        System.out.println("=========================================================");

        cleanup();

        assertTrue(finished, "压测未在预期时间内完成");
        assertEquals(0, errors.get(), "认证回调出现非 200 响应或异常");
        assertEquals(requests, allow.get(), "有效设备凭据的认证应全部 allow");
    }

    /** 预热：让后端缓存处于命中稳态；缓存关闭（TTL=0）时同样无害 */
    private void warmUp(HttpClient client) throws Exception {
        for (int i = 0; i < warmup; i++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(authUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(authBody("warmup-" + i), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        System.out.printf("[0] 预热 %d 次完成（productKey=%s deviceKey=%s）%n", warmup, productKey, deviceKey);
    }

    private String authBody(String clientId) {
        Map<String, String> body = new HashMap<>();
        body.put("username", deviceUsername);
        body.put("password", deviceSecret);
        body.put("clientid", clientId);
        return MAPPER.writeValueAsString(body);
    }

    private static double ms(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(p * sortedNanos.size()) - 1;
        return sortedNanos.get(Math.max(0, Math.min(index, sortedNanos.size() - 1))) / 1_000_000.0;
    }

    private String login() throws Exception {
        Map<String, String> body = Map.of("username", adminUser, "password", adminPassword);
        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/auth/login", jsonEntity(body), String.class);
        String t = MAPPER.readTree(resp.getBody()).path("data").path("token").asText();
        assertTrue(!t.isBlank(), "登录失败：" + resp.getBody());
        return t;
    }

    private void createProductAndDevice() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productKey", productKey);
        body.put("productName", "Auth Load " + productKey);
        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/products", authEntity(token, body), String.class);
        JsonNode data = MAPPER.readTree(resp.getBody()).path("data");
        productId = data.path("id").asLong();
        assertTrue(productId > 0, "创建产品失败：" + resp.getBody());

        body.clear();
        body.put("productId", productId);
        body.put("deviceKey", deviceKey);
        body.put("deviceName", "auth-load-" + deviceKey);
        body.put("deviceType", "AUTHLOAD");
        body.put("topic", "device/" + deviceKey + "/data");
        resp = rest.postForEntity(baseUrl + "/devices", authEntity(token, body), String.class);
        data = MAPPER.readTree(resp.getBody()).path("data");
        deviceId = data.path("id").asLong();
        deviceUsername = data.path("username").asText();
        deviceSecret = data.path("deviceSecret").asText();
        assertTrue(deviceId > 0, "创建设备失败：" + resp.getBody());
        assertTrue(deviceSecret != null && !deviceSecret.isBlank(), "未返回一次性明文密钥");
    }

    private void cleanup() {
        rest.exchange(baseUrl + "/devices/" + deviceId, HttpMethod.DELETE, authEntity(token), String.class);
        rest.exchange(baseUrl + "/products/" + productId, HttpMethod.DELETE, authEntity(token), String.class);
        System.out.printf("[9] 已清理测试产品与设备（productId=%d deviceId=%d）%n", productId, deviceId);
    }

    private static String resolveInternalToken() {
        String value = System.getProperty("authload.internalToken");
        if (value == null || value.isBlank()) {
            value = System.getenv("INTERNAL_TOKEN");
        }
        return value == null ? "" : value;
    }

    private HttpEntity<Void> authEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, ?>> authEntity(String token, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}