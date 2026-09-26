#!/bin/bash

# 原始数据采集系统 - 启动脚本
# 版本: 2.0.0

set -e

echo "=========================================="
echo "  原始数据采集系统"
echo "  版本: 2.0.0"
echo "=========================================="

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 步骤1：检查Docker
echo ""
echo -e "${YELLOW}步骤 1/4: 检查Docker环境${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker环境正常${NC}"

# 步骤2：启动PostgreSQL
echo ""
echo -e "${YELLOW}步骤 2/4: 启动PostgreSQL${NC}"
docker run -d \
  --name fall_detection_db \
  -e POSTGRES_DB=fall_detection \
  -e POSTGRES_USER=fall_user \
  -e POSTGRES_PASSWORD=fall_password \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:15-alpine 2>/dev/null || echo "数据库容器已存在"

sleep 5

# 步骤3：初始化数据库
echo ""
echo -e "${YELLOW}步骤 3/4: 初始化数据库${NC}"
docker exec -i fall_detection_db psql -U fall_user -d fall_detection < database/init_simple.sql 2>/dev/null || echo "表已存在"
echo -e "${GREEN}✓ 数据库已初始化${NC}"

# 步骤4：启动后端
echo ""
echo -e "${YELLOW}步骤 4/4: 启动后端服务${NC}"

# 检查是否需要创建虚拟环境
if [ ! -d "backend/venv" ]; then
    echo "创建Python虚拟环境..."
    cd backend
    python -m venv venv
    cd ..
fi

# 激活虚拟环境并安装依赖
cd backend
source venv/bin/activate 2>/dev/null || true
pip install -q fastapi uvicorn sqlalchemy asyncpg pydantic-settings 2>/dev/null
cd ..

# 启动后端
cd backend
uvicorn app.main_simple:app --host 0.0.0.0 --port 8000 --reload &
BACKEND_PID=$!
cd ..

sleep 3

# 验证服务
echo ""
echo "验证服务..."
if curl -s http://localhost:8000/health > /dev/null; then
    echo -e "${GREEN}✓ 后端服务正常${NC}"
else
    echo -e "${RED}✗ 后端服务启动失败${NC}"
fi

echo ""
echo "=========================================="
echo -e "${GREEN}系统启动完成！${NC}"
echo "=========================================="
echo ""
echo "访问地址："
echo "  后端API: http://localhost:8000"
echo "  API文档: http://localhost:8000/docs"
echo "  统计信息: http://localhost:8000/api/stats"
echo ""
echo "数据库连接："
echo "  主机: localhost"
echo "  端口: 5432"
echo "  数据库: fall_detection"
echo "  用户: fall_user"
echo "  密码: fall_password"
echo ""
echo "ESP32配置："
echo "  打开 arduino_firmware/ESP32_Raw_Data/ESP32_Raw_Data.ino"
echo "  修改 WS_SERVER_HOST 为你的电脑IP"
echo ""
echo "常用命令："
echo "  查看数据库: docker exec -it fall_detection_db psql -U fall_user -d fall_detection"
echo "  查看统计: curl http://localhost:8000/api/stats"
echo "  导出数据: curl http://localhost:8000/api/export -o data.csv"
echo "  停止服务: kill $BACKEND_PID"
echo "=========================================="
