<template>
  <DocArticle eyebrow="接入示例" :title="current.title" :desc="current.desc">
    <!-- 通用接入流程 -->
    <template v-if="section === 'general'">
      <h2>先理解接入模型</h2>
      <p>
        任何支持 MQTT 3.1.1 的客户端都可以接入；下面按常见芯片 / 运行环境给出示例。
        无论使用 Wi-Fi MCU、Linux 单板还是蜂窝模组，设备侧最终都只做四件事：
        用设备凭据连接 Broker、上报 Alink 载荷、订阅下行命令、按需回执。
        芯片与网络只影响「怎么连」，不影响平台侧的身份、Topic 与数据格式。
      </p>
      <ol>
        <li>在产品下创建设备，取得 <code>productKey</code>、<code>deviceKey</code> 与 <code>device_secret</code>。</li>
        <li>按产品认证方式连接 Broker：用户名 <code>{productKey}.{deviceKey}</code>，密码为设备密钥。</li>
        <li>设置遗愿主题，并在连接成功后上报一条属性数据。</li>
        <li>订阅下行命令主题，执行后向回执主题发布响应。</li>
        <li>断线按退避重连，避免高频重试与重复上报。</li>
      </ol>

      <h2>最小连接参数</h2>
      <table>
        <thead>
          <tr><th>参数</th><th>取值</th></tr>
        </thead>
        <tbody>
          <tr><td>Broker</td><td>部署服务器 IP / 域名；TCP <code>1883</code>、TLS <code>8883</code>、WebSocket <code>8083</code></td></tr>
          <tr><td>ClientId</td><td>建议使用 <code>deviceKey</code>，保持唯一</td></tr>
          <tr><td>用户名</td><td><code>{productKey}.{deviceKey}</code></td></tr>
          <tr><td>密码</td><td><code>device_secret</code> 明文</td></tr>
          <tr><td>KeepAlive</td><td>建议 60s，需小于网关空闲超时</td></tr>
          <tr><td>LWT</td><td><code>{topicPrefix}/lwt</code>，载荷 <code>offline</code>，QoS 1</td></tr>
          <tr><td>Topic 前缀</td><td>默认 <code>device/{deviceKey}</code>；若产品配置了自定义模板，以产品配置为准</td></tr>
        </tbody>
      </table>

      <h2>通用连接与收发流程</h2>
      <CodeBlock language="text" :code="generalPseudo" />

      <DocCallout type="warning" title="不要写死 Topic 前缀">
        示例统一使用默认前缀 <code>device/{deviceKey}</code>。如果产品配置了
        <code>topic_prefix</code>，设备必须按产品模板拼接，否则会被 ACL 拒绝。
      </DocCallout>

      <h2>最小属性上报</h2>
      <CodeBlock language="json" :code="propertySample" />
      <p>
        属性 / 事件必须使用 Alink 信封；平铺的 <code>{"temperature":26.5}</code> 会被判为无法解析。
        完整字段规则见 <router-link to="/docs/protocol/d2">D2 · 设备接入协议规范</router-link>。
      </p>

      <h2>命令与回执</h2>
      <CodeBlock language="json" :code="commandReplySample" />
      <p>回执的 <code>id</code> 必须等于下行命令 id；<code>code=200</code> 表示执行成功。</p>

      <h2>按芯片选择客户端</h2>
      <table>
        <thead>
          <tr><th>设备环境</th><th>可用方案</th><th>参考示例</th></tr>
        </thead>
        <tbody>
          <tr><td>ESP32 / ESP8266</td><td>ESP-IDF esp-mqtt、Arduino PubSubClient</td><td><router-link to="/docs/examples/esp32">ESP32 / ESP8266</router-link></td></tr>
          <tr><td>STM32 / 裸机 MCU</td><td>Paho Embedded C、厂商 MQTT SDK、蜂窝模组 AT</td><td><router-link to="/docs/examples/stm32">STM32 / 裸机 MCU</router-link></td></tr>
          <tr><td>Linux / 树莓派</td><td>paho-mqtt、mosquitto 客户端</td><td><router-link to="/docs/examples/linux">Linux / 树莓派</router-link></td></tr>
          <tr><td>4G Cat.1 / NB-IoT</td><td>模组内置 MQTT / AT 指令，或 HTTP 上报</td><td><router-link to="/docs/examples/cellular">4G / NB-IoT</router-link></td></tr>
        </tbody>
      </table>
    </template>

    <!-- ESP32 / ESP8266 -->
    <template v-else-if="section === 'esp32'">
      <h2>Arduino 框架</h2>
      <p>
        适用于 ESP32 与 ESP8266。使用 PubSubClient 连接 Broker，ArduinoJson 解析下行命令；
        先把 Wi-Fi 与 MQTT 连接逻辑跑通，再接传感器。
      </p>
      <CodeBlock language="bash" :code="arduinoInstall" />
      <CodeBlock language="cpp" :code="esp32ArduinoSample" />
      <ul>
        <li>ESP8266 将 <code>#include &lt;WiFi.h&gt;</code> 换成 <code>#include &lt;ESP8266WiFi.h&gt;</code>。</li>
        <li>PubSubClient 的 <code>publish()</code> 固定 QoS 0；数据与回执需要 QoS 1 时，请改用 ESP-IDF esp-mqtt 或支持 QoS 的客户端。</li>
        <li>PubSubClient 默认缓冲区较小，较长命令可在 <code>setBufferSize()</code> 中调大。</li>
        <li>生产环境建议改用 <code>WiFiClientSecure</code> 并连接 <code>8883</code>；不要在校验中跳过证书。</li>
      </ul>

      <h2>ESP-IDF 参考</h2>
      <p>使用 ESP-IDF v5 的 esp-mqtt 时，连接参数、遗愿与用户名密码可直接落到配置结构体：</p>
      <CodeBlock language="cpp" :code="espIdfSample" />

      <DocCallout type="tip" title="上报节奏">
        传感器采样与上报解耦：采样可以 1s 一次，上报按业务需要 5~60s 一次，避免把高频噪声全部推到平台。
      </DocCallout>
    </template>

    <!-- STM32 / 裸机 MCU -->
    <template v-else-if="section === 'stm32'">
      <h2>两种常见路径</h2>
      <table>
        <thead>
          <tr><th>路径</th><th>适用场景</th><th>要点</th></tr>
        </thead>
        <tbody>
          <tr><td>MCU + 以太网 / Wi-Fi</td><td>现场有稳定网络</td><td>移植 Paho Embedded C 或厂商 MQTT SDK，自己实现 socket 收发</td></tr>
          <tr><td>MCU + 蜂窝模组</td><td>4G Cat.1 / NB-IoT</td><td>由模组内置 MQTT 或 AT 指令完成连接，MCU 只组装载荷</td></tr>
        </tbody>
      </table>

      <h2>Paho Embedded C 骨架</h2>
      <p>
        下面是接口级骨架，省略了网络驱动、TLS 与重连细节；实际移植时按所用 SDK 版本补齐。
        重点是连接参数、遗愿与订阅关系必须与平台一致。
      </p>
      <CodeBlock language="c" :code="stm32Sample" />

      <h2>MCU + 蜂窝模组流程</h2>
      <ol>
        <li>上电、附着网络，等待模组注册成功。</li>
        <li>配置 MQTT ClientId、用户名、密码与 Broker 地址。</li>
        <li>发起连接，设置遗愿主题与载荷。</li>
        <li>发布属性数据，订阅 <code>{topicPrefix}/cmd/#</code>。</li>
        <li>解析模组上报的下行数据，执行后向 <code>{topicPrefix}/reply</code> 发布回执。</li>
        <li>断线后按指数退避重连，避免模组反复注册。</li>
      </ol>
      <p>不同模组的 AT 指令差异较大，请以模组手册为准；不要把设备密钥写入 AT 日志或串口调试输出。</p>

      <DocCallout type="warning" title="资源受限设备">
        固定缓冲区、限制 JSON 深度与字段数量；不要为每条消息动态分配大块内存。
        上报频率高时优先合并采样，避免看门狗与网络栈被拖垮。
      </DocCallout>
    </template>

    <!-- Linux / 树莓派 -->
    <template v-else-if="section === 'linux'">
      <h2>Python 示例</h2>
      <p>适用于树莓派、工控机、边缘网关等可运行 Python 的 Linux 设备。</p>
      <CodeBlock language="bash" :code="pythonInstall" />
      <CodeBlock language="python" :code="pythonSample" />
      <ul>
        <li>凭据从环境变量读取，不要把 <code>device_secret</code> 写进代码或 Git。</li>
        <li>按产品配置替换 <code>TOPIC_PREFIX</code>；默认前缀为 <code>device/{deviceKey}</code>。</li>
        <li>生产环境启用 TLS 时，将端口改为 <code>8883</code>，并配置 CA 证书校验。</li>
        <li>需要长期运行时，用 systemd / supervisor 托管并配置自动重启。</li>
      </ul>

      <h2>调试建议</h2>
      <ul>
        <li>先只订阅 <code>cmd/#</code> 观察下行，再打开属性上报，便于定位 ACL 与载荷问题。</li>
        <li>用 <code>mosquitto_sub</code> 或平台「实时消息」交叉验证设备是否真的发出数据。</li>
        <li>网络抖动时保留本地缓存，恢复后按时间顺序补发，避免把旧数据当成实时值。</li>
      </ul>
    </template>

    <!-- 4G / NB-IoT -->
    <template v-else>
      <h2>先选传输方式</h2>
      <table>
        <thead>
          <tr><th>方式</th><th>适合场景</th><th>注意</th></tr>
        </thead>
        <tbody>
          <tr><td>模组内置 MQTT</td><td>需要实时下行、长期在线</td><td>确认模组支持遗愿、KeepAlive 与 QoS1</td></tr>
          <tr><td>AT + MQTT</td><td>模组提供 MQTT AT 指令</td><td>指令随厂商/固件版本变化，必须做超时与重试</td></tr>
          <tr><td>HTTP 上报</td><td>低功耗、间歇联网</td><td>复用设备凭据，适合 NB-IoT 短连接</td></tr>
        </tbody>
      </table>

      <h2>HTTP 上报示例</h2>
      <p>
        不适合长期保持 MQTT 连接的设备，可直接使用 HTTP 上报；数据会进入与 MQTT
        相同的摄取管线，属性最新值与历史数据等价。
      </p>
      <CodeBlock language="bash" :code="httpSample" />
      <p>
        <code>messageType</code> 仅支持 <code>data</code> / <code>heartbeat</code> / <code>lwt</code>；
        成功响应中的 <code>accepted=true</code> 表示已进入摄取管线。
      </p>

      <h2>低功耗与可靠性</h2>
      <ul>
        <li>NB-IoT 优先短连接 + 批量上报，避免长时间维持 TCP 连接。</li>
        <li>为 HTTP 请求设置连接 / 读取超时，并在失败后指数退避。</li>
        <li>设备侧保留未确认数据，但补发时要带原始采集时间，避免污染实时曲线。</li>
        <li>使用 TLS 时确认模组证书存储与时间同步；证书校验失败不要静默降级为明文。</li>
      </ul>

      <DocCallout type="note" title="离线判定">
        没有遗愿能力的模组，平台可能在 TCP 断开后才更新在线状态；需要更及时的离线告警时，
        请结合心跳与平台离线规则一起使用。
      </DocCallout>
    </template>
  </DocArticle>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'
