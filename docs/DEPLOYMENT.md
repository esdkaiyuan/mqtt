# MQTT云平台 - 部署文档

## 1. 环境要求

### 1.1 最低配置
- CPU：2核
- 内存：4GB
- 磁盘：20GB可用空间
- 操作系统：Linux（推荐Ubuntu 20.04+）/ macOS / Windows（WSL2）

### 1.2 软件依赖
- Docker 20.10+
- Docker Compose 2.0+
- Git（可选，用于克隆代码）

### 1.3 端口要求
| 服务 | 端口 | 用途 | 可修改 |
|------|------|------|--------|
| MySQL | 3306 | 数据库 | 是（.env中DB_PORT） |
| Redis | 6379 | 缓存 | 是（.env中REDIS_PORT） |
| EMQX | 1883 | MQTT协议 | 是（.env中MQTT_PORT） |
| EMQX WebSocket | 8083 | WebSocket连接 | 是 |
| EMQX Dashboard | 18083 | EMQX管理后台 | 是 |
| 后端API | 8080 | HTTP服务 | 是（.env中BACKEND_PORT） |
| 前端 | 80 | HTTP服务 | 是（.env中FRONTEND_PORT） |

## 2. 快速部署（推荐）

### 步骤1：克隆代码
```bash
git clone <repository-url>
cd MQTT自建站点
```

### 步骤2：配置环境变量
```bash
cp .env.example .env
# 编辑.env，修改密码等配置
nano .env
```

### 步骤3：执行部署
```bash
bash scripts/deploy.sh
```

### 步骤4：验证部署
```bash
bash scripts/health-check.sh
```

### 步骤5：访问系统
- 前端：http://localhost
- API文档：http://localhost:8080/api/swagger-ui.html
- EMQX后台：http://localhost:18083
- 默认账号：admin / admin123

## 3. 手动部署（不推荐，用于理解流程）

### 步骤1：构建后端
```bash
cd backend
mvn clean package -DskipTests
```

### 步骤2：构建前端
```bash
cd frontend
npm install
npm run build
```

### 步骤3：启动服务
```bash
cd docker
docker-compose up -d --build
```

### 步骤4：验证
```bash
bash ../scripts/health-check.sh
```

## 4. 常见问题

### Q1: 端口冲突怎么办？
A: 修改`.env`文件中的对应端口号。

### Q2: 数据库连接失败？
A: 检查MySQL容器是否正常启动：`docker logs mqtt-mysql`。

### Q3: 前端无法访问？
A: 检查前端容器是否正常启动：`docker logs mqtt-frontend`。

### Q4: MQTT连接失败？
A: 确认EMQX容器运行正常：`docker logs mqtt-emqx`。

### Q5: 如何备份数据？
A: 使用 `docker exec mqtt-mysql mysqldump` 备份MySQL。

### Q6: 如何升级版本？
A: 重新执行 `bash scripts/deploy.sh`。

## 5. 生产环境建议

1. **更换JWT密钥：** 修改`.env`中的`JWT_SECRET`为强随机字符串
2. **启用HTTPS：** 在Nginx配置中添加SSL证书
3. **配置防火墙：** 仅开放必要端口（80/443）
4. **设置日志轮转：** 配置Docker日志大小限制
5. **定期备份：** 设置自动备份脚本
6. **监控告警：** 集成Prometheus + Grafana监控系统
