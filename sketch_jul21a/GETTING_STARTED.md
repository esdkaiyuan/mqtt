# 🚀 摔倒检测系统 - 3分钟快速开始

## 第1步：启动系统（1分钟）

### Windows
```bash
双击 setup_and_start.bat
```

### Linux/Mac
```bash
chmod +x setup_and_start.sh
./setup_and_start.sh
```

脚本会自动：
- ✅ 验证Docker环境
- ✅ 检查项目结构（75个文件）
- ✅ 创建配置文件
- ✅ 启动所有服务
- ✅ 验证服务状态

## 第2步：访问系统（30秒）

启动完成后，打开浏览器：

| 地址 | 功能 |
|------|------|
| http://localhost:3000 | 前端主界面 |
| http://localhost:3000/waveform | 实时波形 |
| http://localhost:3000/3d-view | 3D姿态 |
| http://localhost:3000/annotation | 数据标注 |
| http://localhost:3000/export | 数据导出 |
| http://localhost:3000/dashboard | 仪表板 |
| http://localhost:8000/docs | API文档 |

## 第3步：测试系统（30秒）

生成测试数据：
```bash
cd scripts
pip install websockets
python generate_test_data.py --count 500 --falls 3
```

然后：
1. 刷新前端页面
2. 查看仪表板统计
3. 查看波形显示
4. 尝试数据标注

## 第4步：配置ESP32（1分钟）

### 4.1 获取电脑IP地址

**Windows**:
```bash
ipconfig
```
查找 "IPv4 地址"（通常是 192.168.x.x）

**Linux/Mac**:
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```

### 4.2 修改ESP32配置

编辑 `esp32_firmware/include/config.h`：

```cpp
// WiFi配置
#define WIFI_SSID           "8202"
#define WIFI_PASSWORD       "88888888"

// 修改为你的电脑IP地址
#define WS_SERVER_HOST      "192.168.1.100"  // ← 修改这里
#define WS_SERVER_PORT      8000
```

### 4.3 编译和烧录

```bash
cd esp32_firmware

# 安装PlatformIO（如果没安装）
pip install platformio

# 编译
pio run

# 烧录
pio run -t upload

# 查看串口输出
pio device monitor
```

## 第5步：开始采集（即可开始）

1. 将ESP32+MPU6500佩戴在测试者腰部
2. ESP32会自动连接WiFi（8202）
3. ESP32会自动连接WebSocket服务器
4. LED指示灯：
   - 快闪（300ms）：正在连接
   - 常亮：连接成功
   - 极快闪（100ms）：数据发送中

5. 在前端查看实时数据：
   - 波形页面：查看6轴数据曲线
   - 3D页面：查看姿态旋转

## 常用操作

### 查看日志
```bash
docker-compose logs -f
```

### 停止系统
```bash
docker-compose down
```

### 重启系统
```bash
docker-compose restart
```

### 验证项目
```bash
./verify_project.sh
```

### 备份数据库
```bash
./scripts/backup.sh
```

## 核心功能使用

### 1. 实时波形显示
- **地址**: http://localhost:3000/waveform
- **功能**: 显示6条实时曲线
  - 红色：X轴加速度 (ax)
  - 绿色：Y轴加速度 (ay)
  - 蓝色：Z轴加速度 (az)
  - 橙色：X轴角速度 (gx)
  - 青色：Y轴角速度 (gy)
  - 紫色：Z轴角速度 (gz)
- **操作**: 鼠标悬停查看数值，滚轮缩放

### 2. 3D姿态可视化
- **地址**: http://localhost:3000/3d-view
- **功能**: 3D立方体根据陀螺仪数据实时旋转
- **操作**: 鼠标拖动旋转视角，滚轮缩放

### 3. 数据标注
- **地址**: http://localhost:3000/annotation
- **功能**: 标记摔倒事件
- **流程**:
  1. 选择数据范围
  2. 点击"标注选中数据"
  3. 选择摔倒类型（前倒/后倒/侧倒）
  4. 添加备注
  5. 确认标注

### 4. 数据导出
- **地址**: http://localhost:3000/export
- **功能**: 导出CSV数据用于模型训练
- **筛选**: 时间范围、设备、摔倒类型
- **格式**: CSV（可直接用pandas读取）

### 5. 仪表板
- **地址**: http://localhost:3000/dashboard
- **功能**: 查看统计信息
  - 总数据量
  - 摔倒事件数
  - 今日数据
  - 设备状态
  - 最近事件

## 数据采集工作流

### 完整流程
```
1. 启动系统
   ↓
2. 配置ESP32
   ↓
3. 烧录固件
   ↓
4. 佩戴设备
   ↓
5. 进行测试
   ├─ 正常行走
   ├─ 快速奔跑
   ├─ 前倒
   ├─ 后倒
   └─ 侧倒
   ↓