import DocCallout from '@/components/docs/DocCallout.vue'

const route = useRoute()

const sections = {
  general: { title: '通用接入流程', desc: '与芯片无关的连接、上报、订阅与回执模型' },
  esp32: { title: 'ESP32 / ESP8266', desc: 'Arduino 与 ESP-IDF 两种接入方式' },
  stm32: { title: 'STM32 / 裸机 MCU', desc: 'Paho Embedded C 骨架与蜂窝模组 AT 流程' },
  linux: { title: 'Linux / 树莓派', desc: 'Python paho-mqtt 完整示例与调试建议' },
  cellular: { title: '4G Cat.1 / NB-IoT', desc: '蜂窝模组的 MQTT / HTTP 接入与低功耗取舍' }
}

const section = computed(() => route.params.section || 'general')
const current = computed(() => sections[section.value] || sections.cellular)

const generalPseudo = `connect(
  host        = <broker-host>,
  port        = 1883,
  clientId    = <deviceKey>,
  username    = <productKey>.<deviceKey>,
  password    = <device_secret>,
  keepAlive   = 60,
  lwt         = { topic: <topicPrefix>/lwt, payload: "offline", qos: 1 }
)

on connected:
  subscribe(<topicPrefix>/cmd/#, qos=1)
  publish(<topicPrefix>/data, alinkProperty, qos=1)

on command(payload):
  id = payload.id
  if payload.method == "thing.service.property.set":
    apply(payload.params)
    publish(<topicPrefix>/reply, { id, code: 200, data: { handled: true } }, qos=1)

on disconnected:
  wait(backoff + jitter)
  reconnect()`

