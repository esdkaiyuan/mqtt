# ESP32 接入 MQTT 云平台 - 接入手册

> 版本：v1.0 | 日期：2025-08-11

---

## 1. 接入前确认清单

| 项目 | 当前值 | 说明 |
|------|--------|------|
| MQTT Broker | Mosquitto | 运行在本地（或服务器 IP） |
| MQTT TCP 端口 | 1883 | 设备通过此端口连接 |
| MQTT 用户名 | `admin` | 全局统一认证账号 |
| MQTT 密码 | `public` | 全局统一认证密码 |
| 后端服务 | http://localhost:8080/api | 负责接收并持久化设备消息 |
| 前端管理页 | http://localhost:3000 | 创建设备、查看数据 |
| 设备注册方式 | 前端页面手动创建 | ESP32 须先在前端注册才能发消息 |

---

## 2. 接入流程（共 4 步）

```
Step 1: 在前端页面创建设备
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

**必填字段：**

| 字段 | 说明 | 示例 |
|------|------|------|
| 设备名称 | 自定义，方便识别 | 客厅温湿度传感器 |
| 设备标识（device_key） | **全局唯一**，ESP32 的 Client ID 和 Topic 都用它 | sensor-livingroom-01 |
| 设备类型 | sensor / gateway / actuator | sensor |
| MQTT Topic | 消息发布的 Topic 路径 | device/sensor-livingroom-01/data |
| 描述 | 选填 | 温湿度传感器，DHT11 |

创建成功后记下 `设备标识`（即 `device_key`），后续代码要用。

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
const char* MQTT_USER     = "admin";
const char* MQTT_PASS     = "public";
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
const char* MQTT_USER     = "admin";
const char* MQTT_PASS     = "public";

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
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  // 本示例设备只发布，不需要处理下行指令
  // 如果需要接收平台下发的指令，在这里处理
  Serial.printf("收到消息 [%s] ", topic);
  for (unsigned int i = 0; i < length; i++) {
    Serial.print((char)payload[i]);
  }
  Serial.println();
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

      // 订阅心跳响应主题（如果需要接收平台指令）
      // mqtt.subscribe((String("device/") + DEVICE_KEY + "/command").c_str());

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

---

## 5. Topic 规范

本平台使用以下 Topic 结构：

| Topic 格式 | 方向 | QoS | 说明 |
|-----------|------|-----|------|
| `device/{device_key}/data` | ESP32 → 后端 | 1 | 上报传感器数据 |
| `device/{device_key}/heartbeat` | ESP32 → 后端 | 1 | 心跳包，保持设备在线 |
| `device/{device_key}/lwt` | ESP32 → 后端 | 1 | 遗言主题，断线时自动发 offline |
| `device/{device_key}/command` | 后端 → ESP32 | 1 | 平台下发指令（可选） |

**规则：**
- `{device_key}` 必须与前端创建设备时的标识 **完全一致**
- 每个设备的 Topic 是独立的，互不干扰
- 多个 ESP32 可以连接到同一个 Broker，各自使用不同的 device_key

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

断线时 Mosquitto 自动发送：
```
offline
```

后端收到 `lwt` 主题的 `offline` 消息后，自动将设备状态标记为 OFFLINE。

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
   - 设备名称：客厅温湿度
   - 设备标识：`sensor-livingroom-01`
   - 设备类型：sensor
   - Topic：`device/sensor-livingroom-01/data`
4. 点击「创建设备」

### 7.3 烧录 ESP32

1. 将第 4 节代码中的 WiFi 和 MQTT_SERVER 修改为你实际的网络 IP
2. 将 `DEVICE_KEY` 改为与前端一致的 `sensor-livingroom-01`
3. 编译烧录到 ESP32
4. 打开串口监视器（115200 波特率），观察连接日志

### 7.4 验证数据

1. 回到前端 http://localhost:3000/dashboard
2. 查看「设备总数」和「设备状态分布」图表
3. 查看「最近消息」表格，应能看到 ESP32 上报的数据

---

## 8. 部署到服务器

### 8.1 服务器环境要求

| 软件 | 最低版本 | 说明 |
|------|---------|------|
| Java | 17+ | 运行后端 |
| MySQL | 8.0+ | 存储数据 |
| Redis | 7+ | 缓存 |
| Mosquitto | 2.0+ | MQTT Broker |
| Node.js | 18+ | 前端（可选，可 Nginx 托管构建产物） |
| 防火墙 | — | 开放 1883（MQTT）、8080（API）、80/443（前端） |

### 8.2 服务器 Mosquitto 配置

`/etc/mosquitto/mosquitto.conf`：

```conf
# 监听所有网络接口
listener 1883
allow_anonymous false
password_file /etc/mosquitto/pwfile

# 可选：开启 WebSocket 端口（用于前端实时消息监控）
listener 8083
protocol websockets
allow_anonymous false
password_file /etc/mosquitto/pwfile
```

生成密码文件：
```bash
# 添加用户
sudo mosquitto_passwd -c /etc/mosquitto/pwfile admin
# 输入密码：public
```

重启 Mosquitto：
```bash
sudo systemctl restart mosquitto
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
| 用户名密码是否正确 | 确认是 `admin` / `public` |
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
| Mosquitto 是否监听 0.0.0.0 | 当前配置 `listener 1883` 监听所有接口 |

---

## 10. 下一步可扩展功能

1. **OTA 固件升级** — 通过 MQTT 下发固件更新指令
2. **设备影子** — 缓存设备期望状态，上线后同步
3. **规则引擎** — 阈值告警（如温度 > 40°C 触发告警）
4. **多用户隔离** — 每个用户只能查看自己的设备
5. **MQTT over TLS** — 生产环境使用 `mqtts://` 加密传输
