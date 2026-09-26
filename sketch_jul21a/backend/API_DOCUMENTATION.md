# 后端API详细文档

## 概述

后端服务基于FastAPI构建，提供WebSocket实时数据接收和RESTful API接口。

**基础URL**: `http://localhost:8000`

**API文档**: 
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## WebSocket接口

### 实时数据接收

**端点**: `ws://localhost:8000/ws/motion/{device_id}`

**参数**:
- `device_id` (路径参数): 设备标识符，如 `ESP32_001`

**数据格式**:

单个数据点:
```json
{
  "device_id": "ESP32_001",
  "timestamp": 1704067200000,
  "ax": 0.12,
  "ay": -0.05,
  "az": 9.81,
  "gx": 1.23,
  "gy": -0.45,
  "gz": 0.67
}
```

批量数据（数组）:
```json
[
  {
    "device_id": "ESP32_001",
    "timestamp": 1704067200000,
    "ax": 0.12,
    "ay": -0.05,
    "az": 9.81,
    "gx": 1.23,
    "gy": -0.45,
    "gz": 0.67
  },
  {
    "device_id": "ESP32_001",
    "timestamp": 1704067200010,
    "ax": 0.13,
    "ay": -0.04,
    "az": 9.82,
    "gx": 1.24,
    "gy": -0.44,
    "gz": 0.68
  }
]
```

**字段说明**:
- `device_id`: 设备标识符（字符串）
- `timestamp`: 时间戳，毫秒级（整数）
- `ax`: X轴加速度，单位g（浮点数）
- `ay`: Y轴加速度，单位g（浮点数）
- `az`: Z轴加速度，单位g（浮点数）
- `gx`: X轴角速度，单位°/s（浮点数）
- `gy`: Y轴角速度，单位°/s（浮点数）
- `gz`: Z轴角速度，单位°/s（浮点数）

**响应**:
连接成功后，服务器会返回确认消息：
```json
{
  "status": "connected",
  "device_id": "ESP32_001",
  "message": "WebSocket connection established"
}
```

### 连接状态查询

**端点**: `ws://localhost:8000/ws/status`

**说明**: 返回当前所有活跃的WebSocket连接信息

**响应示例**:
```json
{
  "active_connections": 2,
  "connections": [
    {
      "device_id": "ESP32_001",
      "connected_at": "2024-01-01T12:00:00",
      "last_message_at": "2024-01-01T12:05:00",
      "message_count": 5000
    },
    {
      "device_id": "ESP32_002",
      "connected_at": "2024-01-01T12:01:00",
      "last_message_at": "2024-01-01T12:05:00",
      "message_count": 4500
    }
  ]
}
```

## REST API接口

### 1. 获取数据列表

**端点**: `GET /api/data`

**参数**:
- `page` (可选, 默认1): 页码
- `page_size` (可选, 默认100): 每页数量
- `device_id` (可选): 设备ID筛选
- `start_time` (可选): 开始时间戳（毫秒）
- `end_time` (可选): 结束时间戳（毫秒）
- `is_fall` (可选): 是否为摔倒数据（true/false）

**请求示例**:
```bash
# 获取第1页，每页10条
curl "http://localhost:8000/api/data?page=1&page_size=10"

# 按设备筛选
curl "http://localhost:8000/api/data?device_id=ESP32_001"

# 按时间范围筛选
curl "http://localhost:8000/api/data?start_time=1704067200000&end_time=1704153600000"

# 只获取摔倒数据
curl "http://localhost:8000/api/data?is_fall=true"
```

**响应示例**:
```json
{
  "data": [
    {
      "id": 1,
      "device_id": "ESP32_001",
      "timestamp": 1704067200000,
      "ax": 0.12,
      "ay": -0.05,
      "az": 9.81,
      "gx": 1.23,
      "gy": -0.45,
      "gz": 0.67,
      "created_at": "2024-01-01T12:00:00",
      "is_fall": false,
      "fall_type": null,
      "confidence": null,
      "notes": null
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 10,
    "total_items": 10000,
    "total_pages": 1000
  }
}
```

### 2. 获取单条数据

**端点**: `GET /api/data/{id}`

**参数**:
- `id` (路径参数): 数据ID

**请求示例**:
```bash
curl "http://localhost:8000/api/data/123"
```

