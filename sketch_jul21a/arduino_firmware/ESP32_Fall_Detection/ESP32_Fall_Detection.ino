/**
 * ESP32-S3 测试上传固件 - Arduino IDE 版本
 *
 * 功能：每秒上传一个递增数字 (1~1000) 到 MQTT Broker
 * 通信：MQTT 3.1.1
 * 所需库：PubSubClient, ArduinoJson
 */

#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ===== WiFi =====
#define WIFI_SSID     "1302"
#define WIFI_PASSWORD "17305675843"

// ===== MQTT =====
#define MQTT_HOST     "192.168.1.100"
#define MQTT_PORT     1883
#define MQTT_USER     "admin"
#define MQTT_PASS     "public"
#define MQTT_TOPIC    "device/ESP32_001/data"

WiFiClient espClient;
PubSubClient mqtt(espClient);

// ===== 状态 =====
unsigned long counter = 1;
unsigned long lastUpload = 0;
const unsigned long UPLOAD_INTERVAL = 1000;

// ===== WiFi =====
void setupWiFi() {
  Serial.printf("[WiFi] 连接 %s ...\n", WIFI_SSID);
  WiFi.disconnect(true);
  delay(100);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    if (millis() - start > 20000) {
      Serial.println("\n[WiFi] 连接超时，重试中...");
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      start = millis();
    }
  }
  Serial.printf("\n[WiFi] 已连接! IP: %s\n", WiFi.localIP().toString().c_str());
}

// ===== MQTT =====
void mqttConnect() {
  while (!mqtt.connected()) {
    Serial.printf("[MQTT] 连接中... rc=%d\n", mqtt.state());
    String cid = "ESP32_001_" + String((uint32_t)ESP.getEfuseMac(), HEX);
    if (mqtt.connect(cid.c_str(), MQTT_USER, MQTT_PASS)) {
      Serial.println("[MQTT] 连接成功!");
    } else {
      Serial.println("[MQTT] 连接失败，5秒后重试");
      delay(5000);
    }
  }
}

// ===== 上传 =====
void uploadData() {
  if (!mqtt.connected()) return;

  JsonDocument doc;
  doc["device_id"] = "ESP32_001";
  doc["counter"] = counter;
  doc["timestamp"] = millis();

  String payload;
  serializeJson(doc, payload);

  if (mqtt.publish(MQTT_TOPIC, payload.c_str())) {
    Serial.printf("[OK] #%lu -> %s\n", counter, payload.c_str());
  } else {
    Serial.printf("[FAIL] #%lu\n", counter);
  }

  counter++;
  if (counter > 1000) counter = 1;
}

// ===== setup =====
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== ESP32-S3 数字上传测试 ===");
  Serial.printf("设备: ESP32_001\nBroker: %s:%d\n", MQTT_HOST, MQTT_PORT);

  setupWiFi();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqttConnect();
  Serial.println("=== 初始化完成，开始上传 ===\n");
}

// ===== loop =====
void loop() {
  if (!mqtt.connected()) {
    mqttConnect();
  }
  mqtt.loop();

  unsigned long now = millis();
  if (now - lastUpload >= UPLOAD_INTERVAL) {
    lastUpload = now;
    uploadData();
  }
}
