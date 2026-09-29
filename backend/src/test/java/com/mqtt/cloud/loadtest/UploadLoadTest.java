package com.mqtt.cloud.loadtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上行链路压测工具（默认不执行，surefire 已排除 loadtest 标签）。
 * <p>
 * 运行方式（surefire 默认排除 loadtest 组，需显式关闭排除）：
 * <pre>
 * mvn -B test -Dtest=UploadLoadTest -Dloadtest.excludedGroups=none \
 *     -Dloadtest.brokerUrl=tcp://localhost:1883 \
 *     -Dloadtest.devices=100 -Dloadtest.messagesPerDevice=100
 * </pre>
 * 压测走真实设备一机一密凭据（平台账号无 device/{key}/data 发布权限）。
 * <p>
 * {@code loadtest.baseUrl} 默认指向 nginx 网关（`http://localhost`，由 {@code FRONTEND_PORT} 决定）：
 * 后端不发布宿主机端口（多副本部署，见 R2-4），REST 调用统一经网关。
 * <p>
 * 注意 {@code loadtest.brokerUrl} 必须指向 EMQX 实际映射端口（默认 1883，由 .env 的
 * {@code MQTT_PORT} 决定）：若宿主机另有 Broker 占用该端口，用默认值会把消息发进错误的
 * Broker，表现为"发布全部成功、后端零接收"。
 * <p>
 * 批量应小于 EMQX 订阅端 {@code mqueue} 上限（默认 1000），否则超出部分会在 Broker 侧被静默丢弃。
 * <p>
 * 覆盖范围取决于 EMQX 是否已下发 HTTP 认证源：已下发时同时压测认证回调，未下发（如
 * {@code emqx-init} 未执行）则认证链路不被触发。
 */
