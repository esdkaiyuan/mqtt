# 使用指南

## 系统概述

摔倒检测数据采集系统由三部分组成：

1. **ESP32-S3固件** - 采集MPU6500陀螺仪数据并通过WiFi发送
2. **FastAPI后端** - 接收数据、存储到数据库、提供API接口
3. **Vue3前端** - 实时波形显示、3D姿态可视化、数据标注和导出

## 快速开始

### 第一步：启动后端服务

#### Windows用户
```bash
双击运行 start.bat
```

#### Linux/Mac用户
```bash
chmod +x start.sh
./start.sh
```

服务启动后：
- 后端API：http://localhost:8000
- API文档：http://localhost:8000/docs
- 前端界面：http://localhost:3000

### 第二步：配置ESP32

1. 打开 `esp32_firmware/include/config.h`
2. 修改WiFi和服务器配置：
   ```cpp
   #define WIFI_SSID "8202"
   #define WIFI_PASSWORD "88888888"
   #define WS_SERVER "你的电脑IP地址"  # 例如：192.168.1.100
   ```
3. 使用PlatformIO或Arduino IDE编译并烧录固件

### 第三步：查看数据

1. 打开浏览器访问 http://localhost:3000
2. ESP32会自动连接并开始发送数据
3. 在波形页面查看实时数据
4. 在3D视图页面查看姿态变化

## 功能详解

### 1. 实时波形显示

**页面**：`/waveform`

功能：
- 显示6条实时波形曲线
  - 红色：X轴加速度 (ax)
  - 绿色：Y轴加速度 (ay)
  - 蓝色：Z轴加速度 (az)
  - 橙色：X轴角速度 (gx)
  - 青色：Y轴角速度 (gy)
  - 紫色：Z轴角速度 (gz)
- 10秒滚动窗口
- 实时数值显示
- 可缩放和拖动查看

使用技巧：
- 点击图例可隐藏/显示特定曲线
- 鼠标悬停可查看具体数值
- 滚轮可缩放时间轴

### 2. 3D姿态可视化

**页面**：`/3d-view`

功能：
- 3D立方体实时旋转
- 显示设备当前姿态
- 坐标轴参考线
- 实时角度数值显示

使用技巧：
- 鼠标拖动可旋转视角
- 滚轮可缩放
- 点击"重置视角"按钮恢复默认

### 3. 数据标注

**页面**：`/annotation`

功能：
- 选择时间范围
- 标记摔倒类型：
  - 前倒 (Forward)
  - 后倒 (Backward)
  - 左侧倒 (Left Side)
  - 右侧倒 (Right Side)
- 添加备注说明
- 批量标注操作

标注流程：
1. 在数据表格中选择数据范围
2. 点击"标注选中数据"
3. 选择摔倒类型
4. 添加备注（可选）
5. 点击"确认标注"

### 4. 数据导出

**页面**：`/export`

功能：
- 按时间范围筛选
- 按摔倒类型筛选
- 按设备筛选
- 导出CSV格式

导出格式：
```csv
timestamp,ax,ay,az,gx,gy,gz,is_fall,fall_type,notes
1234567890,0.12,-0.05,9.81,1.23,-0.45,0.67,true,forward,测试摔倒
```

用途：
- 用于机器学习模型训练
- 数据分析和可视化
- 存档备份

### 5. 仪表板

**页面**：`/dashboard`

功能：
- 实时设备连接状态
- 数据统计信息：
  - 总数据量
  - 摔倒事件数
  - 今日数据量
  - 活跃设备数
- 最近摔倒事件列表
- 快速操作入口

## 常见使用场景

### 场景1：采集摔倒数据用于训练

1. 启动系统
2. 将ESP32佩戴在测试者身上
3. 进行摔倒模拟测试
4. 系统自动检测并标记摔倒事件
5. 在标注页面验证和补充标注
6. 在导出页面下载标注好的CSV数据
7. 使用数据训练机器学习模型

### 场景2：长时间数据采集

1. 启动系统
2. 配置ESP32进入低功耗模式（可选）
3. 系统自动采集和存储数据
4. 定期查看统计信息
5. 定期导出数据备份
6. 使用清理脚本删除旧数据：
   ```bash
   docker-compose exec postgres psql -U fall_user -d fall_detection -c "SELECT cleanup_old_data();"
   ```

### 场景3：多设备同时采集

1. 准备多个ESP32设备
2. 为每个设备配置不同的device_id：
   - ESP32_001
   - ESP32_002
   - ESP32_003
3. 同时启动所有设备
4. 在前端可按设备筛选数据
5. 分析不同设备的数据差异

## API使用示例

### 获取统计信息

```bash
curl http://localhost:8000/api/stats
```

响应示例：
```json
{
  "total_records": 100000,
  "fall_records": 50,
  "today_records": 5000,
  "active_devices": 1,
  "last_update": "2024-01-01T12:00:00"
}
```

