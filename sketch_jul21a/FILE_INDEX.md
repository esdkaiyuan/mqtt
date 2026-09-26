# 摔倒检测系统 - 完整文件清单

## 项目统计

- **总文件数**: 70个
- **代码文件**: 40个
- **配置文件**: 12个
- **文档文件**: 18个
- **项目状态**: ✅ 已完成，可立即使用

## 根目录文件 (13个)

### 核心配置
1. `docker-compose.yml` - Docker编排配置
2. `.env.example` - 环境配置示例
3. `.gitignore` - Git忽略文件

### 启动脚本
4. `start.sh` - Linux启动脚本
5. `start.bat` - Windows启动脚本
6. `setup_and_start.sh` - 一键启动脚本（Linux/Mac）
7. `setup_and_start.bat` - 一键启动脚本（Windows）
8. `verify_project.sh` - 项目验证脚本

### 文档
9. `README.md` - 项目说明
10. `QUICKSTART.md` - 快速开始指南
11. `QUICK_REFERENCE.md` - 快速参考卡
12. `FINAL_REPORT.md` - 项目报告
13. `CONFIGURATION_EXAMPLES.md` - 配置示例
14. `PROJECT_SUMMARY.md` - 项目总结
15. `DELIVERY_CHECKLIST.md` - 交付清单

---

## ESP32固件 (8个文件)

### `esp32_firmware/`

#### 配置文件
16. `platformio.ini` - PlatformIO项目配置
17. `include/config.h` - 集中配置参数（WiFi、服务器、采样率等）

#### 头文件
18. `include/mpu6500.h` - MPU6500驱动头文件
19. `include/websocket_client.h` - WebSocket客户端头文件

#### 源代码
20. `src/main.cpp` - 主程序（数据采集和发送逻辑）
21. `src/mpu6500.cpp` - MPU6500 I2C驱动实现

#### 文档
22. `CONFIGURATION.md` - ESP32配置说明文档

---

## FastAPI后端 (18个文件)

### `backend/`

#### 核心应用
23. `app/__init__.py` - 应用包初始化
24. `app/main.py` - FastAPI应用入口（CORS、生命周期）
25. `app/config.py` - 配置管理（pydantic-settings）
26. `app/database.py` - 异步数据库连接（SQLAlchemy）
27. `app/models.py` - 数据库模型（MotionData, FallEvent）

#### 路由
28. `app/routers/__init__.py` - 路由包初始化
29. `app/routers/api.py` - REST API路由（7个端点）
30. `app/routers/websocket.py` - WebSocket路由

#### 服务
31. `app/services/__init__.py` - 服务包初始化
32. `app/services/data_service.py` - 数据库操作服务
33. `app/services/fall_detection.py` - 摔倒检测算法

#### 配置和依赖
34. `requirements.txt` - Python依赖列表
35. `Dockerfile` - Docker构建配置
36. `.env` - 环境变量配置
37. `.env.example` - 环境变量示例

#### 数据库迁移
38. `alembic.ini` - Alembic迁移配置
39. `alembic/env.py` - Alembic环境配置

#### 文档
40. `API_DOCUMENTATION.md` - API接口详细文档
41. `README.md` - 后端说明文档

---

## Vue3前端 (22个文件)

### `frontend/`

#### 配置文件
42. `package.json` - NPM包配置
43. `vite.config.js` - Vite构建配置
44. `.env` - 环境变量配置

#### 核心文件
45. `src/main.js` - Vue应用入口
46. `src/App.vue` - 根组件

#### 路由
47. `src/router/index.js` - Vue Router路由配置

#### 状态管理
48. `src/stores/motion.js` - Pinia状态管理（实时数据、历史数据）

#### 服务
49. `src/services/api.js` - Axios HTTP服务
50. `src/services/websocket.js` - WebSocket服务

#### 页面视图
51. `src/views/Dashboard.vue` - 仪表板页面
52. `src/views/Waveform.vue` - 波形显示页面
53. `src/views/ThreeDView.vue` - 3D姿态可视化页面
54. `src/views/DataAnnotation.vue` - 数据标注页面
55. `src/views/DataExport.vue` - 数据导出页面

#### 通用组件
56. `src/components/RealTimeChart.vue` - 实时图表组件（Chart.js）
57. `src/components/ThreeScene.vue` - Three.js 3D场景组件
58. `src/components/DataTable.vue` - 数据表格组件
59. `src/components/StatsCards.vue` - 统计卡片组件
60. `src/components/Navbar.vue` - 导航栏组件
61. `src/components/Sidebar.vue` - 侧边栏组件

#### 工具和脚本
62. `demo-generator.js` - 测试数据生成器
63. `start.sh` - Linux启动脚本
64. `start.bat` - Windows启动脚本

#### 文档
65. `README.md` - 前端说明文档
66. `QUICK_START.md` - 快速开始指南
67. `PROJECT_SUMMARY.md` - 项目总结

---

## 数据库 (1个文件)

### `database/`
68. `init.sql` - PostgreSQL数据库初始化脚本

---

## 脚本工具 (3个文件)

### `scripts/`
69. `backup.sh` - 数据库备份脚本
70. `restore.sh` - 数据库恢复脚本
71. `generate_test_data.py` - 测试数据生成脚本（Python）

---

## 文档目录 (2个文件)

