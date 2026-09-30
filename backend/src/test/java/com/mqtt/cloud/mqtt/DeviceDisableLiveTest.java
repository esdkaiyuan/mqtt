package com.mqtt.cloud.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 现场复核「设备禁用 / 产品停用与凭据轮换的生效时机」。
 * <p>
 * 前提（需先就绪）：
 * <ul>
 *   <li>本机栈已通过 docker compose 启动，EMQX 端口 1883、nginx 网关映射默认 80</li>
 *   <li>emqx-init 已下发 HTTP 认证器与授权源（后台脚本会校验）</li>
 *   <li>ACCESS_CONTROL_ENFORCE_AUTH=true 已生效（默认 false 时认证恒放行，无法验证）</li>
 * </ul>
 * 该测试按 {@code @Tag("loadtest")} 分组，surefire 默认排除；按需执行：
 * <pre>
 * mvn -B test -Dtest=DeviceDisableLiveTest -Dloadtest.excludedGroups=none \
 *     -Dlive.baseUrl=http://127.0.0.1/api -Dlive.brokerUrl=tcp://127.0.0.1:1883 \
 *     -Dlive.adminUser=admin -Dlive.adminPassword=admin123 \
 *     -Dlive.aclCacheTtlSeconds=10 \
 *     -Dlive.platformSecret=$env:PLATFORM_SECRET
 * </pre>
 * 平台密钥取自 {@code -Dlive.platformSecret} 或环境变量 {@code PLATFORM_SECRET}（与 {@code .env} 同源），
 * 不在代码中写死；缺失时仅第 8 步（平台账号不受产品停用影响）失败。
 * {@code live.aclCacheTtlSeconds} 需与 {@code emqx-init} 下发的 {@code ACL_CACHE_TTL} 对齐（默认 10）。
 */