### 获取最近数据

```bash
curl "http://localhost:8000/api/data?limit=10&device_id=ESP32_001"
```

### 标注数据

```bash
curl -X POST http://localhost:8000/api/data/annotate \
  -H "Content-Type: application/json" \
  -d '{
    "start_id": 1000,
    "end_id": 1200,
    "is_fall": true,
    "fall_type": "forward",
    "notes": "前倒测试 #1"
  }'
```

### 导出CSV

```bash
curl "http://localhost:8000/api/export?start_time=1704067200000&end_time=1704153600000" \
  -o export.csv
```

## 数据库管理

### 查看数据

```bash
# 进入数据库命令行
docker-compose exec postgres psql -U fall_user -d fall_detection

# 查看表结构
\dt
\d motion_data
\d fall_events

# 查询数据
SELECT COUNT(*) FROM motion_data;
SELECT * FROM fall_events ORDER BY detected_at DESC LIMIT 10;

# 查看统计
SELECT * FROM data_stats;
SELECT * FROM fall_stats;
```

### 备份数据

```bash
# 使用备份脚本
chmod +x scripts/backup.sh
./scripts/backup.sh

# 手动备份
docker-compose exec postgres pg_dump -U fall_user -d fall_detection > backup.sql
```

### 恢复数据

```bash
# 使用恢复脚本
chmod +x scripts/restore.sh
./scripts/restore.sh ./backups/fall_detection_20240101_120000.sql.gz

# 手动恢复
docker-compose exec -T postgres psql -U fall_user -d fall_detection < backup.sql
```

## 测试和调试

### 生成测试数据

如果没有ESP32设备，可以使用测试数据生成脚本：

```bash
cd scripts
pip install websockets
python generate_test_data.py --count 1000 --falls 5
```

参数说明：
- `--count`：生成数据点数量（默认1000）
- `--falls`：生成摔倒事件数量（默认5）
- `--device`：设备ID（默认ESP32_001）
- `--server`：服务器地址（默认localhost:8000）

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 查看数据库日志
docker-compose logs -f postgres
```

### 测试WebSocket连接

使用websocat工具：

```bash
# 安装
pip install websocat  # 或 cargo install websocat

# 测试发送数据
echo '{"device_id":"test","timestamp":12345,"ax":0.1,"ay":0.2,"az":9.8,"gx":1.0,"gy":2.0,"gz":3.0}' | websocat ws://localhost:8000/ws/motion/test
```

## 性能优化建议

### 数据库优化

1. **索引优化**（已默认配置）
   - device_id索引
   - timestamp索引
   - 复合索引

2. **定期清理**
   - 默认保留30天数据
   - 可配置保留时间

3. **分区表**（大数据量时考虑）
   - 按时间分区
   - 按设备分区

### 网络优化

1. **批量发送**
   - ESP32每10个样本打包发送
   - 减少网络开销

2. **数据压缩**
   - 可选gzip压缩
   - 减少带宽占用

3. **采样率调节**
   - 默认100Hz
   - 可根据需求调整

### 前端优化

1. **数据采样**
   - 显示时进行下采样
   - 避免渲染过多点

2. **虚拟滚动**
   - 大数据量表格使用虚拟滚动
   - 提高渲染性能

## 故障排除

### 问题：ESP32无法连接WiFi

**解决方案**：
1. 检查WiFi名称和密码是否正确
2. 确认ESP32在WiFi信号范围内
3. 查看串口日志：
   ```
   pio device monitor
   ```
4. 尝试重启ESP32

### 问题：WebSocket连接失败

**解决方案**：
1. 确认后端服务正在运行：
   ```bash
   docker-compose ps
   ```
2. 检查防火墙设置
3. 确认ESP32和电脑在同一网络
4. 检查config.h中的服务器IP地址

### 问题：前端无法显示数据

**解决方案**：
1. 检查浏览器控制台（F12）是否有错误
2. 确认WebSocket连接正常
3. 清除浏览器缓存
4. 尝试使用其他浏览器

### 问题：数据库连接失败

**解决方案**：
1. 检查PostgreSQL服务状态：
   ```bash
   docker-compose ps postgres
   ```
2. 查看数据库日志：
   ```bash
   docker-compose logs postgres
   ```
3. 确认数据库凭据正确

### 问题：性能问题

**解决方案**：
1. 降低采样率（修改ESP32固件）
2. 清理旧数据：
   ```sql
   SELECT cleanup_old_data();
   ```
3. 增加数据库索引
4. 使用更快的存储（SSD）

## 进阶使用

### 自定义摔倒检测算法

编辑 `backend/app/services/fall_detection.py`：

```python
def custom_detect(self, data_window):
    """自定义摔倒检测算法"""

    # 1. 提取特征
    accel_magnitudes = [sqrt(d.ax**2 + d.ay**2 + d.az**2) for d in data_window]
    gyro_magnitudes = [sqrt(d.gx**2 + d.gy**2 + d.gz**2) for d in data_window]

    # 2. 计算统计值
    max_accel = max(accel_magnitudes)
    max_gyro = max(gyro_magnitudes)
    mean_accel = sum(accel_magnitudes) / len(accel_magnitudes)

    # 3. 判断是否摔倒
    if max_accel > self.threshold_acceleration and max_gyro > self.threshold_angular_velocity:
        return {
            'is_fall': True,
            'confidence': min(max_accel / 5.0, 1.0),
            'fall_type': 'detected'
        }

    return {'is_fall': False}