**响应示例**:
```json
{
  "id": 123,
  "device_id": "ESP32_001",
  "timestamp": 1704067200000,
  "ax": 0.12,
  "ay": -0.05,
  "az": 9.81,
  "gx": 1.23,
  "gy": -0.45,
  "gz": 0.67,
  "created_at": "2024-01-01T12:00:00",
  "is_fall": true,
  "fall_type": "forward",
  "confidence": 0.85,
  "notes": "前倒测试"
}
```

### 3. 标注数据

**端点**: `POST /api/data/annotate`

**请求体**:
```json
{
  "start_id": 1000,
  "end_id": 1200,
  "is_fall": true,
  "fall_type": "forward",
  "confidence": 0.9,
  "notes": "前倒测试 #1"
}
```

**字段说明**:
- `start_id`: 起始数据ID（包含）
- `end_id`: 结束数据ID（包含）
- `is_fall`: 是否为摔倒数据
- `fall_type` (可选): 摔倒类型
  - `"forward"`: 前倒
  - `"backward"`: 后倒
  - `"left_side"`: 左侧倒
  - `"right_side"`: 右侧倒
  - `"other"`: 其他
- `confidence` (可选): 置信度，0.0-1.0
- `notes` (可选): 备注说明

**请求示例**:
```bash
curl -X POST "http://localhost:8000/api/data/annotate" \
  -H "Content-Type: application/json" \
  -d '{
    "start_id": 1000,
    "end_id": 1200,
    "is_fall": true,
    "fall_type": "forward",
    "notes": "前倒测试"
  }'
```

**响应示例**:
```json
{
  "success": true,
  "message": "Successfully annotated 201 records",
  "annotated_count": 201
}
```

### 4. 获取摔倒事件

**端点**: `GET /api/fall-events`

**参数**:
- `page` (可选, 默认1): 页码
- `page_size` (可选, 默认20): 每页数量
- `device_id` (可选): 设备ID筛选
- `is_confirmed` (可选): 是否已确认（true/false）

**请求示例**:
```bash
# 获取所有摔倒事件
curl "http://localhost:8000/api/fall-events"

# 按设备筛选
curl "http://localhost:8000/api/fall-events?device_id=ESP32_001"

# 只获取已确认的事件
curl "http://localhost:8000/api/fall-events?is_confirmed=true"
```

**响应示例**:
```json
{
  "data": [
    {
      "id": 1,
      "device_id": "ESP32_001",
      "start_time": 1704067200000,
      "end_time": 1704067201000,
      "peak_acceleration": 3.5,
      "peak_angular_velocity": 350.0,
      "duration": 1000,
      "detected_at": "2024-01-01T12:00:00",
      "is_confirmed": false,
      "fall_type": null,
      "notes": null
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_items": 50,
    "total_pages": 3
  }
}
```

### 5. 获取统计信息

**端点**: `GET /api/stats`

**参数**:
- `device_id` (可选): 设备ID筛选

**请求示例**:
```bash
# 获取全局统计
curl "http://localhost:8000/api/stats"

# 获取特定设备统计
curl "http://localhost:8000/api/stats?device_id=ESP32_001"
```

**响应示例**:
```json
{
  "total_records": 100000,
  "fall_records": 50,
  "today_records": 5000,
  "active_devices": 2,
  "last_update": "2024-01-01T12:05:00",
  "device_stats": [
    {
      "device_id": "ESP32_001",
      "total_records": 60000,
      "fall_records": 30,
      "first_record": "2024-01-01T00:00:00",
      "last_record": "2024-01-01T12:05:00"
    },
    {
      "device_id": "ESP32_002",
      "total_records": 40000,
      "fall_records": 20,
      "first_record": "2024-01-01T01:00:00",
      "last_record": "2024-01-01T12:05:00"
    }
  ],
  "fall_stats": {
    "total_falls": 50,
    "confirmed_falls": 35,
    "first_fall": "2024-01-01T02:00:00",
    "last_fall": "2024-01-01T12:00:00"
  }
}
```

### 6. 导出CSV数据

**端点**: `GET /api/export`

**参数**:
- `start_time` (可选): 开始时间戳（毫秒）
- `end_time` (可选): 结束时间戳（毫秒）
- `device_id` (可选): 设备ID筛选
- `is_fall` (可选): 是否为摔倒数据（true/false）
- `fall_type` (可选): 摔倒类型筛选

