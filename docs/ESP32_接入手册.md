# ESP32 接入 MQTT 云平台 - 接入手册

> 版本：v1.4 | 日期：2026-10-02

---

## 1. 接入前确认清单

| 项目 | 当前值 | 说明 |
|------|--------|------|
| MQTT Broker | EMQX 5（Docker Compose 部署） | 运行在本地（或服务器 IP） |
| MQTT TCP 端口 | 1883 | 设备通过此端口连接 |
| MQTT WebSocket 端口 | 8083 | 前端实时消息监控使用（设备无需关心） |
| EMQX Dashboard | 18083 | 管理后台，查看连接/启用认证 |
| MQTT 用户名 | `{产品标识}.{设备标识}` | 例：`esp32-fall.sensor-livingroom-01`，见 4.4 |
| MQTT 密码 | 设备密钥 `deviceSecret` | 创建设备时一次性返回，平台不提供二次查询 |
| 后端服务 | http://localhost:8080/api | 负责接收并持久化设备消息 |
| 前端管理页 | http://localhost:3000 | 创建产品与设备、查看数据（Docker 部署为 80 端口） |
| 设备注册方式 | 前端页面创建产品 + 设备 | ESP32 须先在前端注册才能发消息 |
| 产品（productKey） | 设备类型模板 | 创建设备前必须先有产品，用户名与 Topic 都由它参与拼接 |

---

## 2. 接入流程（共 4 步）

```
Step 1: 在前端页面创建产品与设备（拿到 username / deviceSecret）
       ↓
Step 2: ESP32 连接 MQTT Broker
       ↓
Step 3: ESP32 订阅 / 发布消息
       ↓
Step 4: 在前端查看实时数据
```

---

## 3. Step 1：创建设备（必须先做）

ESP32 发送的每条 MQTT 消息都需要关联到一个已注册的设备。如果设备不存在，后端会忽略消息。

**操作路径：** 打开 http://localhost:3000 → 登录 → 左侧「设备管理」→ 点击「创建设备」

> 创建设备前必须先有**产品**（设备类型模板）。若下拉框为空，先用 admin 账号通过
> `POST /api/products` 创建产品，或由管理员在控制台创建。

**必填字段：**

| 字段 | 说明 | 示例 |
|------|------|------|
| 所属产品 | 设备类型模板，决定 `productKey` | esp32-fall |
| 设备名称 | 自定义，方便识别 | 客厅温湿度传感器 |
| 设备标识（device_key） | **同一产品内唯一**，ESP32 的 Client ID 和 Topic 都用它 | sensor-livingroom-01 |
| 设备类型 | sensor / gateway / actuator | sensor |
| MQTT Topic | 消息发布的 Topic 路径 | device/sensor-livingroom-01/data |
| 描述 | 选填 | 温湿度传感器，DHT11 |

创建成功后，平台会弹窗返回**一次性凭据**，请立即保存：

| 凭据 | 用途 |
|------|------|
| MQTT 用户名 | `{productKey}.{deviceKey}`，如 `esp32-fall.sensor-livingroom-01` |
| 设备密钥（deviceSecret） | MQTT 密码 |

> **密钥仅在下发与重置时返回一次**，平台不提供二次查询。丢失只能通过
> `POST /api/devices/{id}/reset-secret` 重置（旧密钥立即失效），或由管理员执行
> `POST /api/devices/export-credentials` 批量重置导出。

---

## 4. Step 2：ESP32 连接 MQTT Broker

### 4.1 本地测试（ESP32 和电脑在同一 WiFi）

```cpp
// WiFi 和 MQTT 配置
const char* WIFI_SSID     = "你的WiFi名称";
const char* WIFI_PASSWORD = "你的WiFi密码";

// Broker 地址 = 电脑的局域网 IP
// 查看方法：Windows 命令行执行 ipconfig，找 IPv4 地址
const char* MQTT_SERVER   = "192.168.1.57";  // ← 替换为你的电脑 IP
const int   MQTT_PORT     = 1883;
const char* MQTT_USER     = "esp32-fall.sensor-livingroom-01";  // {productKey}.{deviceKey}
const char* MQTT_PASS     = "创建设备时返回的 deviceSecret";
```