```

### 集成机器学习模型

1. 训练模型：
   ```python
   # 使用导出的CSV数据训练
   import pandas as pd
   from sklearn.ensemble import RandomForestClassifier
   from sklearn.model_selection import train_test_split

   # 加载数据
   df = pd.read_csv('export.csv')

   # 提取特征
   features = df[['ax', 'ay', 'az', 'gx', 'gy', 'gz']]
   labels = df['is_fall']

   # 训练模型
   X_train, X_test, y_train, y_test = train_test_split(features, labels)
   model = RandomForestClassifier()
   model.fit(X_train, y_train)

   # 保存模型
   import joblib
   joblib.dump(model, 'fall_detection_model.pkl')
   ```

2. 在后端集成模型：
   ```python
   import joblib

   class FallDetector:
       def __init__(self):
           self.model = joblib.load('fall_detection_model.pkl')

       def detect(self, data_window):
           features = self.extract_features(data_window)
           prediction = self.model.predict([features])
           return prediction[0] == 1
   ```

### 添加实时报警

1. 配置邮件通知：
   ```python
   # backend/app/services/notification.py
   import smtplib
   from email.mime.text import MIMEText

   async def send_email_alert(fall_event):
       msg = MIMEText(f'检测到摔倒事件！设备: {fall_event.device_id}')
       msg['Subject'] = '摔倒报警'
       msg['From'] = 'alert@example.com'
       msg['To'] = 'user@example.com'

       with smtplib.SMTP('smtp.example.com', 587) as server:
           server.send_message(msg)
   ```

2. 配置钉钉通知：
   ```python
   import requests

   async def send_dingtalk_alert(fall_event):
       webhook = 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
       data = {
           'msgtype': 'text',
           'text': {
               'content': f'摔倒报警！设备: {fall_event.device_id}'
           }
       }
       requests.post(webhook, json=data)
   ```

## 最佳实践

### 数据采集

1. **多样化数据**
   - 不同摔倒方向
   - 不同摔倒速度
   - 不同穿戴位置
   - 不同体型测试者

2. **数据标注**
   - 及时标注，避免遗忘
   - 详细记录摔倒类型
   - 添加环境信息备注

3. **数据质量**
   - 定期检查数据异常
   - 清理噪声数据
   - 验证标注准确性

### 模型训练

1. **数据预处理**
   - 标准化/归一化
   - 处理缺失值
   - 平滑噪声

2. **特征工程**
   - 时域特征：均值、方差、峰值
   - 频域特征：FFT系数
   - 统计特征：斜度、峰度

3. **模型选择**
   - 传统机器学习：SVM、Random Forest
   - 深度学习：LSTM、GRU、CNN
   - 集成方法：Stacking、Blending

4. **评估指标**
   - 准确率 (Accuracy)
   - 精确率 (Precision)
   - 召回率 (Recall)
   - F1分数
   - 混淆矩阵

## 技术支持

### 文档资源

- 后端API文档：http://localhost:8000/docs
- 开发指南：`docs/development.md`
- README：`README.md`

### 日志查看

```bash
# 实时查看日志
docker-compose logs -f

# 查看特定服务
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f postgres
```

### 常用命令

```bash
# 启动服务
docker-compose up -d

# 停止服务
docker-compose down

# 重启服务
docker-compose restart

# 查看状态
docker-compose ps

# 进入容器
docker-compose exec backend bash
docker-compose exec postgres psql -U fall_user -d fall_detection
```

## 更新和维护

### 更新系统

```bash
# 拉取最新代码
git pull

# 重新构建并重启
docker-compose down
docker-compose up -d --build
```

### 数据库迁移

```bash
# 创建迁移
cd backend
alembic revision --autogenerate -m "description"

# 执行迁移
alembic upgrade head
```

### 清理数据

```bash
# 清理30天前的数据
docker-compose exec postgres psql -U fall_user -d fall_detection -c "SELECT cleanup_old_data();"

# 清空所有数据（谨慎使用）
docker-compose exec postgres psql -U fall_user -d fall_detection -c "TRUNCATE motion_data, fall_events;"
```

## 许可证

MIT License

## 联系方式

如有问题或建议，请：
1. 查看文档
2. 搜索已知问题
3. 提交Issue
4. 联系开发者