**请求示例**:
```bash
# 导出所有数据
curl "http://localhost:8000/api/export" -o data.csv

# 按时间范围导出
curl "http://localhost:8000/api/export?start_time=1704067200000&end_time=1704153600000" \
  -o data_range.csv

# 只导出摔倒数据
curl "http://localhost:8000/api/export?is_fall=true" -o fall_data.csv

# 按设备和摔倒类型筛选
curl "http://localhost:8000/api/export?device_id=ESP32_001&fall_type=forward" \
  -o forward_falls.csv
```

**响应**:
- Content-Type: `text/csv`
- Content-Disposition: `attachment; filename=motion_data_export.csv`

**CSV格式**:
```csv
id,device_id,timestamp,ax,ay,az,gx,gy,gz,created_at,is_fall,fall_type,confidence,notes
1,ESP32_001,1704067200000,0.12,-0.05,9.81,1.23,-0.45,0.67,2024-01-01 12:00:00,false,,,,
2,ESP32_001,1704067200010,0.13,-0.04,9.82,1.24,-0.44,0.68,2024-01-01 12:00:00,false,,,,
...
1001,ESP32_001,1704067210000,2.50,-1.80,5.00,200.00,-150.00,100.00,2024-01-01 12:00:10,true,forward,0.85,前倒测试
```

## 错误处理

### 错误响应格式

```json
{
  "detail": "Error message here"
}
```

### 常见错误码

- **400 Bad Request**: 请求参数错误
- **404 Not Found**: 资源不存在
- **422 Unprocessable Entity**: 数据验证失败
- **500 Internal Server Error**: 服务器内部错误

### 错误示例

**请求不存在的数据**:
```bash
curl "http://localhost:8000/api/data/999999"
```

**响应**:
```json
{
  "detail": "Data not found"
}
```

**无效的标注请求**:
```bash
curl -X POST "http://localhost:8000/api/data/annotate" \
  -H "Content-Type: application/json" \
  -d '{"start_id": 100, "end_id": 50}'
```

**响应**:
```json
{
  "detail": "start_id must be less than or equal to end_id"
}
```

## 认证和授权

当前版本不包含认证机制，适合在本地网络使用。

如需添加认证，可以：

1. **API Key认证**:
   ```python
   # 在请求头中添加
   headers = {"X-API-Key": "your-api-key"}
   ```

2. **JWT Token认证**:
   ```python
   # 登录获取token
   response = requests.post("/api/login", json={"username": "user", "password": "pass"})
   token = response.json()["access_token"]

   # 使用token
   headers = {"Authorization": f"Bearer {token}"}
   ```

## 速率限制

当前版本没有速率限制，但建议：

- WebSocket连接：每设备1个连接
- REST API：每分钟最多1000个请求
- 批量操作：每次最多10000条记录

## 数据验证

### 输入验证

后端会自动验证输入数据：

- `ax, ay, az`: -100.0 到 100.0 g
- `gx, gy, gz`: -5000.0 到 5000.0 °/s
- `timestamp`: 必须为正整数
- `device_id`: 非空字符串，最大50字符

### 输出格式

所有时间字段格式：
- REST API: ISO 8601格式（`2024-01-01T12:00:00`）
- WebSocket: Unix时间戳（毫秒）
- CSV: `YYYY-MM-DD HH:MM:SS`

## 最佳实践

### WebSocket连接

1. **自动重连**:
   ```javascript
   function connectWebSocket() {
     const ws = new WebSocket('ws://localhost:8000/ws/motion/ESP32_001');

     ws.onclose = () => {
       setTimeout(connectWebSocket, 3000); // 3秒后重连
     };
   }
   ```

2. **心跳机制**:
   ```javascript
   // 每30秒发送ping
   setInterval(() => {
     if (ws.readyState === WebSocket.OPEN) {
       ws.send(JSON.stringify({ type: 'ping' }));
     }
   }, 30000);
   ```

3. **错误处理**:
   ```javascript
   ws.onerror = (error) => {
     console.error('WebSocket error:', error);
     // 实现重连逻辑
   };
   ```

### REST API调用

1. **分页获取大数据集**:
   ```python
   page = 1
   all_data = []

   while True:
       response = requests.get(f"http://localhost:8000/api/data?page={page}&page_size=1000")
       data = response.json()["data"]

       if not data:
           break

       all_data.extend(data)
       page += 1
   ```