### 4.2 部署到服务器（ESP32  anywhere）

服务器部署后，将 `MQTT_SERVER` 替换为服务器的公网 IP 或域名：

```cpp
// 部署到服务器后的配置
const char* MQTT_SERVER   = "123.45.67.89";   // 服务器公网 IP
// 或
const char* MQTT_SERVER   = "mqtt.yourdomain.com";  // 域名（需 DNS 解析）
const int   MQTT_PORT     = 1883;
```

### 4.3 完整连接代码（Arduino IDE / PlatformIO）

#### 依赖库

在 `platformio.ini` 或 Arduino Library Manager 中安装：

```
- PubSubClient (by Nick O'Leary)   v2.8+
- WiFi (ESP32 内置)
```

#### platformio.ini 示例

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200
lib_deps =
    knolleary/PubSubClient@^2.8
```

#### main.cpp

```cpp
#include <WiFi.h>
#include <PubSubClient.h>

// ==================== WiFi 配置 ====================
const char* WIFI_SSID     = "你的WiFi名称";
const char* WIFI_PASSWORD = "你的WiFi密码";

// ==================== MQTT 配置 ====================
// 本地测试：填电脑局域网 IP（ipconfig 查看）
// 部署到服务器：填服务器公网 IP 或域名
const char* MQTT_SERVER   = "192.168.1.57";
const int   MQTT_PORT     = 1883;
const char* MQTT_USER     = "esp32-fall.sensor-livingroom-01";  // {productKey}.{deviceKey}
const char* MQTT_PASS     = "创建设备时返回的 deviceSecret";

// ==================== 设备信息 ====================
// 必须与前端创建设备时的 device_key 完全一致
const char* DEVICE_KEY     = "sensor-livingroom-01";

// ==================== 生成 Topic 和 Client ID ====================
// Topic 格式：device/{device_key}/data
// 订阅 heartbeat 用于设备在线状态
// 订阅 lwt（遗言主题）用于断线检测
String topicData    = String("device/") + DEVICE_KEY + "/data";
String topicHbt     = String("device/") + DEVICE_KEY + "/heartbeat";
String topicLwt     = String("device/") + DEVICE_KEY + "/lwt";
// 命令下发与回执（T-15）：订阅 cmd/down 收命令，回执发 reply
String topicCmdDown = String("device/") + DEVICE_KEY + "/cmd/down";
String topicReply   = String("device/") + DEVICE_KEY + "/reply";

// Client ID 必须全局唯一
String clientId = String("esp32_") + DEVICE_KEY + "_" + String(random(0xffff), HEX);

WiFiClient   wifiClient;
PubSubClient mqtt(wifiClient);

// ==================== 温湿度传感器（以 DHT11 为例） ====================
#include <DHT.h>
#define DHTPIN  4
#define DHTTYPE DHT11
DHT dht(DHTPIN, DHTTYPE);

// ==================== 定时器 ====================
unsigned long lastPublish   = 0;
unsigned long lastHeartbeat = 0;
const long PUBLISH_INTERVAL  = 10000;   // 每 10 秒上报一次数据
const long HEARTBEAT_INTERVAL = 30000;  // 每 30 秒发送一次心跳