### `docs/`
72. `development.md` - 开发环境搭建指南
73. `user-guide.md` - 完整用户手册

---

## 文件类型统计

### 代码文件
- **C++ (.cpp/.h)**: 6个
- **Python (.py)**: 10个
- **Vue (.vue)**: 12个
- **JavaScript (.js)**: 8个
- **SQL (.sql)**: 1个

### 配置文件
- **YAML (.yml)**: 2个
- **JSON (.json)**: 2个
- **INI (.ini)**: 2个
- **ENV (.env)**: 4个
- **TXT (.txt)**: 1个
- **其他配置**: 1个

### 脚本文件
- **Shell (.sh)**: 8个
- **Batch (.bat)**: 3个

### 文档文件
- **Markdown (.md)**: 18个

---

## 核心功能文件映射

### 数据采集
- `esp32_firmware/src/main.cpp` - ESP32主程序
- `esp32_firmware/src/mpu6500.cpp` - MPU6500驱动
- `esp32_firmware/include/config.h` - 采集配置

### 数据传输
- `esp32_firmware/include/websocket_client.h` - WebSocket客户端
- `backend/app/routers/websocket.py` - WebSocket服务端
- `frontend/src/services/websocket.js` - 前端WebSocket

### 数据存储
- `backend/app/models.py` - 数据库模型
- `backend/app/database.py` - 数据库连接
- `database/init.sql` - 数据库初始化

### 数据处理
- `backend/app/services/fall_detection.py` - 摔倒检测算法
- `backend/app/services/data_service.py` - 数据服务
- `backend/app/routers/api.py` - REST API

### 数据展示
- `frontend/src/views/Waveform.vue` - 波形显示
- `frontend/src/views/ThreeDView.vue` - 3D可视化
- `frontend/src/views/Dashboard.vue` - 仪表板
- `frontend/src/components/RealTimeChart.vue` - 图表组件
- `frontend/src/components/ThreeScene.vue` - 3D场景

### 数据管理
- `frontend/src/views/DataAnnotation.vue` - 数据标注
- `frontend/src/views/DataExport.vue` - 数据导出
- `frontend/src/components/DataTable.vue` - 数据表格

---

## 部署相关文件

### Docker
- `docker-compose.yml` - 容器编排
- `backend/Dockerfile` - 后端镜像
- `frontend/Dockerfile` - 前端镜像
- `frontend/nginx.conf` - Nginx配置

### 启动脚本
- `start.sh` / `start.bat` - 基础启动
- `setup_and_start.sh` / `setup_and_start.bat` - 一键启动
- `verify_project.sh` - 项目验证

### 数据库
- `database/init.sql` - 初始化脚本
- `scripts/backup.sh` - 备份脚本
- `scripts/restore.sh` - 恢复脚本

---

## 文档索引

### 用户文档
1. **README.md** - 项目概述和快速开始
2. **QUICKSTART.md** - 5分钟快速启动
3. **QUICK_REFERENCE.md** - 快速参考卡
4. **FINAL_REPORT.md** - 项目完整报告
5. **docs/user-guide.md** - 完整用户手册

### 开发文档
6. **docs/development.md** - 开发环境指南
7. **backend/API_DOCUMENTATION.md** - API接口文档
8. **esp32_firmware/CONFIGURATION.md** - ESP32配置说明
9. **CONFIGURATION_EXAMPLES.md** - 配置示例集合

### 参考文档
10. **PROJECT_SUMMARY.md** - 项目技术总结
11. **DELIVERY_CHECKLIST.md** - 交付清单
12. **backend/README.md** - 后端说明
13. **frontend/README.md** - 前端说明

---

## 快速查找指南

### 想修改WiFi配置？
→ `esp32_firmware/include/config.h`

### 想修改服务器地址？
→ `esp32_firmware/include/config.h` (WS_SERVER_HOST)

### 想修改数据库配置？
→ `backend/.env` 或 `docker-compose.yml`

### 想修改摔倒检测参数？
→ `backend/.env` (FALL_WINDOW_SIZE, FALL_ACCEL_THRESHOLD, FALL_GYRO_THRESHOLD)

### 想修改前端API地址？
→ `frontend/.env` (VITE_API_URL, VITE_WS_URL)

### 想查看API接口？
→ `backend/API_DOCUMENTATION.md` 或 http://localhost:8000/docs

### 想了解ESP32配置？
→ `esp32_firmware/CONFIGURATION.md`

### 想启动系统？
→ `./setup_and_start.sh` 或 `setup_and_start.bat`

### 想验证项目完整性？
→ `./verify_project.sh`

### 想生成测试数据？
→ `cd scripts && python generate_test_data.py`

### 想备份数据库？
→ `./scripts/backup.sh`

### 想查看日志？
→ `docker-compose logs -f`

---

## 项目亮点

✅ **完整性** - 覆盖数据采集、传输、存储、展示全流程
✅ **实时性** - WebSocket毫秒级数据传输
✅ **可扩展** - 模块化设计，支持多设备
✅ **易用性** - Docker一键部署，详细文档
✅ **专业性** - 适合机器学习模型训练

---

**总文件数**: 70+ 个
**项目状态**: ✅ 已完成，可立即使用
**开发时间**: 2024年1月
**技术栈**: ESP32 + FastAPI + Vue3 + PostgreSQL + Docker
