package com.mqtt.cloud.service.impl;

import com.mqtt.cloud.common.ResultCode;
import com.mqtt.cloud.common.exception.BusinessException;
import com.mqtt.cloud.common.logging.TraceIdFilter;
import com.mqtt.cloud.config.DebugProperties;
import com.mqtt.cloud.dto.response.DebugCodeResponse;
import com.mqtt.cloud.dto.response.DebugContextResponse;
import com.mqtt.cloud.dto.response.DebugSimulateResponse;
import com.mqtt.cloud.entity.Device;
import com.mqtt.cloud.entity.DevicePropertyLatest;
import com.mqtt.cloud.entity.Product;
import com.mqtt.cloud.ingest.IngestPipeline;
import com.mqtt.cloud.ingest.IngestProperties;
import com.mqtt.cloud.ingest.IngestRecord;
import com.mqtt.cloud.service.DeviceDataService;
import com.mqtt.cloud.service.DeviceDebugService;
import com.mqtt.cloud.service.ThingModelDefinition;
import com.mqtt.cloud.service.ThingModelService;
import com.mqtt.cloud.service.TopicPrefixResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备调试中心实现（T-37）。
 * <p>
 * 模拟上行刻意复用 {@link IngestPipeline}：只有走同一条管线，下游的物模型解析、影子、
 * 规则、告警、Webhook 与 SSE 才会与真机上报完全一致。若另起一条「调试专用落库」，
 * 调试结果与真机结果将不可避免地漂移。
 * <p>
 * 代码生成只读取物模型与产品配置，不读取设备密钥；设备端样例中的密码一律用环境变量占位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDebugServiceImpl implements DeviceDebugService {

    static final Set<String> MESSAGE_TYPES = Set.of("data", "heartbeat", "lwt", "reply", "ota");
    static final Set<String> JSON_REQUIRED_TYPES = Set.of("data", "reply", "ota");
    static final Set<String> DEVICE_LANGUAGES = Set.of("javascript", "python", "java", "arduino");
    static final Set<String> HOST_LANGUAGES = Set.of("javascript", "python", "java");

    private static final String TARGET_DEVICE = "device";
    private static final String TARGET_HOST = "host";
    private static final String DEFAULT_WS_PATH = "/mqtt";
    private static final String PASSWORD_HINT = "设备密钥仅在创建 / 重置设备时一次性展示";

    private final DebugProperties debugProperties;
    private final ThingModelService thingModelService;
    private final DeviceDataService deviceDataService;
    private final IngestPipeline ingestPipeline;
    private final IngestProperties ingestProperties;
    private final TopicPrefixResolver topicPrefixResolver;

    @Override
    public DebugContextResponse context(Device device, Product product) {
        ThingModelDefinition model = thingModelService.getForProduct(product.getId());
        Map<String, DevicePropertyLatest> latest = deviceDataService.getPropertyLatest(device.getId()).stream()
                .collect(Collectors.toMap(DevicePropertyLatest::getIdentifier, Function.identity(), (left, right) -> left));

        List<DebugContextResponse.PropertyView> properties = model.properties().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.PropertySpec::identifier))
                .map(spec -> toPropertyView(spec, latest.get(spec.identifier())))
                .toList();
        List<DebugContextResponse.PropertyView> writable = properties.stream()
                .filter(item -> "rw".equals(item.accessMode()))
                .toList();
        List<DebugContextResponse.EventView> events = model.events().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.EventSpec::identifier))
                .map(spec -> new DebugContextResponse.EventView(spec.identifier(), spec.eventType()))
                .toList();
        List<DebugContextResponse.ServiceView> services = model.services().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.ServiceSpec::identifier))
                .map(this::toServiceView)
                .toList();

        return new DebugContextResponse(
                device.getDeviceKey(),
                device.getDeviceName(),
                product.getProductKey(),
                product.getProductName(),
                StringUtils.hasText(product.getProtocolType()) ? product.getProtocolType() : "MQTT",
                StringUtils.hasText(product.getAuthMode()) ? product.getAuthMode() : "SECRET",
                device.getStatus(),
                model.version() > 0,
                model.version(),
                broker(product, device),
                topics(product, device),
                properties,
                events,
                services,
                writable);
    }

    @Override
    public DebugSimulateResponse simulate(Long operatorId, Device device, Product product,
                                          String messageType, JsonNode payload) {
        String type = messageType == null ? "" : messageType.trim().toLowerCase(Locale.ROOT);
        if (!MESSAGE_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.DEBUG_MESSAGE_TYPE_UNSUPPORTED);
        }
        String payloadText = payloadText(type, payload);
        int size = payloadText.getBytes(StandardCharsets.UTF_8).length;
        if (size > debugProperties.getMaxPayloadBytes()) {
            throw new BusinessException(ResultCode.DEBUG_PAYLOAD_INVALID,
                    "调试载荷超过上限 " + debugProperties.getMaxPayloadBytes() + " 字节");
        }

        String prefix = topicPrefixResolver.resolve(product.getTopicPrefix(),
                product.getProductKey(), device.getDeviceKey());
        String topic = prefix + type;
        LocalDateTime acceptedAt = LocalDateTime.now();
        boolean submitted = ingestProperties.isEnabled();
        if (!submitted) {
            log.warn("调试模拟上报未提交：摄取管线已关闭(app.ingest.enabled=false), operatorId={}, deviceKey={}, type={}",
                    operatorId, device.getDeviceKey(), type);
        } else {
            String traceId = MDC.get(TraceIdFilter.TRACE_ID);
            ingestPipeline.submit(new IngestRecord(device.getDeviceKey(), topic, type, payloadText,
                    qosOf(type), acceptedAt, traceId));
            // 不记录完整 payload：调试报文可能含现场敏感数据
            log.info("设备调试模拟上报: operatorId={}, deviceKey={}, messageType={}, size={}, topic={}",
                    operatorId, device.getDeviceKey(), type, size, topic);
        }
        return new DebugSimulateResponse(device.getDeviceKey(), type, topic, size, submitted, acceptedAt);
    }

    @Override
    public DebugCodeResponse code(Device device, Product product, String target, String language) {
        String normalizedTarget = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        String normalizedLanguage = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        boolean deviceTarget = TARGET_DEVICE.equals(normalizedTarget);
        boolean hostTarget = TARGET_HOST.equals(normalizedTarget);
        if (!deviceTarget && !hostTarget) {
            throw new BusinessException(ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED, "target 仅支持 device / host");
        }
        Set<String> allowed = deviceTarget ? DEVICE_LANGUAGES : HOST_LANGUAGES;
        if (!allowed.contains(normalizedLanguage)) {
            throw new BusinessException(ResultCode.DEBUG_CODE_TARGET_UNSUPPORTED,
                    "该 target 不支持语言 " + normalizedLanguage);
        }

        ThingModelDefinition model = thingModelService.getForProduct(product.getId());
        Map<String, String> vars = codeVariables(device, product, model);
        String content = deviceTarget
                ? deviceCode(normalizedLanguage, vars)
                : hostCode(normalizedLanguage, vars);
        String extension = switch (normalizedLanguage) {
            case "javascript" -> "js";
            case "python" -> "py";
            case "java" -> "java";
            default -> "ino";
        };
        String prefix = deviceTarget ? "device" : "host";
        return new DebugCodeResponse(normalizedTarget, normalizedLanguage,
                prefix + "-" + device.getDeviceKey() + "." + extension, content);
    }

    private DebugContextResponse.Broker broker(Product product, Device device) {
        String host = StringUtils.hasText(debugProperties.getPublicBrokerHost())
                ? debugProperties.getPublicBrokerHost().trim()
                : "localhost";
        String wsPath = StringUtils.hasText(debugProperties.getWsPath())
                ? debugProperties.getWsPath().trim()
                : DEFAULT_WS_PATH;
        if (!wsPath.startsWith("/")) {
            wsPath = "/" + wsPath;
        }
        return new DebugContextResponse.Broker(
                "tcp://" + host + ":" + debugProperties.getTcpPort(),
                "ssl://" + host + ":" + debugProperties.getTlsPort(),
                "ws://" + host + ":" + debugProperties.getWsPort() + wsPath,
                product.getProductKey() + "." + device.getDeviceKey(),
                PASSWORD_HINT);
    }

    private DebugContextResponse.Topics topics(Product product, Device device) {
        String prefix = topicPrefixResolver.resolve(product.getTopicPrefix(),
                product.getProductKey(), device.getDeviceKey());
        return new DebugContextResponse.Topics(
                prefix + "data",
                prefix + "heartbeat",
                prefix + "lwt",
                prefix + "reply",
                prefix + "ota",
                // 下行主题在 DeviceCommandServiceImpl 中固定为 device/{deviceKey}/cmd/down
                "device/" + device.getDeviceKey() + "/cmd/down");
    }

    private DebugContextResponse.PropertyView toPropertyView(ThingModelDefinition.PropertySpec spec,
                                                             DevicePropertyLatest latest) {
        return new DebugContextResponse.PropertyView(
                spec.identifier(),
                spec.type(),
                spec.accessMode(),
                spec.min(),
                spec.max(),
                spec.integer(),
                List.copyOf(spec.enumKeys()),
                spec.textLength(),
                latest == null ? null : latest.getValueText(),
                latest == null ? null : latest.getReportedAt());
    }

    private DebugContextResponse.ServiceView toServiceView(ThingModelDefinition.ServiceSpec spec) {
        List<DebugContextResponse.ParamView> input = spec.input().values().stream()
                .sorted(Comparator.comparing(ThingModelDefinition.ParamSpec::identifier))
                .map(param -> new DebugContextResponse.ParamView(param.identifier(), param.type(), param.min(),
                        param.max(), param.integer(), List.copyOf(param.enumKeys()), param.textLength(),
                        param.required()))
                .toList();
        return new DebugContextResponse.ServiceView(spec.identifier(), spec.callType(), input);
    }

    private String payloadText(String messageType, JsonNode payload) {
        if (JSON_REQUIRED_TYPES.contains(messageType)) {
            if (payload == null || !payload.isObject()) {
                throw new BusinessException(ResultCode.DEBUG_PAYLOAD_INVALID,
                        messageType + " 的载荷必须是 JSON 对象");
            }
            return payload.toString();
        }
        if (payload == null || payload.isNull()) {
            return "";
        }
        return payload.isTextual() ? payload.asText() : payload.toString();
    }

    private int qosOf(String messageType) {
        return "heartbeat".equals(messageType) ? 0 : 1;
    }

    // ================= 代码生成 =================

    private Map<String, String> codeVariables(Device device, Product product, ThingModelDefinition model) {
        String prefix = topicPrefixResolver.resolve(product.getTopicPrefix(),
                product.getProductKey(), device.getDeviceKey());
        String host = StringUtils.hasText(debugProperties.getPublicBrokerHost())
                ? debugProperties.getPublicBrokerHost().trim()
                : "localhost";
        String firstWritable = model.properties().values().stream()
                .filter(ThingModelDefinition.PropertySpec::writable)
                .map(ThingModelDefinition.PropertySpec::identifier)
                .sorted()
                .findFirst().orElse("your_property");
        String firstProperty = model.properties().keySet().stream().sorted().findFirst().orElse("your_property");
        String firstService = model.services().keySet().stream().sorted().findFirst().orElse("your_service");
        String sampleValue = model.properties().values().stream()
                .filter(ThingModelDefinition.PropertySpec::writable)
                .min(Comparator.comparing(ThingModelDefinition.PropertySpec::identifier))
                .map(this::sampleValue)
                .orElse("\"value\"");

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("DEVICE_KEY", device.getDeviceKey());
        vars.put("PRODUCT_KEY", product.getProductKey());
        vars.put("USERNAME", product.getProductKey() + "." + device.getDeviceKey());
        vars.put("BROKER_HOST", host);
        vars.put("TCP_PORT", String.valueOf(debugProperties.getTcpPort()));
        vars.put("WS_PORT", String.valueOf(debugProperties.getWsPort()));
        vars.put("DATA_TOPIC", prefix + "data");
        vars.put("HEARTBEAT_TOPIC", prefix + "heartbeat");
        vars.put("LWT_TOPIC", prefix + "lwt");
        vars.put("REPLY_TOPIC", prefix + "reply");
        vars.put("OTA_TOPIC", prefix + "ota");
        vars.put("COMMAND_TOPIC", "device/" + device.getDeviceKey() + "/cmd/down");
        String payloadJson = samplePayload(model);
        vars.put("PAYLOAD_JSON", payloadJson);
        vars.put("PAYLOAD_JSON_JAVA", payloadJson.replace("\\", "\\\\").replace("\"", "\\\""));
        vars.put("FIRST_PROPERTY", firstProperty);
        vars.put("FIRST_WRITABLE", firstWritable);
        vars.put("SAMPLE_VALUE", sampleValue);
        vars.put("FIRST_SERVICE", firstService);
        return vars;
    }

    private String deviceCode(String language, Map<String, String> vars) {
        return switch (language) {
            case "javascript" -> render(DEVICE_JS, vars);
            case "python" -> render(DEVICE_PYTHON, vars);
            case "java" -> render(DEVICE_JAVA, vars);
            default -> render(DEVICE_ARDUINO, vars);
        };
    }

    private String hostCode(String language, Map<String, String> vars) {
        return switch (language) {
            case "javascript" -> render(HOST_JS, vars);
            case "python" -> render(HOST_PYTHON, vars);
            default -> render(HOST_JAVA, vars);
        };
    }

    private String render(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * 属性上报样例：外层必须是物模型解析链路认的 Alink 信封。
     * <p>
     * {@code ThingModelInterpretServiceImpl} 只处理 {@code method=thing.event.property.post}
     * 且读取 {@code params} 的对象；平铺的 {@code {temperature: 25}} 会被记为「无法解析」，
     * 调试时看起来上报成功、属性却不动。
     */
    private String samplePayload(ThingModelDefinition model) {
        List<ThingModelDefinition.PropertySpec> specs = new ArrayList<>(model.properties().values());
        specs.sort(Comparator.comparing(ThingModelDefinition.PropertySpec::identifier));
        StringBuilder params = new StringBuilder("{");
        for (int i = 0; i < specs.size(); i++) {
            ThingModelDefinition.PropertySpec spec = specs.get(i);
            if (i > 0) {
                params.append(", ");
            }
            params.append(quote(spec.identifier())).append(": ").append(sampleValue(spec));
        }
        params.append('}');
        return "{\"id\":\"debug\",\"version\":\"1.0\","
                + "\"method\":\"thing.event.property.post\",\"params\":" + params + "}";
    }

    private String sampleValue(ThingModelDefinition.PropertySpec spec) {
        String type = spec.type() == null ? "" : spec.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "bool", "boolean" -> "false";
            case "int", "integer", "long" -> spec.min() == null ? "0" : integerText(spec.min());
            case "float", "double", "number" -> spec.min() == null ? "25.0" : spec.min().toPlainString();
            case "enum" -> quote(spec.enumKeys().stream().sorted().findFirst().orElse("value"));
            case "date" -> "\"2026-01-01 00:00:00\"";
            case "struct", "object" -> "{}";
            case "array" -> "[]";
            case "text", "string" -> "\"hello\"";
            default -> "\"value\"";
        };
    }

    private String integerText(BigDecimal value) {
        try {
            return value.toBigIntegerExact().toString();
        } catch (ArithmeticException e) {
            return value.toBigInteger().toString();
        }
    }

    private String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    // ================= 模板 =================

    private static final String DEVICE_JS = """
            // 设备端最小联调样例（Node.js）
            // 依赖：npm i mqtt
            const mqtt = require('mqtt')

            const REPORT_TOPIC = '{{DATA_TOPIC}}'
            const COMMAND_TOPIC = '{{COMMAND_TOPIC}}'
            const REPLY_TOPIC = '{{REPLY_TOPIC}}'

            const client = mqtt.connect('tcp://{{BROKER_HOST}}:{{TCP_PORT}}', {
              username: '{{USERNAME}}',
              // 设备密钥仅在创建 / 重置设备时一次性返回，请勿写入源码
              password: process.env.DEVICE_SECRET,
              clientId: '{{DEVICE_KEY}}-debug'
            })

            client.on('connect', () => {
              console.log('MQTT connected')
              client.subscribe(COMMAND_TOPIC, { qos: 1 })
              client.publish(REPORT_TOPIC, JSON.stringify({{PAYLOAD_JSON}}), { qos: 1 })
            })

            client.on('message', (topic, buffer) => {
              const command = JSON.parse(buffer.toString())
              // 按物模型处理 command.method / command.params，这里只回执「已收到」
              const reply = { id: command.id, code: 200, data: { handled: true } }
              client.publish(REPLY_TOPIC, JSON.stringify(reply), { qos: 1 })
            })

            client.on('error', (error) => console.error('MQTT error:', error.message))
            """;

    private static final String DEVICE_PYTHON = """
            # 设备端最小联调样例（Python 3）
            # 依赖：pip install paho-mqtt
            import json
            import os
            import paho.mqtt.client as mqtt

            REPORT_TOPIC = "{{DATA_TOPIC}}"
            COMMAND_TOPIC = "{{COMMAND_TOPIC}}"
            REPLY_TOPIC = "{{REPLY_TOPIC}}"
            PAYLOAD = json.loads(r'''{{PAYLOAD_JSON}}''')


            def on_connect(client, userdata, flags, reason_code, properties):
                print("MQTT connected:", reason_code)
                client.subscribe(COMMAND_TOPIC, qos=1)
                client.publish(REPORT_TOPIC, json.dumps(PAYLOAD), qos=1)


            def on_message(client, userdata, message):
                command = json.loads(message.payload.decode("utf-8"))
                # 按物模型处理 command["method"] / command["params"]
                reply = {"id": command.get("id"), "code": 200, "data": {"handled": True}}
                client.publish(REPLY_TOPIC, json.dumps(reply), qos=1)


            client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="{{DEVICE_KEY}}-debug")
            client.username_pw_set("{{USERNAME}}", os.environ.get("DEVICE_SECRET", ""))
            client.on_connect = on_connect
            client.on_message = on_message
            client.connect("{{BROKER_HOST}}", {{TCP_PORT}}, keepalive=60)
            client.loop_forever()
            """;

    private static final String DEVICE_JAVA = """
            // 设备端最小联调样例（Java 17 + Eclipse Paho）
            // 依赖：org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5
            import org.eclipse.paho.client.mqttv3.*;
            import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

            import java.nio.charset.StandardCharsets;

            public class DeviceClient {
                public static void main(String[] args) throws Exception {
                    String broker = "tcp://{{BROKER_HOST}}:{{TCP_PORT}}";
                    MqttClient client = new MqttClient(broker, "{{DEVICE_KEY}}-debug", new MemoryPersistence());
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setUserName("{{USERNAME}}");
                    options.setPassword(System.getenv("DEVICE_SECRET").toCharArray());
                    options.setCleanSession(true);

                    client.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            System.out.println("connection lost: " + cause.getMessage());
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) {
                            // 按物模型处理 command.method / command.params；
                            // 回执必须带 command.id，这里先用占位值验证链路，接入时替换为真实 id
                            String command = new String(message.getPayload(), StandardCharsets.UTF_8);
                            String reply = "{\\"id\\":\\"debug\\",\\"code\\":200,\\"data\\":{\\"handled\\":true}}";
                            System.out.println("command: " + command);
                            try {
                                client.publish("{{REPLY_TOPIC}}", reply.getBytes(StandardCharsets.UTF_8), 1, false);
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                        }
                    });

                    client.connect(options);
                    client.subscribe("{{COMMAND_TOPIC}}", 1);
                    client.publish("{{DATA_TOPIC}}",
                            "{{PAYLOAD_JSON_JAVA}}".getBytes(StandardCharsets.UTF_8), 1, false);
                    System.out.println("connected, property report sent");
                }
            }
            """;

    private static final String DEVICE_ARDUINO = """
            // 设备端最小联调样例（ESP32 + Arduino）
            // 依赖库：PubSubClient（Arduino Library Manager 搜索安装）
            #include <WiFi.h>
            #include <PubSubClient.h>

            const char* WIFI_SSID = "YOUR_WIFI_SSID";
            const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
            const char* MQTT_HOST = "{{BROKER_HOST}}";
            const int   MQTT_PORT = {{TCP_PORT}};
            const char* MQTT_USER = "{{USERNAME}}";
            const char* MQTT_PASS = "YOUR_DEVICE_SECRET";   // 仅创建 / 重置设备时一次性返回

            const char* REPORT_TOPIC  = "{{DATA_TOPIC}}";
            const char* COMMAND_TOPIC = "{{COMMAND_TOPIC}}";
            const char* REPLY_TOPIC   = "{{REPLY_TOPIC}}";
            const char* PAYLOAD = R"json({{PAYLOAD_JSON}})json";

            WiFiClient espClient;
            PubSubClient client(espClient);
            unsigned long lastReport = 0;

            void callback(char* topic, byte* payload, unsigned int length) {
              // 按物模型解析 command.method / command.params，这里只回执「已收到」
              String command;
              for (unsigned int i = 0; i < length; i++) command += (char) payload[i];
              String reply = "{\\"id\\":\\"debug\\",\\"code\\":200,\\"data\\":{\\"handled\\":true}}";
              client.publish(REPLY_TOPIC, reply.c_str());
              Serial.println(command);
            }

            void setup() {
              Serial.begin(115200);
              WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
              while (WiFi.status() != WL_CONNECTED) delay(500);
              client.setServer(MQTT_HOST, MQTT_PORT);
              client.setCallback(callback);
              if (client.connect("{{DEVICE_KEY}}-debug", MQTT_USER, MQTT_PASS)) {
                client.subscribe(COMMAND_TOPIC);
                client.publish(REPORT_TOPIC, PAYLOAD);
              }
            }

            void loop() {
              if (!client.connected()) {
                client.connect("{{DEVICE_KEY}}-debug", MQTT_USER, MQTT_PASS);
              }
              client.loop();
              if (millis() - lastReport > 10000) {
                lastReport = millis();
                client.publish(REPORT_TOPIC, PAYLOAD);
              }
            }
            """;

    private static final String HOST_JS = """
            // 上位机最小联调样例（Node.js 18+，无需安装依赖）
            // 1. 在「API 密钥」页创建密钥，填入环境变量 PLATFORM_API_KEY
            const BASE = (process.env.PLATFORM_BASE_URL || 'http://localhost:8080') + '/api/external/v1'
            const API_KEY = process.env.PLATFORM_API_KEY || '<YOUR_API_KEY>'

            async function api(path, options = {}) {
              const response = await fetch(BASE + path, {
                ...options,
                headers: { 'X-API-Key': API_KEY, 'Content-Type': 'application/json', ...(options.headers || {}) }
              })
              const body = await response.json()
              if (!response.ok || body.code !== 200) throw new Error(JSON.stringify(body))
              return body.data
            }

            // 读取属性最新值
            const properties = await api('/devices/{{DEVICE_KEY}}/properties')
            console.log(properties)

            // 写期望值（离线设备会排队，上线后补发）
            await api('/devices/{{DEVICE_KEY}}/shadow/desired', {
              method: 'PUT',
              body: JSON.stringify({ params: { {{FIRST_WRITABLE}}: {{SAMPLE_VALUE}} } })
            })

            // 调用服务
            await api('/devices/{{DEVICE_KEY}}/command', {
              method: 'POST',
              body: JSON.stringify({
                type: 'service',
                identifier: '{{FIRST_SERVICE}}',
                params: {},
                callType: 'async'
              })
            })
            """;

    private static final String HOST_PYTHON = """
            # 上位机最小联调样例（Python 3）
            # 依赖：pip install requests
            # 1. 在「API 密钥」页创建密钥，填入环境变量 PLATFORM_API_KEY
            import json
            import os
            import requests

            BASE = os.environ.get("PLATFORM_BASE_URL", "http://localhost:8080") + "/api/external/v1"
            HEADERS = {
                "X-API-Key": os.environ.get("PLATFORM_API_KEY", "<YOUR_API_KEY>"),
                "Content-Type": "application/json",
            }


            def api(path, method="GET", payload=None):
                response = requests.request(method, BASE + path, headers=HEADERS,
                                            data=json.dumps(payload) if payload is not None else None,
                                            timeout=10)
                body = response.json()
                if response.status_code >= 400 or body.get("code") != 200:
                    raise RuntimeError(body)
                return body.get("data")


            # 读取属性最新值
            print(api("/devices/{{DEVICE_KEY}}/properties"))

            # 写期望值（离线设备会排队，上线后补发）
            api("/devices/{{DEVICE_KEY}}/shadow/desired", "PUT",
                {"params": {"{{FIRST_WRITABLE}}": {{SAMPLE_VALUE}}}})

            # 调用服务
            api("/devices/{{DEVICE_KEY}}/command", "POST",
                {"type": "service", "identifier": "{{FIRST_SERVICE}}", "params": {}, "callType": "async"})
            """;

    private static final String HOST_JAVA = """
            // 上位机最小联调样例（Java 17，仅 JDK 标准库）
            // 1. 在「API 密钥」页创建密钥，填入环境变量 PLATFORM_API_KEY
            import java.net.URI;
            import java.net.http.HttpClient;
            import java.net.http.HttpRequest;
            import java.net.http.HttpResponse;
            import java.time.Duration;

            public class HostClient {
                private static final String BASE = System.getenv().getOrDefault(
                        "PLATFORM_BASE_URL", "http://localhost:8080") + "/api/external/v1";
                private static final String API_KEY = System.getenv().getOrDefault("PLATFORM_API_KEY", "<YOUR_API_KEY>");
                private static final HttpClient CLIENT = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)).build();

                static String request(String method, String path, String body) throws Exception {
                    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                            .header("X-API-Key", API_KEY)
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(10));
                    if (body == null) {
                        builder.method(method, HttpRequest.BodyPublishers.noBody());
                    } else {
                        builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                    }
                    HttpResponse<String> response = CLIENT.send(builder.build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException(response.body());
                    }
                    return response.body();
                }

                public static void main(String[] args) throws Exception {
                    // 读取属性最新值
                    System.out.println(request("GET", "/devices/{{DEVICE_KEY}}/properties", null));

                    // 写期望值（离线设备会排队，上线后补发）
                    request("PUT", "/devices/{{DEVICE_KEY}}/shadow/desired",
                            "{\\"params\\":{\\"{{FIRST_WRITABLE}}\\":{{SAMPLE_VALUE}}}}");

                    // 调用服务
                    request("POST", "/devices/{{DEVICE_KEY}}/command",
                            "{\\"type\\":\\"service\\",\\"identifier\\":\\"{{FIRST_SERVICE}}\\",\\"params\\":{},\\"callType\\":\\"async\\"}");
                }
            }
            """;
}