// ==================== WiFi 连接 ====================
void setupWiFi() {
  Serial.printf("连接 WiFi: %s\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 30) {
    delay(500);
    Serial.print(".");
    retry++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("\nWiFi 已连接，IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
    Serial.println("\nWiFi 连接失败！");
    ESP.restart();
  }
}

// ==================== MQTT 回调 ====================
// 收到下行命令后：解析 → 执行 → 回执到 reply 主题
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String body;
  for (unsigned int i = 0; i < length; i++) {
    body += (char)payload[i];
  }
  Serial.printf("收到命令 [%s] %s\n", topic, body.c_str());

  // 命令主题：device/{DEVICE_KEY}/cmd/down
  if (String(topic) != topicCmdDown) {
    return;
  }

  // id 是请求 / 应答的关联键，回执必须原样带回
  // 生产环境建议用 ArduinoJson 解析；此处用最简字符串截取演示
  int idStart = body.indexOf("\"id\":\"");
  String commandId = "";
  if (idStart >= 0) {
    idStart += 6;
    int idEnd = body.indexOf('"', idStart);
    commandId = body.substring(idStart, idEnd);
  }

  // 执行命令（此处按业务实现，示例仅回执成功）
  // ... 根据 method / params 操作设备 ...

  // 回执：id 关联命令，code=200 表示成功，data 为可选返回数据
  String reply = String("{\"id\":\"") + commandId +
                 "\",\"version\":\"1.0\",\"method\":\"thing.service.property.set.reply\"," +
                 "\"code\":200,\"data\":{}}";
  mqtt.publish(topicReply.c_str(), reply.c_str(), 1);
  Serial.printf("已回执 [%s] %s\n", topicReply.c_str(), reply.c_str());
}

// ==================== MQTT 重连 ====================
bool mqttReconnect() {
  while (!mqtt.connected()) {
    Serial.println("尝试连接 MQTT Broker...");

    // 设置遗言（LWT）：断开时自动发布 offline 状态
    mqtt.setBufferSize(256);
    mqtt.connect(
      clientId.c_str(),
      MQTT_USER, MQTT_PASS,
      topicLwt.c_str(),   // will topic
      1,                   // will qos
      false,               // will retain
      "offline"            // will payload
    );

    if (mqtt.connected()) {
      Serial.println("MQTT 连接成功！");

      // 订阅命令下发主题（T-15）：平台下发的属性设置 / 服务调用都走这一条
      mqtt.subscribe(topicCmdDown.c_str(), 1);

      // 发布在线状态
      mqtt.publish(topicHbt.c_str(), "online", true);
      return true;
    }

    Serial.printf("连接失败，rc=%d，5秒后重试...\n", mqtt.state());
    delay(5000);
  }
  return true;
}

// ==================== 发布传感器数据 ====================
void publishSensorData() {
  float temperature = dht.readTemperature();
  float humidity    = dht.readHumidity();

  if (isnan(temperature) || isnan(humidity)) {
    Serial.println("DHT11 读取失败！");
    return;
  }

  // 构造 JSON 载荷
  char payload[128];
  snprintf(payload, sizeof(payload),
    "{\"temperature\":%.1f,\"humidity\":%.1f,\"device_key\":\"%s\"}",
    temperature, humidity, DEVICE_KEY
  );

  // 发布消息
  if (mqtt.publish(topicData.c_str(), payload, 1)) {
    Serial.printf("数据已发布 [%s] -> %s\n", topicData.c_str(), payload);
  } else {
    Serial.println("发布失败！");
  }
}

// ==================== 发送心跳 ====================
void publishHeartbeat() {
  if (mqtt.publish(topicHbt.c_str(), "online", 1)) {
    Serial.printf("心跳已发送 [%s]\n", topicHbt.c_str());
  }
}

// ==================== Setup ====================
void setup() {
  Serial.begin(115200);
  delay(1000);

  dht.begin();
  setupWiFi();

  mqtt.setServer(MQTT_SERVER, MQTT_PORT);
  mqtt.setCallback(mqttCallback);
  mqtt.setBufferSize(512);

  // 首次连接
  mqttReconnect();
}

