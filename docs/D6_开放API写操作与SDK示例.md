# D6 · 开放 API 写操作与 SDK 示例（T-57）

> 版本：v1.0　创建日期：2026-10-26　状态：已交付
> 定位：T-57 把 `/external/v1` 从「只读 + 命令 / 影子」扩展为**可运营的写入口**。本文给出 curl 与最小 SDK 示例；完整端点参数以 Swagger / 文档站「开放 API」为准。

---

## 1. 认证与 scope

所有请求通过请求头 `X-API-Key` 认证：

```bash
curl -H "X-API-Key: your-api-key" https://your-server/api/external/v1/devices
```

| scope | 能力 |
|---|---|
| `device:read`（或旧别名 `data:read`） | 所有 GET 查询 |
| `device:write`（或旧别名 `data:write`） | **所有写操作**（设备 / 规则 / 场景）、命令下发、影子写入 |
| `stats:read` | 统计接口 |
| 空 / `*` | 全部权限 |

> 写操作统一要求 `device:write`；读取仍可只发 `device:read`。密钥只展示一次，请按最小权限分发。

---

## 2. 设备写操作

### 2.1 创建设备（一次性密钥）

```bash
curl -X POST https://your-server/api/external/v1/devices \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
        "productId": 3,
        "deviceKey": "sensor-002",
        "deviceName": "传感器-02",
        "deviceType": "sensor",
        "topic": "device/sensor-002/data",
        "description": "二号车间"
      }'
```

响应中的 `deviceSecret` 只返回一次：

```json
{
  "code": 200,
  "data": {
    "id": 12,
    "deviceKey": "sensor-002",
    "productKey": "env-sensor",
    "username": "env-sensor.sensor-002",
    "deviceSecret": "one-time-secret"
  }
}
```

### 2.2 更新 / 删除

```bash
curl -X PUT https://your-server/api/external/v1/devices/sensor-002 \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{"deviceName":"传感器-02-A"}'

curl -X DELETE https://your-server/api/external/v1/devices/sensor-002 \
  -H "X-API-Key: your-api-key"
```

非归属设备返回 `2003`。

---

## 3. 规则与场景写操作

```bash
# 创建规则（RuleRequest 与控制台同结构）
curl -X POST https://your-server/api/external/v1/rules \
  -H "X-API-Key: your-api-key" \
  -H "Content-Type: application/json" \
  -d @rule.json

# 启停规则
curl -X PUT "https://your-server/api/external/v1/rules/9/enabled?enabled=false" \
  -H "X-API-Key: your-api-key"

# 手动执行场景
curl -X POST https://your-server/api/external/v1/scenes/12/run \
  -H "X-API-Key: your-api-key"
```

错误码与控制台一致：规则 `6218` / `6219` / `6220` / `2003` / `6222`；场景 `6240` / `6241` / `6242` / `6244` / `6245` / `2003`。

---

## 4. 最小 SDK 示例

> 生产环境请自行封装重试、指数退避、429 `Retry-After`、密钥轮换与错误分类。以下示例刻意保持最小，便于对照端点。

### 4.1 JavaScript（fetch）

```js
export class MqttCloudClient {
  constructor(baseUrl, apiKey) {
    this.baseUrl = baseUrl.replace(/\/$/, '')
    this.apiKey = apiKey
  }

  async request(path, { method = 'GET', body, query } = {}) {
    const url = new URL(this.baseUrl + path)
    if (query) {
      for (const [key, value] of Object.entries(query)) {
        if (value !== undefined && value !== null) url.searchParams.set(key, String(value))
      }
    }
    const response = await fetch(url, {
      method,
      headers: {
        'X-API-Key': this.apiKey,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
    const payload = await response.json().catch(() => ({}))
    if (response.status === 429) {
      const retryAfter = Number(response.headers.get('Retry-After') || 60)
      throw new Error(`rate limited, retry after ${retryAfter}s`)
    }
    if (payload.code !== 200) throw new Error(`${payload.code}: ${payload.message}`)
    return payload.data
  }

  listDevices() {
    return this.request('/api/external/v1/devices')
  }

  createDevice(device) {
    return this.request('/api/external/v1/devices', { method: 'POST', body: device })
  }

  setRuleEnabled(id, enabled) {
    return this.request(`/api/external/v1/rules/${id}/enabled`, {
      method: 'PUT',
      query: { enabled }
    })
  }

  runScene(id) {
    return this.request(`/api/external/v1/scenes/${id}/run`, { method: 'POST' })
  }
}
```

### 4.2 Python（requests）

```python
import requests


class MqttCloudClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers["X-API-Key"] = api_key

    def request(self, path, method="GET", params=None, json=None):
        response = self.session.request(
            method, f"{self.base_url}{path}", params=params, json=json, timeout=10
        )
        if response.status_code == 429:
            retry_after = int(response.headers.get("Retry-After", "60"))
            raise RuntimeError(f"rate limited, retry after {retry_after}s")
        payload = response.json()
        if payload.get("code") != 200:
            raise RuntimeError(f"{payload.get('code')}: {payload.get('message')}")
        return payload.get("data")

    def list_devices(self):
        return self.request("/api/external/v1/devices")

    def create_device(self, device: dict):
        return self.request("/api/external/v1/devices", method="POST", json=device)

    def set_rule_enabled(self, rule_id: int, enabled: bool):
        return self.request(
            f"/api/external/v1/rules/{rule_id}/enabled",
            method="PUT",
            params={"enabled": str(enabled).lower()},
        )
```

---

## 5. 错误处理建议

| 场景 | 处置 |
|---|---|
| `403` | scope 不足或访问他人资源；确认 `device:write` 与归属 |
| `2003` | 设备不属于该密钥所属用户 |
| `429` + `Retry-After` | 按秒退避；开放接口按 API Key 限流，HTTP 上报按设备限流 |
| `400/409` 业务码 | 按错误码修正请求体；创建设备 `deviceKey` 重复为 `2001` |
| 网络异常 | 指数退避重试；**不要重试非幂等创建**，先按 `deviceKey` 查询确认 |

---

## 6. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：开放 API 写操作、scope 要求、curl / JS / Python 最小示例与错误处理。 |