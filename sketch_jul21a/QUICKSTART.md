# 快速开始指南

## 5分钟快速启动

### 步骤1：启动服务（1分钟）

```bash
# Windows用户
双击运行 start.bat

# Linux/Mac用户
chmod +x start.sh
./start.sh
```

等待服务启动完成，会显示：
```
===================================
服务启动完成！
===================================

访问地址：
  - 后端API: http://localhost:8000
  - API文档: http://localhost:8000/docs
  - 前端界面: http://localhost:3000
```

### 步骤2：验证服务（1分钟）

在浏览器中打开以下地址：

1. **后端API文档**：http://localhost:8000/docs
   - 应该看到Swagger UI界面
   - 可以测试API端点

2. **前端界面**：http://localhost:3000
   - 应该看到摔倒检测系统界面
   - 仪表板页面会显示统计信息（初始为0）

### 步骤3：测试数据（1分钟）

使用测试数据生成脚本：

```bash
cd scripts
pip install websockets  # 首次使用需要安装
python generate_test_data.py --count 500 --falls 3
```

这会生成500个数据点，包含3个摔倒事件。

### 步骤4：查看结果（1分钟）

1. **刷新前端页面**：http://localhost:3000
2. **查看仪表板**：应该看到数据统计更新
3. **波形页面**：查看实时波形（如果有新数据）
4. **标注页面**：查看和标注数据

### 步骤5：配置ESP32（1分钟）

编辑 `esp32_firmware/include/config.h`：

```cpp
// 修改为你的电脑IP地址
#define WS_SERVER_HOST      "192.168.1.100"  // 改为你的IP
```

获取电脑IP地址：
- Windows: `ipconfig` -> IPv4 地址
- Linux/Mac: `ifconfig` -> inet 地址

编译并烧录：
```bash
cd esp32_firmware
pio run -t upload
pio device monitor
```

## 完整功能体验

### 1. 实时数据监控

**前提**：ESP32已连接并发送数据

1. 打开前端：http://localhost:3000
2. 点击侧边栏"波形显示"
3. 实时查看6条数据曲线：
   - 红色：X轴加速度
   - 绿色：Y轴加速度
   - 蓝色：Z轴加速度
   - 橙色：X轴角速度
   - 青色：Y轴角速度
   - 紫色：Z轴角速度

### 2. 3D姿态查看

1. 点击侧边栏"3D视图"
2. 3D立方体会根据陀螺仪数据实时旋转
3. 可以拖动鼠标旋转视角
4. 右上角显示实时角度数值

### 3. 数据标注

1. 点击侧边栏"数据标注"
2. 查看数据表格
3. 选择数据范围（点击复选框）
4. 点击"标注选中数据"
5. 选择摔倒类型：
   - 前倒
   - 后倒
   - 左侧倒
   - 右侧倒
6. 添加备注（可选）
7. 点击"确认标注"

### 4. 数据导出

1. 点击侧边栏"数据导出"
2. 设置筛选条件：
   - 时间范围
   - 设备ID
   - 摔倒类型
   - 是否只导出摔倒数据
3. 点击"导出CSV"
4. 下载CSV文件，可用于机器学习训练

### 5. 摔倒事件查看

1. 在仪表板查看最近摔倒事件列表
2. 点击"摔倒事件"菜单查看所有事件
3. 可以确认或修改摔倒类型

## 常见使用场景

### 场景A：开发测试

**目标**：验证系统功能

```bash
# 1. 启动服务
docker-compose up -d

# 2. 生成测试数据
cd scripts
python generate_test_data.py --count 1000 --falls 5

# 3. 查看结果
# 打开 http://localhost:3000
# 查看仪表板统计
# 查看波形显示
# 标注数据
# 导出CSV
```

### 场景B：实际数据采集

**目标**：采集真实摔倒数据

1. **准备ESP32**
   - 连接MPU6500传感器
   - 配置WiFi和服务器地址
   - 烧录固件

2. **启动系统**
   ```bash
   docker-compose up -d
   ```

3. **佩戴设备**
   - 将ESP32+MPU6500固定在测试者腰部
   - 确保设备稳定，不会晃动

4. **数据采集**
   - 进行各种动作测试：
     - 正常行走
     - 快速奔跑
     - 前倒
     - 后倒
     - 侧倒
   - 系统自动采集和存储数据

5. **数据标注**
   - 打开前端标注页面
   - 根据实际情况标注摔倒事件
   - 添加详细备注

6. **数据导出**
   - 导出标注好的CSV数据
   - 用于机器学习模型训练

### 场景C：多人多设备

**目标**：同时采集多个设备的数据

1. **准备多个ESP32**
   - 为每个设备设置唯一ID：
     - ESP32_001
     - ESP32_002
     - ESP32_003

2. **配置设备**
   ```cpp
   // 设备1
   #define DEVICE_ID "ESP32_001"

   // 设备2
   #define DEVICE_ID "ESP32_002"

   // 设备3
   #define DEVICE_ID "ESP32_003"
   ```

3. **同时运行**
   - 所有设备连接到同一WiFi
   - 同时发送数据到服务器
   - 前端可按设备筛选数据

## 数据分析工作流

### 1. 数据采集

```bash
# 启动系统
docker-compose up -d

# 采集数据（通过ESP32或测试脚本）
# 等待采集足够的数据量
```