@Tag("loadtest")
class UploadLoadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRODUCT_KEY = "loadtest";

    private final String baseUrl = System.getProperty("loadtest.baseUrl", "http://localhost");
    private final String brokerUrl = System.getProperty("loadtest.brokerUrl", "tcp://localhost:1883");
    private final String adminUser = System.getProperty("loadtest.adminUser", "admin");
    private final String adminPassword = System.getProperty("loadtest.adminPassword", "admin123");
    private final int deviceCount = Integer.getInteger("loadtest.devices", 100);
    private final int messagesPerDevice = Integer.getInteger("loadtest.messagesPerDevice", 100);
    private final int publishers = Integer.getInteger("loadtest.publishers", 16);
    private final int payloadBytes = Integer.getInteger("loadtest.payloadBytes", 256);
    /** 发布结束后等待后端排空消息再清理设备；否则设备先被逻辑删除，在途消息会因“未注册设备”被丢弃 */
    private final int drainWaitMs = Integer.getInteger("loadtest.drainWaitMs", 30000);

    private final RestTemplate rest = new RestTemplate();

    @Test
    void runUploadLoadTest() throws Exception {
        String token = login();
        long productId = ensureProduct(token);
        List<CreatedDevice> devices = createDevices(token, productId);

        byte[] payload = buildPayload();
        AtomicInteger published = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicLong windowStart = new AtomicLong(Long.MAX_VALUE);
        AtomicLong windowEnd = new AtomicLong(0L);
        ConcurrentLinkedQueue<Long> publishNanos = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> errorSamples = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(publishers);
        CountDownLatch latch = new CountDownLatch(devices.size());
        for (CreatedDevice device : devices) {
            pool.submit(() -> {
                try {
                    publishFromDevice(device, payload, published, windowStart, windowEnd, publishNanos);
                } catch (Exception e) {
                    failures.incrementAndGet();
                    if (errorSamples.size() < 5) {
                        errorSamples.add(device.deviceKey() + ": " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean finished = latch.await(30, TimeUnit.MINUTES);
        pool.shutdownNow();

        long start = windowStart.get();
        long end = windowEnd.get();
        double elapsedSec = (end > start && start != Long.MAX_VALUE) ? (end - start) / 1_000_000_000.0 : 0.0;
        int total = published.get();

        List<Long> latencies = new ArrayList<>(publishNanos);
        Collections.sort(latencies);
        double p99Ms = latencies.isEmpty() ? 0.0 : percentile(latencies, 0.99) / 1_000_000.0;
        double avgMs = latencies.isEmpty() ? 0.0
                : latencies.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0;

        System.out.printf("等待后端排空在途消息 %d ms 后再清理设备...%n", drainWaitMs);
        Thread.sleep(drainWaitMs);

        cleanup(token, devices);

        System.out.println();
        System.out.println("==================== 上行压测结果 ====================");
        System.out.printf("目标地址          : %s (broker %s)%n", baseUrl, brokerUrl);
        System.out.printf("设备数            : %d%n", devices.size());
        System.out.printf("每设备消息数      : %d%n", messagesPerDevice);
        System.out.printf("并发发布线程数    : %d%n", publishers);
        System.out.printf("载荷大小          : %d 字节%n", payloadBytes);
        System.out.printf("发布总数          : %d%n", total);
        System.out.printf("失败数            : %d%n", failures.get());
        System.out.printf("发布窗口耗时      : %.3f s%n", elapsedSec);
        System.out.printf("最大稳定吞吐      : %.1f msg/s%n", elapsedSec > 0 ? total / elapsedSec : 0.0);
        System.out.printf("发布侧平均延迟    : %.2f ms%n", avgMs);
        System.out.printf("发布侧 P99 延迟   : %.2f ms%n", p99Ms);
        if (!errorSamples.isEmpty()) {
            System.out.println("失败样例          : " + errorSamples);
        }
        System.out.println("======================================================");

        assertTrue(finished, "压测未在超时时间内完成");
        assertTrue(total > 0, "未成功发布任何消息");
    }

    private String login() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", adminUser);
        body.put("password", adminPassword);
        ResponseEntity<String> resp = rest.postForEntity(
                baseUrl + "/api/auth/login", jsonEntity(body), String.class);
        String token = MAPPER.readTree(resp.getBody()).path("data").path("token").asText();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("登录失败，未取得 token：" + resp.getBody());
        }
        return token;
    }

    private long ensureProduct(String token) throws Exception {
        ResponseEntity<String> list = rest.exchange(
                baseUrl + "/api/products", HttpMethod.GET, authEntity(token), String.class);
        for (JsonNode node : MAPPER.readTree(list.getBody()).path("data")) {
            if (PRODUCT_KEY.equals(node.path("productKey").asText())) {
                return node.path("id").asLong();
            }
        }
        Map<String, String> body = new HashMap<>();
        body.put("productKey", PRODUCT_KEY);
        body.put("productName", "Load Test Product");
        body.put("description", "压测专用产品，由 UploadLoadTest 自动创建");
        ResponseEntity<String> created = rest.postForEntity(
                baseUrl + "/api/products", authEntity(token, body), String.class);
        return MAPPER.readTree(created.getBody()).path("data").path("id").asLong();
    }

    private List<CreatedDevice> createDevices(String token, long productId) throws Exception {
        String runId = Long.toString(System.currentTimeMillis(), 36);
        List<CreatedDevice> devices = new ArrayList<>(deviceCount);
        for (int i = 0; i < deviceCount; i++) {
            String deviceKey = "lt-" + runId + "-" + i;
            Map<String, Object> body = new HashMap<>();
            body.put("productId", productId);
            body.put("deviceName", "loadtest-" + runId + "-" + i);
            body.put("deviceKey", deviceKey);
            body.put("deviceType", "LOADTEST");
            body.put("topic", "device/" + deviceKey + "/data");
            body.put("description", "压测设备");
            ResponseEntity<String> resp = rest.postForEntity(
                    baseUrl + "/api/devices", authEntity(token, body), String.class);
            JsonNode data = MAPPER.readTree(resp.getBody()).path("data");
            devices.add(new CreatedDevice(
                    data.path("id").asLong(),
                    data.path("deviceKey").asText(),
                    data.path("username").asText(),
                    data.path("deviceSecret").asText()));
        }
        return devices;
    }

    private void publishFromDevice(CreatedDevice device, byte[] payload,
                                   AtomicInteger published, AtomicLong windowStart,
                                   AtomicLong windowEnd, ConcurrentLinkedQueue<Long> publishNanos)
            throws Exception {
        String clientId = "lt" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(device.username());
        options.setPassword(device.deviceSecret().toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        try {
            client.connect(options);
            String topic = "device/" + device.deviceKey() + "/data";
            windowStart.accumulateAndGet(System.nanoTime(), Math::min);
            for (int i = 0; i < messagesPerDevice; i++) {
                MqttMessage message = new MqttMessage(payload);
                message.setQos(1);
                long t0 = System.nanoTime();
                client.publish(topic, message);
                publishNanos.add(System.nanoTime() - t0);
                published.incrementAndGet();
            }
            windowEnd.accumulateAndGet(System.nanoTime(), Math::max);
        } finally {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }

    private void cleanup(String token, List<CreatedDevice> devices) {
        int removed = 0;
        for (CreatedDevice device : devices) {
            try {
                rest.exchange(baseUrl + "/api/devices/" + device.id(), HttpMethod.DELETE,
                        authEntity(token), String.class);
                removed++;
            } catch (Exception ignored) {
                // 清理失败不影响压测结论
            }
        }
        System.out.printf("已清理压测设备    : %d/%d%n", removed, devices.size());
    }

    private byte[] buildPayload() {
        Random random = new Random(42L);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        // 真实设备按产品 payload_format=JSON 上报，且 history_record.payload 为 JSON 列：
        // 载荷必须是合法 JSON，否则历史留存会以 Invalid JSON text 失败
        String prefix = "{\"data\":\"";
        String suffix = "\"}";
        int dataLength = Math.max(1, payloadBytes - prefix.length() - suffix.length());
        StringBuilder sb = new StringBuilder(payloadBytes);
        sb.append(prefix);
        for (int i = 0; i < dataLength; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        sb.append(suffix);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static long percentile(List<Long> sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.size()) - 1;
        return sortedNanos.get(Math.max(0, Math.min(index, sortedNanos.size() - 1)));
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

    private record CreatedDevice(long id, String deviceKey, String username, String deviceSecret) {
    }
}