@Tag("loadtest")
class DeviceDisableLiveTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl = System.getProperty("live.baseUrl", "http://127.0.0.1/api");
    private final String brokerUrl = System.getProperty("live.brokerUrl", "tcp://127.0.0.1:1883");
    private final String adminUser = System.getProperty("live.adminUser", "admin");
    private final String adminPassword = System.getProperty("live.adminPassword", "admin123");
    private final String platformUsername = "PLATFORM";
    // 平台密钥不写死：优先取 -Dlive.platformSecret，其次取环境变量 PLATFORM_SECRET（与 .env 同源），
    // 避免真实凭据进入版本库。缺失时仅第 8 步（平台账号不受产品停用影响）会失败。
    private final String platformSecret = resolvePlatformSecret();

    // EMQX 授权结果缓存 TTL（与 emqx-init 的 ACL_CACHE_TTL 对齐）。禁用后需等该缓存过期，
    // ACL 的 deny 才会对已连接会话生效；测试等待 TTL + 余量后再断言。
    private final long aclCacheTtlSeconds = Long.getLong("live.aclCacheTtlSeconds", 10L);

    private final RestTemplate rest = new RestTemplate();
    private final String productKey = "live-" + Long.toString(System.currentTimeMillis(), 36);
    private final String deviceKey = "d-" + Long.toString(System.currentTimeMillis(), 36);

    private String token;
    private long productId;
    private long deviceId;
    private String deviceUsername;
    private String deviceSecret;

    @Test
    void verifyDisableEnableTiming() throws Exception {
        token = login();
        createProductAndDevice();

        // 1) 禁用前：基线连接必须成功
        MqttClient client = connect(deviceUsername, deviceSecret);
        System.out.printf("[1] 禁用前连接成功 clientId=%s%n", client.getClientId());

        // 2) 禁用设备后：已建立连接必须保持存活（EMQX 不主动踢下线）
        postDisable("/devices/" + deviceId + "/disable");
        Thread.sleep(1500);
        assertTrue(client.isConnected(), "禁用后已连接设备应保持连接");
        System.out.println("[2] 禁用设备后，已建立连接仍保持（不被踢下线）");

        // 3) 禁用后已连接会话 publish：客户端不报错（QoS1 仍会收到 PUBACK），
        //    是否真正投递由 Broker 授权决定 —— 禁用后 ACL 回调返回 deny，消息被丢弃（见 ACL 复核）
        String topic = "device/" + deviceKey + "/data";
        MqttMessage message = new MqttMessage("{\"t\":1}".getBytes());
        message.setQos(1);
        client.publish(topic, message);
        System.out.println("[3] 禁用后已连接会话 publish 不报错（投递与否由 ACL 裁决，见 ACL 复核）");

        // 4) 禁用后：全新连接（重连）必须被拒绝（认证回调返回 deny）
        client.disconnect();
        assertRejectedByAuth("禁用后重连", deviceUsername, deviceSecret);
        System.out.println("[4] 禁用后重连被拒（认证失败）");

        // 5) 启用设备后：重新连接必须恢复成功
        postDisable("/devices/" + deviceId + "/enable");
        Thread.sleep(1500);
        MqttClient reconnected = connect(deviceUsername, deviceSecret);
        assertTrue(reconnected.isConnected());
        System.out.println("[5] 启用后重连恢复成功");
        reconnected.disconnect();

        // 6) 产品维度对称验证：停用产品后重连被拒，启用后恢复
        postDisable("/products/" + productId + "/disable");
        Thread.sleep(1500);
        assertRejectedByAuth("停用产品后重连", deviceUsername, deviceSecret);
        System.out.println("[6] 停用产品后重连被拒");

        postDisable("/products/" + productId + "/enable");
        Thread.sleep(1500);
        MqttClient again = connect(deviceUsername, deviceSecret);
        assertTrue(again.isConnected());
        System.out.println("[7] 启用产品后重连恢复成功");
        again.disconnect();

        // 8) 平台账号（PLATFORM）不受产品停用影响
        postDisable("/products/" + productId + "/disable");
        Thread.sleep(1500);
        MqttClient platform = connect(platformUsername, platformSecret);
        assertTrue(platform.isConnected(), "平台账号认证不受产品停用影响");
        System.out.println("[8] 产品停用后 PLATFORM 账号仍可连接");
        platform.disconnect();
        postDisable("/products/" + productId + "/enable");

        cleanup();
    }

    /**
     * 现场复核 ACL 缺口收敛：禁用后**已连接会话**的上行发布必须被授权回调拒绝。
     * <p>
     * 用 {@code PLATFORM} 账号另开一个订阅端监听 {@code device/+/data}，以「消息是否真正投递」为判据
     * —— 客户端侧 QoS1 发布即使被 Broker 丢弃也仍会收到 PUBACK，故不能只看发布是否报错。
     * 时序：启用时基线投递 → 禁用 → 等 EMQX 授权缓存过期（{@code ACL_CACHE_TTL}）→ 发布应被丢弃
     * → 启用 → 等缓存过期 → 投递恢复。
     */
    @Test
    void verifyAclDeniedForExistingSessionAfterDisable() throws Exception {
        token = login();
        createProductAndDevice();

        String topic = "device/" + deviceKey + "/data";
        AtomicInteger received = new AtomicInteger();

        MqttClient observer = connect(platformUsername, platformSecret);
        observer.subscribe("device/+/data", 1, (t, msg) -> {
            if (topic.equals(t)) {
                received.incrementAndGet();
            }
        });

        MqttClient device = connect(deviceUsername, deviceSecret);

        // 基线：启用状态下发布应被投递（否则无法区分「禁用生效」与「链路本就不通」）
        device.publish(topic, qos1("{\"n\":1}"));
        awaitTrue(() -> received.get() >= 1, 5000, "启用状态下发布应被投递");
        System.out.println("[ACL-1] 启用状态下发布已投递（基线）");

        // 禁用设备：已建立连接保持存活（EMQX 不主动踢下线）
        postDisable("/devices/" + deviceId + "/disable");
        Thread.sleep(1500);
        assertTrue(device.isConnected(), "禁用后已连接会话应保持连接");

        // 等 EMQX 授权结果缓存过期，deny 才会对已连接会话生效
        long waitMs = (aclCacheTtlSeconds + 3) * 1000L;
        System.out.printf("[ACL-2] 等待 EMQX 授权缓存过期（%ds）...%n", aclCacheTtlSeconds + 3);
        Thread.sleep(waitMs);

        int before = received.get();
        device.publish(topic, qos1("{\"n\":2}"));
        Thread.sleep(3000);
        assertEquals(before, received.get(),
                "禁用后已连接会话的发布应被 ACL 拒绝（消息被丢弃），实际仍被投递");
        assertTrue(device.isConnected(), "ACL 拒绝发布不应断开连接（deny_action=ignore）");
        System.out.println("[ACL-3] 禁用后已连接会话发布被丢弃（ACL deny 生效）");

        // 启用设备：等缓存过期后投递应恢复
        postDisable("/devices/" + deviceId + "/enable");
        Thread.sleep(waitMs);
        device.publish(topic, qos1("{\"n\":3}"));
        awaitTrue(() -> received.get() > before, 5000, "启用后发布应恢复投递");
        System.out.println("[ACL-4] 启用后发布投递恢复");

        device.disconnect();
        observer.disconnect();
        cleanup();
    }

    /**
     * 现场复核凭据轮换的生效时机：密钥重置后旧凭据**立即失效**、新凭据可用。
     * <p>
     * 验收门槛为「60 秒内旧凭据失效」；后端在轮换事务内主动删除认证缓存键，且 EMQX 5.0 的
     * HTTP 认证器无结果缓存，故实测为**即时**（无需等待 TTL）。
     */
    @Test
    void verifyOldSecretRejectedImmediatelyAfterReset() throws Exception {
        token = login();
        createProductAndDevice();

        // 轮换前：旧凭据可连接
        MqttClient before = connect(deviceUsername, deviceSecret);
        assertTrue(before.isConnected(), "轮换前旧凭据应可连接");
        before.disconnect();
        System.out.println("[RESET-1] 轮换前旧凭据可连接");

        // 重置密钥：一次性返回新明文
        ResponseEntity<String> resp = rest.exchange(
                baseUrl + "/devices/" + deviceId + "/reset-secret", HttpMethod.POST,
                authEntity(token), String.class);
        assertEquals(200, resp.getStatusCode().value(), "重置密钥失败：" + resp.getBody());
        String newSecret = MAPPER.readTree(resp.getBody()).path("data").asText();
        assertFalse(newSecret.isBlank(), "未返回新明文密钥");
        assertNotEquals(deviceSecret, newSecret, "新密钥不应与旧密钥相同");

        // 旧凭据立即被拒（不等 TTL）
        assertRejectedByAuth("轮换后旧凭据", deviceUsername, deviceSecret);
        System.out.println("[RESET-2] 旧凭据立即被拒");

        // 新凭据可用
        MqttClient after = connect(deviceUsername, newSecret);
        assertTrue(after.isConnected(), "轮换后新凭据应可连接");
        after.disconnect();
        System.out.println("[RESET-3] 新凭据可连接");

        cleanup();
    }

    private MqttMessage qos1(String payload) {
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        return message;
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    /**
     * 断言连接被认证拒绝。EMQX 对 HTTP 认证 deny 的 CONNACK 返回码可能是 4（用户名或密码错误）
     * 或 5（未授权），故只要求落在认证类失败码内，避免绑定具体实现细节。
     */
    private void assertRejectedByAuth(String scene, String username, String password) {
        MqttSecurityException e = assertThrows(MqttSecurityException.class,
                () -> connect(username, password), scene + "应被认证拒绝");
        int rc = e.getReasonCode();
        assertTrue(rc == 4 || rc == 5, scene + "应因认证失败被拒，实际 reasonCode=" + rc);
        System.out.printf("    %s reasonCode=%d%n", scene, rc);
    }

    private String login() throws Exception {
        Map<String, String> body = Map.of("username", adminUser, "password", adminPassword);
        ResponseEntity<String> resp = rest.postForEntity(
                baseUrl + "/auth/login", jsonEntity(body), String.class);
        String t = MAPPER.readTree(resp.getBody()).path("data").path("token").asText();
        assertFalse(t.isBlank(), "登录失败：" + resp.getBody());
        return t;
    }

    private void createProductAndDevice() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("productKey", productKey);
        body.put("productName", "Live Verify " + productKey);
        ResponseEntity<String> resp = rest.postForEntity(
                baseUrl + "/products", authEntity(token, body), String.class);
        JsonNode data = MAPPER.readTree(resp.getBody()).path("data");
        productId = data.path("id").asLong();
        assertTrue(productId > 0, "创建产品失败：" + resp.getBody());

        body.clear();
        body.put("productId", productId);
        body.put("deviceKey", deviceKey);
        body.put("deviceName", "live-verify-" + deviceKey);
        body.put("deviceType", "LIVETEST");
        body.put("topic", "device/" + deviceKey + "/data");
        resp = rest.postForEntity(baseUrl + "/devices", authEntity(token, body), String.class);
        data = MAPPER.readTree(resp.getBody()).path("data");
        deviceId = data.path("id").asLong();
        deviceUsername = data.path("username").asText();
        deviceSecret = data.path("deviceSecret").asText();
        assertTrue(deviceId > 0, "创建设备失败：" + resp.getBody());
        assertNotNull(deviceSecret, "未返回一次性明文密钥");
        System.out.printf("[0] 准备：productKey=%s deviceKey=%s deviceId=%d%n",
                productKey, deviceKey, deviceId);
    }

    private static String resolvePlatformSecret() {
        String value = System.getProperty("live.platformSecret");
        if (value == null || value.isBlank()) {
            value = System.getenv("PLATFORM_SECRET");
        }
        return value == null ? "" : value;
    }

    private MqttClient connect(String username, String password) throws Exception {
        MqttClient client = new MqttClient(brokerUrl,
                "live-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        client.connect(options);
        return client;
    }

    private void postDisable(String path) {
        ResponseEntity<String> resp = rest.exchange(
                baseUrl + path, HttpMethod.POST, authEntity(token), String.class);
        assertEquals(200, resp.getStatusCode().value(), "POST " + path + " 失败：" + resp.getBody());
    }

    private void cleanup() {
        rest.exchange(baseUrl + "/devices/" + deviceId, HttpMethod.DELETE,
                authEntity(token), String.class);
        rest.exchange(baseUrl + "/products/" + productId, HttpMethod.DELETE,
                authEntity(token), String.class);
        System.out.println("[9] 已清理测试产品与设备");
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