### 2. 数据标注

1. 打开前端标注页面
2. 逐条或批量标注数据
3. 确保标注准确性和一致性
4. 添加详细备注

### 3. 数据导出

```bash
# 通过前端导出
# 或使用API
curl "http://localhost:8000/api/export?is_fall=true" -o fall_data.csv
curl "http://localhost:8000/api/export?is_fall=false" -o normal_data.csv
```

### 4. 数据预处理

```python
import pandas as pd
import numpy as np

# 加载数据
df = pd.read_csv('motion_data.csv')

# 数据清洗
df = df.dropna()  # 删除缺失值
df = df[(df['ax'].abs() < 100)]  # 删除异常值

# 特征工程
df['accel_magnitude'] = np.sqrt(df['ax']**2 + df['ay']**2 + df['az']**2)
df['gyro_magnitude'] = np.sqrt(df['gx']**2 + df['gy']**2 + df['gz']**2)

# 保存处理后的数据
df.to_csv('processed_data.csv', index=False)
```

### 5. 模型训练

```python
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report
import joblib

# 准备数据
features = ['ax', 'ay', 'az', 'gx', 'gy', 'gz',
            'accel_magnitude', 'gyro_magnitude']
X = df[features]
y = df['is_fall']

# 划分训练测试集
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# 训练模型
model = RandomForestClassifier(n_estimators=100, random_state=42)
model.fit(X_train, y_train)

# 评估模型
y_pred = model.predict(X_test)
print(classification_report(y_test, y_pred))

# 保存模型
joblib.dump(model, 'fall_detection_model.pkl')
```

### 6. 模型部署

```python
# 在后端集成模型
import joblib

class FallDetector:
    def __init__(self):
        self.model = joblib.load('fall_detection_model.pkl')

    def predict(self, data):
        features = self.extract_features(data)
        return self.model.predict([features])[0]
```

## 性能优化技巧

### 1. 数据库优化

```sql
-- 定期清理旧数据
SELECT cleanup_old_data();

-- 分析查询性能
EXPLAIN ANALYZE SELECT * FROM motion_data
WHERE device_id = 'ESP32_001'
AND timestamp > 1704067200000;

-- 创建更多索引（如果需要）
CREATE INDEX idx_motion_data_fall_type
ON motion_data(fall_type)
WHERE is_fall = TRUE;
```

### 2. 网络优化

- 批量发送数据（已配置每10个样本）
- 使用gzip压缩（Nginx已配置）
- 优化采样率（根据需求调整）

### 3. 前端优化

- 数据采样显示（避免渲染过多点）
- 使用虚拟滚动（大数据量表格）
- 启用浏览器缓存

## 故障排除

### 快速诊断

```bash
# 1. 检查服务状态
docker-compose ps

# 2. 查看日志
docker-compose logs -f

# 3. 测试数据库
docker-compose exec postgres psql -U fall_user -d fall_detection -c "SELECT COUNT(*) FROM motion_data;"

# 4. 测试API
curl http://localhost:8000/api/stats

# 5. 测试前端
curl http://localhost:3000
```

### 常见问题速查

**问题**：ESP32无法连接WiFi
**解决**：
- 检查WiFi名称和密码
- 确认WiFi为2.4GHz
- 查看串口日志

**问题**：前端无法显示数据
**解决**：
- 检查浏览器控制台
- 确认WebSocket连接
- 清除浏览器缓存

**问题**：数据库连接失败
**解决**：
- 检查PostgreSQL服务
- 验证数据库凭据
- 查看后端日志

## 进阶功能

### 1. 自定义摔倒检测阈值

编辑 `backend/.env`：

```env
FALL_ACCEL_THRESHOLD=2.0  # 降低阈值，更灵敏
FALL_GYRO_THRESHOLD=250.0
FALL_WINDOW_SIZE=40  # 缩短检测窗口
```

### 2. 添加实时报警

```python
# backend/app/services/notification.py
import smtplib
from email.mime.text import MIMEText

async def send_alert(fall_event):
    msg = MIMEText(f'检测到摔倒！设备: {fall_event.device_id}')
    msg['Subject'] = '摔倒报警'
    msg['From'] = 'alert@example.com'
    msg['To'] = 'user@example.com'

    with smtplib.SMTP('smtp.example.com', 587) as server:
        server.send_message(msg)
```

### 3. 集成机器学习模型

参考 `docs/user-guide.md` 中的"集成机器学习模型"部分

## 获取帮助

1. **查看文档**
   - `README.md` - 项目概述
   - `docs/user-guide.md` - 详细使用指南
   - `docs/development.md` - 开发指南
   - `backend/API_DOCUMENTATION.md` - API文档

2. **查看日志**
   ```bash
   docker-compose logs -f backend
   ```

3. **社区支持**
   - 提交Issue
   - 查看Wiki
   - 联系开发者

## 下一步

完成快速开始后，建议：

1. **阅读完整文档**
   - 了解所有功能
   - 学习高级配置

2. **实际数据采集**
   - 准备ESP32硬件
   - 进行真实测试

3. **模型训练**
   - 积累足够数据
   - 训练摔倒检测模型

4. **系统优化**
   - 根据使用情况调整参数
   - 优化性能

5. **功能扩展**
   - 添加实时报警
   - 支持更多设备
   - 开发移动端应用

祝你使用愉快！
