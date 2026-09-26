# 开发指南

## 快速开发环境搭建

### 方式一：使用Docker（推荐）

最简单的方式是使用Docker一键启动所有服务：

```bash
# Windows
start.bat

# Linux/Mac
chmod +x start.sh
./start.sh
```

### 方式二：本地开发

如果你想在本地开发和调试，可以分别启动各个服务。

#### 1. 启动PostgreSQL数据库

```bash
# 使用Docker启动PostgreSQL
docker run -d \
  --name fall_detection_db \
  -e POSTGRES_DB=fall_detection \
  -e POSTGRES_USER=fall_user \
  -e POSTGRES_PASSWORD=fall_password \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:15-alpine
```

#### 2. 启动后端服务

```bash
cd backend

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 运行数据库迁移
alembic upgrade head

# 启动开发服务器
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### 3. 启动前端服务

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端将在 http://localhost:5173 启动（Vite默认端口）

## ESP32开发

### 使用PlatformIO（推荐）

```bash
cd esp32_firmware

# 安装PlatformIO CLI
pip install platformio

# 编译
pio run

# 烧录
pio run -t upload

# 串口监控
pio device monitor
```

### 使用Arduino IDE

1. 安装Arduino IDE 2.x
2. 添加ESP32开发板支持：
   - 文件 > 首选项 > 附加开发板管理器网址
   - 添加：`https://dl.espressif.com/dl/package_esp32_index.json`
3. 工具 > 开发板 > 开发板管理器
   - 搜索并安装 "esp32"
4. 选择开发板：ESP32S3 Dev Module
5. 安装所需库：
   - 工具 > 管理库
   - 搜索并安装：
     - WebSocketsClient
     - ArduinoJson
6. 打开 `esp32_firmware/src/main.cpp`
7. 编译并上传

## 调试技巧

### 查看后端日志

```bash
# Docker模式
docker-compose logs -f backend

# 本地开发模式
# 后端会在终端输出日志
```

### 查看数据库数据

```bash
# 使用Docker
docker-compose exec postgres psql -U fall_user -d fall_detection

# SQL查询示例
SELECT COUNT(*) FROM motion_data;
SELECT * FROM fall_events ORDER BY detected_at DESC LIMIT 10;
SELECT device_id, COUNT(*) as count FROM motion_data GROUP BY device_id;
```

### 测试WebSocket连接

使用 websocat 工具测试：

```bash
# 安装websocat
# Windows
cargo install websocat
# Linux/Mac
brew install websocat

# 测试连接
echo '{"device_id":"test","timestamp":12345,"ax":0.1,"ay":0.2,"az":9.8,"gx":1.0,"gy":2.0,"gz":3.0}' | websocat ws://localhost:8000/ws/motion/test
```

### 测试API端点

```bash
# 获取统计信息
curl http://localhost:8000/api/stats

# 获取数据列表
curl http://localhost:8000/api/data?limit=10

# 导出CSV
curl http://localhost:8000/api/export -o data.csv

# 标注数据
curl -X POST http://localhost:8000/api/data/annotate \
  -H "Content-Type: application/json" \
  -d '{"start_id":1,"end_id":100,"is_fall":true,"fall_type":"forward","notes":"测试摔倒"}'
```

## 性能优化建议

### 数据库优化

1. **添加索引**（已包含在init.sql中）：
   - device_id
   - timestamp
   - created_at
   - 复合索引：(device_id, timestamp)

2. **定期清理旧数据**：
   ```sql
   -- 清理30天前的数据
   SELECT cleanup_old_data();
   ```

3. **使用连接池**：
   - SQLAlchemy已配置连接池
   - 调整 `pool_size` 和 `max_overflow` 参数

### 实时传输优化

1. **批量发送**：
   - ESP32每10个样本打包发送（100ms间隔）
   - 减少网络开销

2. **数据压缩**：
   - 可选gzip压缩JSON数据
   - 减少带宽占用

3. **采样率调节**：
   - 根据网络状况动态调整
   - 默认100Hz，可降至50Hz

### 前端优化

1. **数据采样**：
   - 显示时进行下采样
   - 避免渲染过多数据点

2. **Web Workers**：
   - 在Worker中处理数据
   - 避免阻塞主线程

3. **虚拟滚动**：
   - 大数据量表格使用虚拟滚动
   - 提高渲染性能

## 扩展开发

### 添加新的摔倒检测算法

在 `backend/app/services/fall_detection.py` 中添加：

```python
def detect_with_ml_model(self, data_window):
    """使用机器学习模型检测摔倒"""
    # 提取特征
    features = self.extract_features(data_window)

    # 使用模型预测
    prediction = self.model.predict(features)

    return prediction
```

### 支持多个ESP32设备

系统已支持多设备，只需：

1. 为每个ESP32设置不同的device_id
2. 在config.h中修改：
   ```cpp
   #define DEVICE_ID "ESP32_002"
   ```

### 添加实时报警功能

在 `backend/app/services/notification.py` 中添加：

```python
class NotificationService:
    async def send_alert(self, fall_event):
        """发送摔倒报警"""
        # 发送邮件
        await self.send_email(fall_event)

        # 发送钉钉通知
        await self.send_dingtalk(fall_event)

        # 发送短信
        await self.send_sms(fall_event)
```

## 常见问题

### Q: ESP32无法连接WiFi？

A:
1. 检查WiFi名称和密码是否正确
2. 确认ESP32在WiFi信号范围内
3. 查看串口日志获取详细错误信息
4. 尝试重启ESP32

### Q: WebSocket连接失败？

A:
1. 确认后端服务正在运行
2. 检查防火墙设置
3. 确认ESP32和电脑在同一网络
4. 检查config.h中的服务器IP地址是否正确

### Q: 数据库连接失败？

A:
1. 检查PostgreSQL服务是否启动
2. 确认数据库凭据是否正确
3. 检查端口5432是否被占用
4. 查看后端日志获取详细错误信息

### Q: 前端无法显示数据？

A:
1. 检查浏览器控制台是否有错误
2. 确认WebSocket连接是否正常
3. 清除浏览器缓存重试
4. 检查后端API是否正常响应

### Q: 数据量太大导致性能问题？

A:
1. 定期清理旧数据：`SELECT cleanup_old_data();`
2. 降低采样率（从100Hz降到50Hz）
3. 添加更多数据库索引
4. 使用数据分区

## 贡献指南

1. Fork项目
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add your feature'`
4. 推送到分支：`git push origin feature/your-feature`
5. 提交Pull Request

### 代码规范

- Python: 遵循PEP 8规范
- JavaScript/Vue: 使用ESLint + Prettier
- C++: 遵循Google C++ Style Guide

### 提交规范

使用语义化提交信息：
- `feat:` 新功能
- `fix:` 修复bug
- `docs:` 文档更新
- `style:` 代码格式调整
- `refactor:` 代码重构
- `test:` 测试相关
- `chore:` 构建/工具链更新