const propertySample = `{
  "id": "msg-001",
  "version": "1.0",
  "method": "thing.event.property.post",
  "params": {
    "temperature": 26.5,
    "humidity": 61,
    "power": true
  }
}`

const commandReplySample = `// 下行命令
{
  "id": "6f1c2a4e-8b7d-4e2a-9c1f-3d5e7a9b0c1d",
  "version": "1.0",
  "method": "thing.service.property.set",
  "params": { "power": true }
}

// 设备回执
{
  "id": "6f1c2a4e-8b7d-4e2a-9c1f-3d5e7a9b0c1d",
  "code": 200,
  "data": { "handled": true }
}`

const arduinoInstall = `# Arduino IDE：库管理器安装 PubSubClient 与 ArduinoJson
# 或使用 arduino-cli
arduino-cli lib install PubSubClient
arduino-cli lib install ArduinoJson`

const esp32ArduinoSample = `#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

const char* WIFI_SSID   = "<wifi-ssid>";
const char* WIFI_PASS   = "<wifi-password>";
const char* MQTT_HOST   = "<broker-host>";
const int   MQTT_PORT   = 1883;
const char* PRODUCT_KEY = "<productKey>";
const char* DEVICE_KEY  = "<deviceKey>";
const char* DEVICE_SECRET = "<device_secret>";

String username;
String prefix;
String lwtTopic;
String cmdTopic;
String dataTopic;
String replyTopic;

WiFiClient espClient;
PubSubClient client(espClient);
unsigned long lastPublish = 0;

void publishProperty() {
  JsonDocument doc;
  doc["id"] = String(DEVICE_KEY) + "-" + String(millis());
  doc["version"] = "1.0";
  doc["method"] = "thing.event.property.post";
  doc["params"]["temperature"] = 26.5;
  doc["params"]["humidity"] = 61;

  String payload;
  serializeJson(doc, payload);
  client.publish(dataTopic.c_str(), payload.c_str(), false);
}

void onMessage(char* topic, byte* payload, unsigned int length) {
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, payload, length);
  if (error) return;

  const char* id = doc["id"] | "";
  const char* method = doc["method"] | "";
  if (String(method) != "thing.service.property.set") return;

  // 在这里执行实际控制，例如 relay = doc["params"]["power"];
  JsonDocument reply;
  reply["id"] = id;
  reply["code"] = 200;
  reply["data"]["handled"] = true;
  String body;
  serializeJson(reply, body);
  client.publish(replyTopic.c_str(), body.c_str(), false);
}

void connectMqtt() {
  while (!client.connected()) {
    String clientId = String(DEVICE_KEY);
    if (client.connect(clientId.c_str(), username.c_str(), DEVICE_SECRET,
                       lwtTopic.c_str(), 1, false, "offline")) {
      client.subscribe(cmdTopic.c_str(), 1);
      publishProperty();
    } else {
      delay(5000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  username = String(PRODUCT_KEY) + "." + DEVICE_KEY;
  prefix = String("device/") + DEVICE_KEY;
  lwtTopic = prefix + "/lwt";
  cmdTopic = prefix + "/cmd/#";
  dataTopic = prefix + "/data";
  replyTopic = prefix + "/reply";

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(500);

  client.setServer(MQTT_HOST, MQTT_PORT);
  client.setCallback(onMessage);
  client.setKeepAlive(60);
  client.setBufferSize(512);
}

void loop() {
  if (!client.connected()) connectMqtt();
  client.loop();

  if (millis() - lastPublish > 10000) {
    lastPublish = millis();
    publishProperty();
  }
}`

