#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  MQTT云平台部署脚本 v1.1"
echo "=========================================="

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.yml"
ENV_FILE="$ROOT_DIR/.env"

# 1. 检查依赖
echo -e "\n${YELLOW}[1/6] 检查依赖...${NC}"
command -v docker >/dev/null 2>&1 || { echo -e "${RED}错误：Docker未安装${NC}"; exit 1; }
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo -e "${RED}错误：Docker Compose未安装${NC}"; exit 1
fi
echo -e "${GREEN}✓ 依赖检查通过（${COMPOSE}）${NC}"

# 2. 准备环境变量
echo -e "\n${YELLOW}[2/6] 准备环境配置...${NC}"
if [ ! -f "$ENV_FILE" ]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
    echo -e "${YELLOW}⚠ 未找到 .env，已从 .env.example 创建${NC}"
fi

gen_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 48 | tr -d '\n'
    else
        head -c 48 /dev/urandom | base64 | tr -d '\n'
    fi
}

# JWT_SECRET 为空会导致 JWT 签名抛 WeakKeyException，此处自动补齐
if ! grep -qE '^JWT_SECRET=.+' "$ENV_FILE"; then
    SECRET="$(gen_secret)"
    if grep -qE '^JWT_SECRET=' "$ENV_FILE"; then
        sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=${SECRET}|" "$ENV_FILE" && rm -f "${ENV_FILE}.bak"
    else
        printf '\nJWT_SECRET=%s\n' "$SECRET" >> "$ENV_FILE"
    fi
    echo -e "${YELLOW}⚠ JWT_SECRET 为空，已自动生成并写入 .env${NC}"
fi
echo -e "${GREEN}✓ 环境配置就绪${NC}"

# 3. 构建并启动（前后端均在容器内构建，无需本地 Maven/Node）
echo -e "\n${YELLOW}[3/6] 构建并启动容器...${NC}"
$COMPOSE --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build
echo -e "${GREEN}✓ 容器已启动${NC}"

# 4. 等待后端就绪
echo -e "\n${YELLOW}[4/6] 等待后端就绪...${NC}"
BACKEND_PORT="$(grep -E '^BACKEND_PORT=' "$ENV_FILE" | cut -d= -f2 | tr -d '[:space:]')"
BACKEND_PORT="${BACKEND_PORT:-8080}"
READY=""
for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:${BACKEND_PORT}/api/health" >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 3
done
if [ -z "$READY" ]; then
    echo -e "${RED}✗ 后端未在预期时间内就绪，最近日志：${NC}"
    $COMPOSE --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail=50 backend
    exit 1
fi
echo -e "${GREEN}✓ 后端已就绪${NC}"

# 5. 执行健康检查
echo -e "\n${YELLOW}[5/6] 执行健康检查...${NC}"
bash "$ROOT_DIR/scripts/health-check.sh"

# 6. 输出访问信息
FRONTEND_PORT="$(grep -E '^FRONTEND_PORT=' "$ENV_FILE" | cut -d= -f2 | tr -d '[:space:]')"
FRONTEND_PORT="${FRONTEND_PORT:-80}"
echo -e "\n${YELLOW}[6/6] 部署信息${NC}"
echo ""
echo "=========================================="
echo -e "${GREEN}  ✓ 部署完成！${NC}"
echo "=========================================="
echo "  前端页面：http://localhost:${FRONTEND_PORT}"
echo "  后端API：  http://localhost:${BACKEND_PORT}/api"
echo "  EMQX后台： http://localhost:18083"
echo ""
echo "  默认账号：admin / admin123"
echo ""
echo "  停止服务：$COMPOSE --env-file .env -f docker/docker-compose.yml down"
echo "  重置数据：$COMPOSE --env-file .env -f docker/docker-compose.yml down -v  # 会清空数据库"
echo ""