6. 系统自动检测摔倒
   ↓
7. 人工验证标注
   ↓
8. 导出CSV数据
   ↓
9. 训练机器学习模型
```

## 常见问题

### Q: ESP32无法连接WiFi？
**A**:
1. 检查WiFi名称和密码
2. 确认WiFi为2.4GHz（ESP32不支持5GHz）
3. 查看串口日志
4. 尝试重启ESP32

### Q: 前端无法显示数据？
**A**:
1. 检查浏览器控制台（F12）
2. 确认后端服务运行
3. 清除浏览器缓存
4. 重启前端服务

### Q: WebSocket连接失败？
**A**:
1. 确认后端服务运行
2. 检查防火墙设置
3. 验证IP地址配置
4. 检查端口8000

### Q: 数据库连接失败？
**A**:
1. 检查PostgreSQL服务
2. 验证数据库凭据
3. 查看后端日志
4. 重启数据库服务

## 技术栈

### 硬件
- ESP32-S3（双核Xtensa LX7）
- MPU6500（6轴IMU）
- WiFi 802.11 b/g/n

### 后端
- Python 3.11 + FastAPI
- PostgreSQL 15
- SQLAlchemy（异步）
- WebSocket实时通信

### 前端
- Vue 3 + Composition API
- Element Plus UI
- Chart.js（波形图）
- Three.js（3D可视化）

### 部署
- Docker + Docker Compose
- Nginx反向代理

## 项目文件结构

```
fall-detection-system/
├── esp32_firmware/           # ESP32固件（7个文件）
│   ├── src/main.cpp         # 主程序
│   ├── include/config.h     # 配置文件
│   └── platformio.ini       # PlatformIO配置
│
├── backend/                  # FastAPI后端（28个文件）
│   ├── app/main.py          # 应用入口
│   ├── app/models.py        # 数据库模型
│   └── requirements.txt     # Python依赖
│
├── frontend/                 # Vue3前端（29个文件）
│   ├── src/views/           # 页面（5个）
│   ├── src/components/      # 组件（6个）
│   └── package.json         # NPM配置
│
├── database/
│   └── init.sql             # 数据库初始化
│
├── scripts/
│   └── generate_test_data.py # 测试数据生成
│
├── docker-compose.yml        # Docker配置
├── setup_and_start.sh       # 一键启动脚本
└── README.md                # 项目文档
```

## 完整文档

### 用户文档
- **README.md** - 项目概述
- **QUICKSTART.md** - 快速开始
- **QUICK_REFERENCE.md** - 快速参考卡
- **docs/user-guide.md** - 用户手册

### 开发文档
- **docs/development.md** - 开发指南
- **backend/API_DOCUMENTATION.md** - API文档
- **esp32_firmware/CONFIGURATION.md** - ESP32配置

### 参考文档
- **FINAL_REPORT.md** - 项目报告
- **PROJECT_SUMMARY.md** - 项目总结
- **FILE_INDEX.md** - 文件清单

## 获取帮助

### 查看日志
```bash
# 所有服务
docker-compose logs -f

# 后端日志
docker-compose logs -f backend

# 前端日志
docker-compose logs -f frontend

# 数据库日志
docker-compose logs -f postgres
```

### 验证系统
```bash
./verify_project.sh
```

### 测试API
```bash
# 健康检查
curl http://localhost:8000/health

# 获取统计
curl http://localhost:8000/api/stats

# 获取数据
curl "http://localhost:8000/api/data?limit=10"
```

## 下一步

### 立即行动
1. ✅ 启动系统
2. ✅ 访问前端
3. ✅ 生成测试数据
4. ✅ 熟悉所有功能

### 短期目标（1-2周）
1. 配置ESP32硬件
2. 进行初步数据采集
3. 熟悉数据标注工作流
4. 了解API接口

### 中期目标（1-2月）
1. 积累训练数据（1000+摔倒事件）
2. 完成数据标注
3. 导出CSV数据
4. 训练摔倒检测模型

### 长期目标（3-6月）
1. 优化系统性能
2. 部署到生产环境
3. 添加实时报警
4. 开发移动端应用

## 快速命令参考

```bash
# 启动系统
./setup_and_start.sh

# 停止系统
docker-compose down

# 查看日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 生成测试数据
cd scripts && python generate_test_data.py --count 500 --falls 3

# 验证项目
./verify_project.sh

# 备份数据库
./scripts/backup.sh

# 进入数据库
docker-compose exec postgres psql -U fall_user -d fall_detection
```

## 立即开始

现在就开始使用摔倒检测系统吧！

```bash
# 1. 启动系统
./setup_and_start.sh

# 2. 访问前端
# 打开 http://localhost:3000

# 3. 生成测试数据
cd scripts
python generate_test_data.py --count 500 --falls 3

# 4. 开始探索！
```

**祝你使用愉快！** 🎉