const espIdfSample = `// ESP-IDF v5 参考；实际字段以所用 IDF 版本头文件为准
esp_mqtt_client_config_t cfg = {
    .broker.address.uri = "mqtt://<broker-host>:1883",
    .credentials.client_id = "<deviceKey>",
    .credentials.username = "<productKey>.<deviceKey>",
    .credentials.authentication.password = "<device_secret>",
    .session.keepalive = 60,
    .session.last_will.topic = "device/<deviceKey>/lwt",
    .session.last_will.msg = "offline",
    .session.last_will.qos = 1,
    .session.last_will.retain = false,
};
esp_mqtt_client_handle_t client = esp_mqtt_client_init(&cfg);
esp_mqtt_client_start(client);`

const stm32Sample = `#include "MQTTClient.h"

static Network network;
static MQTTClient client;
static unsigned char sendBuf[1024];
static unsigned char readBuf[1024];

static void onMessage(MQTTClient *c, char *topic, int topicLen, MQTTMessage *msg) {
    // 解析 msg->payload，执行命令后向 reply Topic 发布回执
}

void mqtt_start(void) {
    MQTTPacket_connectData data = MQTTPacket_connectData_initializer;
    data.MQTTVersion = 4;
    data.clientID.cstring = DEVICE_KEY;
    data.username.cstring = USERNAME;          // productKey.deviceKey
    data.password.cstring = DEVICE_SECRET;
    data.keepAliveInterval = 60;
    data.cleansession = 1;
    data.will.topicName.cstring = LWT_TOPIC;   // <topicPrefix>/lwt
    data.will.message.cstring = "offline";
    data.will.qos = 1;

    NewNetwork(&network);                      // 由移植层实现 socket 收发
    MQTTClientInit(&client, &network, 10000, sendBuf, sizeof(sendBuf),
                   readBuf, sizeof(readBuf));

    if (MQTTConnect(&client, &data) != SUCCESS) return;
    MQTTSubscribe(&client, CMD_TOPIC, QOS1, onMessage);

    while (1) {
        MQTTYield(&client, 1000);
        // 按采集周期组装 Alink 并 MQTTPublish 到 DATA_TOPIC
    }
}`

