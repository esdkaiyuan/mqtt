#!/usr/bin/env bash
#
# 摔倒检测系统 —— Docker 一键启动
# Windows 等价脚本: start.bat
#
# 启动 postgres + backend + frontend 三个服务，并等待后端健康检查通过。
# 若只想在本地直接跑后端（SQLite，无需 Docker），请用 backend/start_backend.sh。

set -euo pipefail

cd "$(dirname "$0")"

# 优先使用 Compose V2 插件，回退到独立的 docker-compose 命令
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo "错误: 未找到 Docker Compose（docker compose 或 docker-compose）"
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "错误: 未找到 Docker，请先安装 Docker Desktop"
    exit 1
fi

# 首次运行时准备本地后端配置（docker-compose 已内联数据库连接，这里只做本地覆盖）
if [ ! -f backend/.env ]; then
    cp backend/.env.example backend/.env
    echo "已创建 backend/.env"
fi

echo "构建并启动服务..."
$COMPOSE up -d --build

echo ""
echo "等待后端就绪..."
READY=0
for _ in $(seq 1 30); do
    if curl -fsS http://localhost:8000/health >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 2
done

echo ""
$COMPOSE ps

echo ""
if [ "$READY" -eq 1 ]; then
    echo "后端健康检查通过"
else
    echo "后端在 60 秒内未就绪，请查看日志: $COMPOSE logs backend"
fi

cat <<'EOF'

===========================================
访问地址
===========================================
  前端界面:  http://localhost:3000
  后端 API:  http://localhost:8000
  API 文档:  http://localhost:8000/docs

常用命令
  $COMPOSE logs -f           # 查看日志
  $COMPOSE down              # 停止服务
  $COMPOSE up -d --build     # 重新构建并启动
===========================================
EOF