// ==================== Loop ====================
void loop() {
  // 保持 MQTT 连接
  if (!mqtt.connected()) {
    mqttReconnect();
  }
  mqtt.loop();

  unsigned long now = millis();

  // 定时发布传感器数据
  if (now - lastPublish >= PUBLISH_INTERVAL) {
    lastPublish = now;
    publishSensorData();
  }

  // 定时发送心跳
  if (now - lastHeartbeat >= HEARTBEAT_INTERVAL) {
    lastHeartbeat = now;
    publishHeartbeat();
  }

  // 低功耗模式（可选）
  // delay(100);
}
```

### 4.4 凭据来源与校验规则

- MQTT 用户名固定为 `{productKey}.{deviceKey}`，密码为该设备的 `deviceSecret`
- 设备接入由 EMQX 回调后端 `/api/internal/emqx/auth` 校验：产品须为 `ENABLED`、设备须属于该产品且 `enabled=1`、密码哈希匹配
- 主题授权由 `/api/internal/emqx/acl` 裁决：设备只能发布/订阅自己 `device/{deviceKey}/**` 下的主题，且不能发布到自己的 `cmd/`（防止伪造下行指令）
- 双轨期（`ACCESS_CONTROL_ENFORCE_AUTH=false`）回调一律放行，存量设备仍可用老账号接入；
  全部设备刷机完成后须置为 `true`，操作见《存量设备凭据迁移手册》（`scripts/migrate-device-secrets.md`）
- 后端自身连接 Broker 使用平台账号：用户名固定 `PLATFORM`，密码为 `.env` 中的 `PLATFORM_SECRET`

---

## 5. Topic 规范

本平台使用以下 Topic 结构：

| Topic 格式 | 方向 | QoS | 说明 |
|-----------|------|-----|------|
| `device/{device_key}/data` | ESP32 → 后端 | 1 | 上报传感器数据 |
| `device/{device_key}/heartbeat` | ESP32 → 后端 | 1 | 心跳包，保持设备在线 |
| `device/{device_key}/lwt` | ESP32 → 后端 | 1 | 遗言主题，断线时自动发 offline |
| `device/{device_key}/cmd/down` | 后端 → ESP32 | 1 | 平台下发命令（属性设置 / 服务调用） |
| `device/{device_key}/reply` | ESP32 → 后端 | 1 | 命令回执，设备对 `cmd/down` 的应答 |

**规则：**
- `{device_key}` 必须与前端创建设备时的标识 **完全一致**
- 每个设备的 Topic 是独立的，互不干扰
- 多个 ESP32 可以连接到同一个 Broker，各自使用不同的 device_key
- 设备**只能**发布/订阅自己 `device/{device_key}/**` 下的主题；`cmd/` 前缀禁止设备发布（防伪造下行），回执因此走独立的 `reply` 主题（详见 §6.5）

---

## 6. 消息载荷格式

### 6.1 数据消息（`/data`）

```json
{
  "temperature": 25.3,
  "humidity": 62.1,
  "device_key": "sensor-livingroom-01"
}
```

载荷可以是任意 JSON，后端原样存储。建议包含 `device_key` 方便追溯。

### 6.2 心跳消息（`/heartbeat`）

```
online
```

### 6.3 遗言（`/lwt`）

断线时 EMQX 自动发送（Last Will and Testament）：
```
offline
```

后端收到 `lwt` 主题的 `offline` 消息后，自动将设备状态标记为 OFFLINE。

### 6.4 物模型 Alink JSON 上报（T-14）

为产品定义物模型（TSL：属性 / 事件 / 服务）后，设备按 **Alink JSON** 格式上报，云端会自动解析出**属性最新值**与**事件记录**（分别落 `device_property_latest` / `device_event_record`）。

**属性上报**（发到 `/data`；`params` 的键必须是物模型中已定义的属性标识符）：

```json
{
  "id": "123",
  "version": "1.0",
  "method": "thing.event.property.post",
  "params": {
    "temperature": 25.3,
    "humidity": 62
  }
}
```

**事件上报**（`method` 固定为 `thing.event.post`；`params.eventId` 为物模型中已定义的事件标识符，
`params.value` 为该事件的输出数据，原样以 JSON 文本落 `device_event_record.output_data`）：

```json
{
  "id": "124",
  "version": "1.0",
  "method": "thing.event.post",
  "params": {
    "eventId": "highTempAlarm",
    "value": {
      "value": 80
    }
  }
}
```

约定：

- **标识符**：属性 / 事件 / 服务的标识符须与物模型定义一致（`[a-zA-Z][a-zA-Z0-9_]{0,31}`）；未定义的标识符会被跳过并计数，**不影响消息落库**；
- **数据类型**：属性值须符合物模型声明的 `dataType`（`int`/`float`/`double`/`bool`/`text`/`date`/`enum`/`struct`/`array`），类型或范围不符的条目会被跳过；
- **兼容**：非 Alink 载荷（如 §6.1 的自由 JSON）仍原样落 `message` 表，**不丢消息**，只是不产生派生数据；
- **上报时间**：以云端接收时间为准；同一标识符的乱序旧包（时间早于库中最新值）不会覆盖最新值。

> 物模型由管理员在「产品管理 → 物模型」编辑器中定义（或导入 TSL JSON）；属性最新值与事件记录可在设备详情页查看。

### 6.5 命令下发与回执（T-15）

云端可对设备**下发命令**（属性设置 / 服务调用），设备收到后执行并**回执**，平台据此把命令状态从 `SENT` 流转到 `ACKED` / `FAILED`。设备需订阅 `cmd/down`，并在处理完成后向 `reply` 发布回执。

**下行：属性设置**（`device/{device_key}/cmd/down`）：

```json
{
  "id": "8f1c0b6e-...-a1",
  "version": "1.0",
  "method": "thing.service.property.set",
  "params": { "targetTemp": 26, "mode": "cool" }
}
```

**下行：服务调用**（同一主题，`method` 为 `thing.service.{服务标识符}`）：

```json
{
  "id": "8f1c0b6e-...-a1",
  "version": "1.0",
  "method": "thing.service.reboot",
  "params": { "delay": 3 }
}
```

**上行：回执**（`device/{device_key}/reply`）：

```json
{
  "id": "8f1c0b6e-...-a1",
  "version": "1.0",
  "method": "thing.service.property.set.reply",
  "code": 200,
  "data": { "targetTemp": 26 }
}
```

约定：

- **`id` 是关联键**：必须与下行命令的 `id` **完全一致**，平台据此把回执关联到命令记录；缺失或匹配不到记录时该回执被忽略并计数，**不影响其他消息**；
- **`code` 判定结果**：`200` 视为成功（`ACKED`），非 `200` 视为失败（`FAILED`），失败原因取 `code` 与 `data` 的文本；`code` 非数字按失败处理；
- **`data` 原样落库**：作为命令记录的 `result`，可空；
- **`method` 仅用于可读性**：关联只依赖 `id`，容忍设备回执 `method` 不精确；
- **幂等**：同一命令重复回执不会覆盖已到达的终态（只有 `PENDING` / `SENT` 的记录会被更新）；
- **QoS 1、不保留**：命令与回执均为 QoS 1；平台不使用保留消息，避免设备离线时残留旧命令。

> 命令参数会**先按物模型校验**再下发：属性设置要求属性 `accessMode=rw` 且值符合 `dataType`；服务调用要求 `identifier` 命中物模型服务、必填入参齐全。云端校验失败时命令不会发到设备，返回明确错误码（6201~6204）。
> 命令记录可在**设备详情页 → 命令记录**查看（状态着色、参数与回执 JSON 详情）；下发入口为**设备详情页 → 设备控制**面板。

### 6.6 设备影子与离线补发（T-16）

设备影子以 **`desired`（云端期望）/ `reported`（设备上报）/ `delta`（两者差异）** 三份状态描述设备，云端可对**当前离线**的设备设置期望值，待设备上线后**自动补发**——设备侧**无需任何改动**即可受益。

**设备侧需要做什么**：与 §6.5 完全一致，仍只需订阅 `cmd/down` 并在执行后向 `reply` 回执、按 §6.4 上报属性。补发命令与实时命令**格式完全相同**（`thing.service.property.set`），设备无需区分。

**云端行为**（供理解，设备侧不必处理）：

- 对离线设备下发属性设置 → 命令置 `QUEUED`（前端显示「待补发」），期望值写入 `desired`，`delta` 记录尚未追平的键；
- 设备上线（发送 `/data` 或 `/heartbeat`）→ 平台自动把 `QUEUED` 命令按 `cmd/down` 补发，命令流转为 `SENT`；失败则按指数退避周期重试，次数耗尽置 `FAILED`；
- 设备执行并上报该属性 → 影子 `reported` 更新，`delta` 中该键**自动清空**（收敛）；
- **服务调用（`service`）对离线设备不排队**（语义是「此刻执行」），仍按 T-15 行为处理。

> 影子可在**设备详情页 → 设备影子**面板查看（三列对照 desired / reported / delta，delta 高亮，支持一键按 delta 补发与设置期望值）。设备只要正常上报属性，`delta` 就会自然收敛。

---

## 7. 本地测试步骤

### 7.1 确认服务运行

```
后端 API：    http://localhost:8080/api/health
前端页面：    http://localhost:3000
MQTT Broker：localhost:1883
```

### 7.2 创建设备

1. 打开 http://localhost:3000，登录（admin / admin123）
2. 点击左侧「设备管理」→「创建设备」
3. 填写：
   - 所属产品：`esp32-fall`（若无产品，先用 admin 调 `POST /api/products` 创建）
   - 设备名称：客厅温湿度
   - 设备标识：`sensor-livingroom-01`
   - 设备类型：sensor
   - Topic：`device/sensor-livingroom-01/data`
4. 点击「创建设备」，弹窗会显示一次性凭据
5. 复制保存 `MQTT 用户名` 与 `设备密钥`（关闭弹窗后无法再次查看）

### 7.3 烧录 ESP32

1. 将第 4 节代码中的 WiFi 和 MQTT_SERVER 修改为你实际的网络 IP
2. 将 `DEVICE_KEY` 改为与前端一致的 `sensor-livingroom-01`
3. 将 `MQTT_USER` / `MQTT_PASS` 改为上一步保存的凭据
4. 编译烧录到 ESP32
5. 打开串口监视器（115200 波特率），观察连接日志

### 7.4 验证数据

1. 回到前端 http://localhost:3000/dashboard
2. 查看「设备总数」和「设备状态分布」图表
3. 查看「最近消息」表格，应能看到 ESP32 上报的数据

---

## 8. 部署到服务器

### 8.1 服务器环境要求

推荐直接用仓库内的 Docker Compose 起栈（MySQL / Redis / EMQX / 后端 / 前端 一体），无需手工安装 Broker。

| 软件 | 最低版本 | 说明 |
|------|---------|------|
| Docker + Docker Compose | 24+ / v2 | 一键起栈（推荐） |
| Java | 17+ | 非容器方式运行后端时 |
| MySQL | 8.0+ | 存储数据（或使用 compose 内置） |
| Redis | 7+ | 缓存（或使用 compose 内置） |
| EMQX | 5.x | MQTT Broker（或使用 compose 内置） |
| Node.js | 18+ | 前端（可选，可 Nginx 托管构建产物） |
| 防火墙 | — | 开放 1883（MQTT）、8080（API）、80/443（前端） |

### 8.2 服务器 MQTT Broker（EMQX）

本项目统一使用 Docker Compose 部署 **EMQX 5**（`docker/docker-compose.yml` 中的 `emqx` 服务），不再使用 Mosquitto。默认暴露端口：

| 端口 | 用途 |
|------|------|
| 1883 | MQTT over TCP（设备连接） |
| 8083 | MQTT over WebSocket（前端实时消息） |
| 18083 | EMQX Dashboard（管理后台，初始账号 `admin` / `public`） |

> **重要**：EMQX 5 默认**不启用认证**（允许匿名接入），但本项目的认证与授权由 `emqx-init` 服务自动下发：
> 它通过 EMQX REST API 配置 HTTP 认证源（回调后端 `/api/internal/emqx/auth`）与 HTTP 授权源
> （回调 `/api/internal/emqx/acl`），并把未匹配时的默认策略设为 `deny`。
>
> - 后端自身连接 Broker 使用平台账号：用户名固定 `PLATFORM`，密码为 `.env` 的 `PLATFORM_SECRET`
> - 设备使用一机一密账号：`{productKey}.{deviceKey}` / `deviceSecret`
> - 迁移期由 `.env` 的 `ACCESS_CONTROL_ENFORCE_AUTH=false` 放行全部回调，切换步骤见《存量设备凭据迁移手册》
> - 确认回调已生效：`docker logs mqtt-emqx-init` 应输出「EMQX 认证与授权配置完成」

启动 / 查看 EMQX：

```bash
cd docker
docker compose up -d emqx
docker compose logs -f emqx
```

### 8.3 后端配置

修改 `application-dev.yml` 中的 MQTT 地址：

```yaml
spring:
  mqtt:
    host: tcp://localhost:1883       # 服务器上就是 localhost
```

### 8.4 ESP32 代码修改

部署到服务器后，只需修改一行：

```cpp
// 本地测试
// const char* MQTT_SERVER = "192.168.1.57";

// 部署到服务器后
const char* MQTT_SERVER = "你的服务器公网IP";  // 如 123.45.67.89
// 或
const char* MQTT_SERVER = "mqtt.yourdomain.com";  // 如有域名
```

不需要修改其他任何代码。

### 8.5 防火墙规则

```bash
# UFW（Ubuntu）
sudo ufw allow 1883/tcp   # MQTT
sudo ufw allow 8080/tcp   # API
sudo ufw allow 80/tcp     # 前端
sudo ufw allow 443/tcp    # 前端 HTTPS

# firewalld（CentOS）
sudo firewall-cmd --add-port=1883/tcp --permanent
sudo firewall-cmd --add-port=8080/tcp --permanent
sudo firewall-cmd --add-port=80/tcp --permanent
sudo firewall-cmd --reload
```

> EMQX Dashboard 的 18083 端口**仅用于管理，建议不要对公网开放**；如需远程访问，请通过内网或 SSH 隧道转发，或先在 Dashboard 修改初始密码。

### 8.6 可选：使用域名 + HTTPS

如需域名访问，推荐使用 Nginx 反代：

```nginx
server {
    listen 80;
    server_name mqtt.yourdomain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}

server {
    listen 8080;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 9. 常见问题排查

### ESP32 连接失败

| 检查项 | 方法 |
|--------|------|
| WiFi 是否连接 | 串口监视器查看 IP 地址 |
| Broker 地址是否正确 | 用电脑 `telnet <IP> 1883` 测试端口通断 |
| 用户名密码是否正确 | 用创建设备时返回的 `{productKey}.{deviceKey}` / `deviceSecret`；老账号 `admin`/`public` 仅在双轨期可用 |
| device_key 是否已注册 | 前端「设备管理」查看 |
| 防火墙是否放行 | 服务器 `ufw status` 或 `iptables -L` |

### 消息发出去但后端没收到

| 检查项 | 方法 |
|--------|------|
| Topic 是否匹配 | 确认格式 `device/{device_key}/data` |
| QoS 等级 | 建议用 QoS 1（至少一次） |
| 设备是否存在 | 前端查看设备列表 |
| 后端日志 | 查看后端控制台输出 |

### 本地测试 ESP32 连不上

| 检查项 | 方法 |
|--------|------|
| ESP32 和电脑是否同一 WiFi | 确认 |
| 电脑防火墙是否拦截 | 暂时关闭防火墙测试 |
| IP 地址是否正确 | `ipconfig` 查看，注意是 IPv4 |
| EMQX 是否监听 0.0.0.0 | compose 已将 `1883:1883` 映射到宿主全部接口，`docker compose ps` 确认端口已发布 |

---

## 10. 下一步可扩展功能

1. **OTA 固件升级** — 通过 MQTT 下发固件更新指令
2. **规则引擎** — 阈值告警（如温度 > 40°C 触发告警）
3. **多用户隔离** — 每个用户只能查看自己的设备
4. **MQTT over TLS** — 生产环境使用 `mqtts://` 加密传输

> **设备影子（desired / reported / delta）与离线补发已于 T-16 落地**，设备侧无需改动即可享受「离线可下发、上线自动补发」（见 §6.6）。