const pythonInstall = `python3 -m venv .venv
source .venv/bin/activate
pip install paho-mqtt`

const pythonSample = `import json
import os
import time

import paho.mqtt.client as mqtt

HOST = os.getenv("MQTT_HOST", "localhost")
PORT = int(os.getenv("MQTT_PORT", "1883"))
PRODUCT_KEY = os.environ["PRODUCT_KEY"]
DEVICE_KEY = os.environ["DEVICE_KEY"]
DEVICE_SECRET = os.environ["DEVICE_SECRET"]
TOPIC_PREFIX = os.getenv("TOPIC_PREFIX", f"device/{DEVICE_KEY}")
USERNAME = f"{PRODUCT_KEY}.{DEVICE_KEY}"

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=DEVICE_KEY)
client.username_pw_set(USERNAME, DEVICE_SECRET)
client.will_set(f"{TOPIC_PREFIX}/lwt", "offline", qos=1, retain=False)


def on_connect(client, userdata, flags, reason_code, properties):
    print("connected:", reason_code)
    client.subscribe(f"{TOPIC_PREFIX}/cmd/#", qos=1)


def on_message(client, userdata, message):
    command = json.loads(message.payload)
    command_id = command.get("id")
    if command.get("method") != "thing.service.property.set":
        return

    # 在这里执行实际控制，例如 apply(command["params"])
    reply = {"id": command_id, "code": 200, "data": {"handled": True}}
    client.publish(f"{TOPIC_PREFIX}/reply", json.dumps(reply), qos=1)


client.on_connect = on_connect
client.on_message = on_message
client.connect(HOST, PORT, keepalive=60)
client.loop_forever()`

const httpSample = `curl -X POST "https://<your-server>/api/ingest/<deviceKey>/data" \\
  -u "<productKey>.<deviceKey>:<device_secret>" \\
  -H "Content-Type: application/json" \\
  -d '{"method":"thing.event.property.post","params":{"temperature":26.5}}'`
</script>
