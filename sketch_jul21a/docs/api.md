# 接口参考

后端基于 FastAPI，提供两类接口：

- **REST**：统一挂在 `/api` 前缀下（健康检查除外）。
- **WebSocket**：使用绝对路径 `/ws/...`，与设备写入、前端只读两条通道隔离。

服务默认监听 `8000`。交互式文档见 `http://localhost:8000/docs`（Swagger）与 `/redoc`。

- [通用约定](#通用约定)
- [健康检查](#健康检查)
- [数据查询](#数据查询)
- [设备信息](#设备信息)
- [统计](#统计)
- [数据导出](#数据导出)
- [区间标注](#区间标注)
- [WebSocket：设备上报](#websocket设备上报)
- [WebSocket：前端实时订阅](#websocket前端实时订阅)
- [数据模型](#数据模型)

## 通用约定

**时间参数**（`start_time` / `end_time`）为查询字符串形式的 ISO-8601 时间，例如
`2026-07-21T10:30:00`。未指定时区按服务本地时区解释。

**分页**：所有列表接口返回统一结构。

```json
{ "total": 1234, "limit": 100, "offset": 0, "data": [] }
```

`total` 是应用过滤条件后的总记录数，与 `limit` / `offset` 无关。

**错误**：业务错误返回 FastAPI 标准结构，HTTP 状态码见各接口说明。

```json
{ "detail": "Data not found" }
```

## 健康检查

### `GET /`

服务元信息。

```json
{ "status": "running", "service": "Fall Detection System", "version": "1.0.0" }
```

### `GET /health`

存活探针，供 Docker `HEALTHCHECK` 与启动脚本轮询使用。

```json
{ "status": "healthy" }
```

### `GET /ws/status`

WebSocket 连接概况。

```json
{
  "active_devices": ["ESP32_001"],
  "sender_connections": 1,
  "viewer_connections": 2
}
```

| 字段 | 说明 |
| --- | --- |
| `active_devices` | 当前有设备上报连接的 device_id 列表（已排序） |
| `sender_connections` | 设备侧连接总数 |
| `viewer_connections` | 前端订阅连接总数 |

## 数据查询

### `GET /api/data`

分页查询运动数据，按 `timestamp` 倒序。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | — | 按设备过滤 |
| `start_time` | datetime | — | `timestamp >= start_time` |
| `end_time` | datetime | — | `timestamp <= end_time` |
| `is_fall` | bool | — | 按摔倒标记过滤 |
| `limit` | int | 100 | 返回条数，最大 `10000` |
| `offset` | int | 0 | 偏移量，`>= 0` |

```bash
curl "http://localhost:8000/api/data?device_id=ESP32_001&is_fall=true&limit=20"
```

### `GET /api/data/{data_id}`

按主键获取单条记录。不存在时返回 `404`。

### `POST /api/data/annotate`

对**单条**记录人工修正摔倒判定结果。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `data_id` | int | 是 | 目标记录 ID |
| `is_fall` | bool | 否 | 人工覆盖摔倒标记 |
| `fall_type` | string | 否 | 摔倒类型，最长 50 |
| `notes` | string | 否 | 备注 |

仅传入的字段会被更新，未传字段保持原值。记录不存在返回 `404`。

```bash
curl -X POST http://localhost:8000/api/data/annotate \
  -H "Content-Type: application/json" \
  -d '{"data_id": 1024, "is_fall": true, "fall_type": "forward", "notes": "前倒测试 #1"}'
```

```json
{ "status": "annotated", "data": { "id": 1024, "is_fall": true, "fall_type": "forward" } }
```

### `GET /api/events`

分页查询摔倒事件（`fall_events` 表），按 `detected_at` 倒序。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | — | 按设备过滤 |
| `start_time` | datetime | — | `detected_at >= start_time` |
| `end_time` | datetime | — | `detected_at <= end_time` |
| `limit` | int | 50 | 返回条数，最大 `1000` |
| `offset` | int | 0 | 偏移量 |

> 注意路径是 `/api/events`（复数，无 `fall-` 前缀）。

## 设备信息

### `GET /api/device/info`

从已存数据与实时连接注册表推导单台设备的元信息。

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | 是 | 长度 1–50 |

该设备从未上报过数据时返回 `404`。

```bash
curl "http://localhost:8000/api/device/info?device_id=ESP32_001"
```

```json
{
  "device_id": "ESP32_001",
  "total_records": 51230,
  "fall_records": 12,
  "first_seen": "2026-07-21T02:10:00",
  "last_seen": "2026-07-21T09:45:12",
  "last_fall_at": "2026-07-21T08:31:07",
  "online": true
}
```

`online` 取自 WebSocket 注册表，表示该设备此刻是否有上报连接（而非“最近有数据”）。

## 统计

### `GET /api/stats`

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `device_id` | string | 可选，按设备过滤 |
| `start_time` | datetime | 可选 |
| `end_time` | datetime | 可选 |

```json
{
  "total_records": 100000,
  "total_falls": 50,
  "total_fall_events": 48,
  "unique_devices": 2,
  "fall_rate_percent": 0.05,
  "recent_falls_24h": 3,
  "query_params": { "device_id": null, "start_time": null, "end_time": null }
}
```

| 字段 | 说明 |
| --- | --- |
| `total_records` | 应用过滤后的运动数据条数 |
| `total_falls` | 被标记 `is_fall=true` 的**样本**数 |
| `total_fall_events` | `fall_events` 表中的**事件**数（受设备/时间过滤） |
| `unique_devices` | 过滤范围内出现过的设备数 |
| `fall_rate_percent` | `total_falls / total_records × 100`，保留两位小数 |
| `recent_falls_24h` | 最近 24 小时的摔倒样本数（`device_id` 生效，时间过滤不生效） |

> `total_falls`（样本）通常大于 `total_fall_events`（事件）：检测算法对同一次撞击只产生一个事件，但撞击窗口内可能有多个样本被标记。

## 数据导出

### `GET /api/export`

导出为 CSV（`text/csv`），按 `timestamp` 倒序，附带
`Content-Disposition: attachment; filename=motion_data_<YYYYmmdd_HHMMSS>.csv`。

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | — | 按设备过滤 |
| `start_time` | datetime | — | 起始时间 |
| `end_time` | datetime | — | 结束时间 |
| `include_falls_only` | bool | false | 仅导出摔倒样本 |

导出行固定为：`id, device_id, timestamp, ax, ay, az, gx, gy, gz, is_fall, fall_type, confidence, notes`。
注意 CSV 不包含 `created_at`，而 REST 单条记录会返回它。

```bash
curl "http://localhost:8000/api/export?device_id=ESP32_001&include_falls_only=true" -o falls.csv
```

## 区间标注

面向“在波形上框选一段时间范围并打标签”的场景，独立于单条样本的
`/api/data/annotate`。所有路径以 `/api/annotations` 开头。

### `GET /api/annotations`

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | — | 按设备过滤 |
| `type` | string | — | 按标注类型精确匹配 |
| `start_time` | datetime | — | `start_time >= start_time`（区间起点下界） |
| `end_time` | datetime | — | `start_time <= end_time`（区间起点上界） |
| `limit` | int | 100 | 返回条数，范围 1–1000 |
| `offset` | int | 0 | 偏移量 |

按 `created_at` 倒序，返回分页结构（`data` 为标注对象数组）。

> 过滤比较的是标注**区间的起点** `Annotation.start_time`，而非区间终点。

### `POST /api/annotations`

创建标注，成功返回 `201`。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `type` | string | 是 | 长度 1–50，如 `forward_fall` / `normal_walk` |
| `start_time` | datetime | 是 | 区间起点 |
| `end_time` | datetime | 是 | 区间终点，必须 `>= start_time` |
| `device_id` | string | 否 | 最长 50 |
| `data_count` | int | 否 | 默认 `0`，`>= 0` |
| `confidence` | int | 否 | 人工置信度 0–100 |
| `quality` | int | 否 | 数据质量 1–5 |
| `tags` | string[] | 否 | 默认 `[]`，落库时序列化为 JSON 文本 |
| `notes` | string | 否 | 备注 |

`end_time < start_time` 时返回 `422`。

```bash
curl -X POST http://localhost:8000/api/annotations \
  -H "Content-Type: application/json" \
  -d '{"device_id":"ESP32_001","type":"forward_fall",
       "start_time":"2026-07-21T08:30:00","end_time":"2026-07-21T08:30:02",
       "data_count":200,"confidence":95,"quality":4,
       "tags":["indoor","hard-floor"],"notes":"前倒，硬地面"}'
```

### `GET /api/annotations/{annotation_id}`

按主键获取，不存在返回 `404`。

### `PUT /api/annotations/{annotation_id}`

部分更新，仅提交需要修改的字段（同 `POST` 的字段集合，全部可选）。
更新后若 `end_time < start_time` 返回 `422`。

### `DELETE /api/annotations/{annotation_id}`

删除成功返回 `204`（无响应体），不存在返回 `404`。

## WebSocket：设备上报

### `ws://<host>:8000/ws/motion/{device_id}`

设备（ESP32）写通道。服务端逐帧处理并落库，随后向该设备的订阅者广播。

**请求帧**：单个样本对象，或样本数组（固件每 10 个样本打包一次）。

```json
[
  { "timestamp": 123456, "ax": 0.12, "ay": 0.05, "az": 9.81, "gx": 1.23, "gy": -0.45, "gz": 0.67 },
  { "timestamp": 123466, "ax": 0.11, "ay": 0.06, "az": 9.79, "gx": 1.20, "gy": -0.44, "gz": 0.65 }
]
```

**响应帧**：每处理完一帧回一个确认。

```json
{ "status": "ok", "count": 10, "id": 51230, "is_fall": false, "timestamp": "2026-07-21T09:45:12.345678" }
```

| 字段 | 说明 |
| --- | --- |
| `count` | 本帧成功入库的样本数 |
| `id` | 本帧最后一条记录的数据库主键（固件可用它校验连续性） |
| `is_fall` | 本帧内是否含摔倒样本 |

JSON 解析失败或载荷无法识别时回 `{"status": "error", "message": "..."}`，**连接保持**，设备可继续发下一帧。

**timestamp 兼容规则**（`_parse_timestamp`）：

| 输入 | 解释 |
| --- | --- |
| ISO-8601 字符串 | 直接解析，`Z` 视为 UTC |
| 数值 `> 1e11` | Unix 毫秒 |
| 数值 `> 1e9` | Unix 秒 |
| 更小的数值 | 设备上电以来的毫秒计数（固件 `millis()`）。按设备首次上报时刻锚定到墙钟时间 |
| 缺失/其他 | 服务端当前 UTC 时间 |

设备断开后，该设备的时钟偏移与检测器状态会被清理。

## WebSocket：前端实时订阅

### `ws://<host>:8000/ws/view/{device_id}`

前端只读通道。连接建立后立即收到一条设备状态，之后被动接收推送。
该通道**不接受**客户端上报数据（收到的帧会被丢弃），因此前端无法注入样本。

**首帧：设备状态**

```json
{ "type": "device_info", "device_id": "ESP32_001", "online": true }
```

**推送：新数据**

```json
{
  "type": "sensor_data",
  "device_id": "ESP32_001",
  "data": [ { "id": 51230, "timestamp": "2026-07-21T09:45:12.345678", "ax": 0.12, "ay": 0.05, "az": 9.81, "gx": 1.23, "gy": -0.45, "gz": 0.67, "is_fall": false, "fall_type": null, "confidence": 0.0 } ]
}
```

`data` 是本批入库的完整样本（与 `GET /api/data` 的元素结构一致），而非单条。

**推送：摔倒事件**

```json
{
  "type": "fall_detected",
  "device_id": "ESP32_001",
  "fall_type": "forward",
  "confidence": 0.92,
  "timestamp": "2026-07-21T08:31:07.120000"
}
```

`fall_type` / `confidence` 由检测算法给出，可能为 `null` / `0`。当前算法不区分方向，
`fall_type` 固定为 `detected`；`forward` / `backward` / `lateral` 等具体方向来自人工标注
（`POST /api/data/annotate` 或区间标注）。

## 数据模型

### MotionData（`motion_data`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | int | 主键 |
| `device_id` | string(50) | 设备标识，索引 |
| `timestamp` | datetime | 采样时刻，索引 |
| `ax` `ay` `az` | float | 三轴加速度 |
| `gx` `gy` `gz` | float | 三轴角速度 |
| `created_at` | datetime | 入库时间（服务端） |
| `is_fall` | bool | 检测/人工标记 |
| `fall_type` | string(50) | 如 `forward` / `backward` / `lateral` |
| `confidence` | float | 检测置信度 0.0–1.0 |
| `notes` | text | 备注 |

复合索引 `(device_id, timestamp)` 与 `is_fall` 索引支撑常用查询。

### FallEvent（`fall_events`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | int | 主键 |
| `device_id` | string(50) | 设备标识 |
| `start_time` | datetime | 事件起点 |
| `end_time` | datetime | 事件终点（检测时按 +1s 估算） |
| `peak_acceleration` | float | 事件窗口内的加速度峰值 |
| `detected_at` | datetime | 检测时间（服务端） |
| `fall_type` | string(50) | 摔倒类型 |
| `confidence` | float | 置信度 |
| `notes` | text | 备注 |
| `is_confirmed` | bool | 是否人工确认 |

### Annotation（`annotations`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | int | 主键 |
| `device_id` | string(50) | 可为空 |
| `start_time` / `end_time` | datetime | 标注区间 |
| `data_count` | int | 区间内样本数 |
| `type` | string(50) | 标注类型 |
| `confidence` | int | 人工置信度 0–100 |
| `quality` | int | 质量 1–5 |
| `tags` | string[] | 标签列表（库内为 JSON 文本，接口返回数组） |
| `notes` | text | 备注 |
| `created_at` | datetime | 创建时间 |

### DeviceInfo

见 [`GET /api/device/info`](#get-apideviceinfo)。