2. **批量标注**:
   ```python
   # 分批标注，避免单次请求过大
   batch_size = 1000
   for i in range(0, len(data_ids), batch_size):
       batch = data_ids[i:i + batch_size]
       requests.post("http://localhost:8000/api/data/annotate", json={
           "start_id": batch[0],
           "end_id": batch[-1],
           "is_fall": True,
           "fall_type": "forward"
       })
   ```

3. **流式导出**:
   ```python
   # 流式下载大文件
   with requests.get("http://localhost:8000/api/export", stream=True) as r:
       with open("data.csv", "wb") as f:
           for chunk in r.iter_content(chunk_size=8192):
               f.write(chunk)
   ```

## 示例代码

### Python客户端

```python
import asyncio
import websockets
import json

async def connect_esp32():
    uri = "ws://localhost:8000/ws/motion/ESP32_001"

    async with websockets.connect(uri) as websocket:
        print(f"Connected to {uri}")

        while True:
            message = await websocket.recv()
            data = json.loads(message)

            if isinstance(data, list):
                print(f"Received batch of {len(data)} samples")
                for sample in data:
                    print(f"  ax={sample['ax']:.2f}, ay={sample['ay']:.2f}, az={sample['az']:.2f}")
            else:
                print(f"Received: ax={data['ax']:.2f}, ay={data['ay']:.2f}, az={data['az']:.2f}")

asyncio.run(connect_esp32())
```

### JavaScript客户端

```javascript
const ws = new WebSocket('ws://localhost:8000/ws/motion/ESP32_001');

ws.onopen = () => {
  console.log('Connected to WebSocket');
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);

  if (Array.isArray(data)) {
    console.log(`Received batch of ${data.length} samples`);
    data.forEach(sample => {
      console.log(`  ax=${sample.ax.toFixed(2)}, ay=${sample.ay.toFixed(2)}, az=${sample.az.toFixed(2)}`);
    });
  } else {
    console.log(`Received: ax=${data.ax.toFixed(2)}, ay=${data.ay.toFixed(2)}, az=${data.az.toFixed(2)}`);
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('WebSocket closed');
  // 实现重连逻辑
  setTimeout(() => {
    // 重新连接
  }, 3000);
};
```

### cURL示例集合

```bash
# 获取统计信息
curl http://localhost:8000/api/stats

# 获取最近10条数据
curl "http://localhost:8000/api/data?limit=10"

# 获取特定设备的摔倒数据
curl "http://localhost:8000/api/data?device_id=ESP32_001&is_fall=true"

# 标注数据范围
curl -X POST http://localhost:8000/api/data/annotate \
  -H "Content-Type: application/json" \
  -d '{
    "start_id": 1000,
    "end_id": 1200,
    "is_fall": true,
    "fall_type": "forward",
    "notes": "前倒测试 #1"
  }'

# 获取摔倒事件
curl "http://localhost:8000/api/fall-events?page_size=50"

# 导出CSV数据
curl "http://localhost:8000/api/export?start_time=1704067200000&end_time=1704153600000" \
  -o export.csv

# 健康检查
curl http://localhost:8000/health
```

## 性能优化建议

### 数据库优化

1. **索引**（已配置）:
   - device_id
   - timestamp
   - created_at
   - 复合索引

2. **查询优化**:
   - 使用分页避免一次返回过多数据
   - 添加时间范围限制
   - 使用合适的batch_size

3. **定期维护**:
   - 清理旧数据
   - 分析查询性能
   - 优化慢查询

### 网络优化

1. **WebSocket**:
   - 批量发送数据
   - 启用压缩
   - 使用心跳保活

2. **REST API**:
   - 启用gzip压缩
   - 使用CDN（生产环境）
   - 合理设置缓存头

## 监控和日志

### 查看日志

```bash
# 实时查看后端日志
docker-compose logs -f backend

# 查看特定时间段的日志
docker-compose logs --since 1h backend
```

### 健康检查

```bash
# 检查服务状态
curl http://localhost:8000/health
```

响应：
```json
{
  "status": "healthy",
  "database": "connected",
  "timestamp": "2024-01-01T12:00:00"
}
```

## 更新日志

### v1.0.0 (2024-01-01)
- 初始版本
- WebSocket实时数据接收
- REST API完整实现
- CSV导出功能
- 摔倒检测算法
