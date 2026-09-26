# MQTT云平台

> 完整的MQTT云平台系统，支持设备管理、实时消息监控、历史数据查询

## 技术栈

- **前端：** Vue.js 3 + Vite + Element Plus + SVG图标
- **后端：** Spring Boot 3.x + MQTT Broker (EMQX) + MyBatis Plus
- **数据库：** MySQL 8
- **消息中间件：** MQTT over TCP (EMQX作为Broker)
- **缓存：** Redis (会话/设备在线状态)
- **部署：** Docker Compose

## 功能特性

- 用户认证与权限管理（ADMIN/OPERATOR/VIEWER）
- 设备管理（CRUD、在线状态监控、MQTT Topic管理）
- 实时消息监控（WebSocket连接、实时消息流）
- 历史数据查询（按设备/时间/Topic过滤、分页）
- 极简黑白灰色系UI设计

## 快速开始

### 环境要求
- Docker 20.10+
- Docker Compose 2.0+
- 4GB+ 内存

### 部署步骤
```bash
# 1. 克隆项目
git clone <repository-url>
cd MQTT自建站点

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 修改数据库密码、端口等配置
# JWT_SECRET 留空时 deploy.sh 会自动生成（Base64 且解码后不少于 32 字节）

# 3. 一键部署（前后端均在容器内构建，本机无需 Maven/Node）
bash scripts/deploy.sh
```

### 常用运维命令
```bash
# compose 文件位于 docker/ 目录，需显式指定 --env-file 才能读到根目录的 .env
docker compose --env-file .env -f docker/docker-compose.yml ps      # 查看状态
docker compose --env-file .env -f docker/docker-compose.yml logs -f # 查看日志
docker compose --env-file .env -f docker/docker-compose.yml down    # 停止服务（保留数据）
docker compose --env-file .env -f docker/docker-compose.yml down -v # 停止并清空数据卷
```

### 访问系统
- 前端页面：http://localhost
- API文档：http://localhost:8080/api/swagger-ui.html
- EMQX后台：http://localhost:18083

### 默认账号
- 用户名：admin
- 密码：admin123

## 项目结构

```
MQTT自建站点/
├── backend/              # Spring Boot后端
│   └── src/main/java/com/mqtt/cloud/
│       ├── config/       # 全局配置
│       ├── controller/   # 控制器
│       ├── service/      # 业务逻辑
│       ├── mapper/       # 数据访问层
│       ├── entity/       # 数据实体
│       ├── dto/          # 数据传输对象
│       ├── mqtt/         # MQTT处理
│       └── common/       # 通用类
├── frontend/             # Vue.js前端
│   └── src/
│       ├── assets/       # 静态资源
│       ├── components/   # 组件
│       ├── views/        # 页面
│       ├── router/       # 路由
│       ├── stores/       # 状态管理
│       └── api/          # 接口封装
├── docker/               # Docker配置
├── scripts/              # 部署和健康检查脚本
├── docs/                 # 项目文档
└── README.md
```

## 文档

- [数据库设计](docs/T-01_数据库设计与Schema初始化_开发文档.md)
- [后端架构](docs/T-02_后端项目基础架构搭建_开发文档.md)
- [部署文档](docs/DEPLOYMENT.md)
- [API文档](http://localhost:8080/api/swagger-ui.html)（本地部署后访问）

## 开发

### 本地开发（不使用Docker）

#### 后端
```bash
cd backend
mvn clean compile spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 前端
```bash
cd frontend
npm install
npm run dev
```

## 许可证

MIT License
