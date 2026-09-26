#!/bin/bash

# 摔倒检测系统 - 一键启动和验证脚本

set -e

echo "==========================================="
echo "摔倒检测数据采集系统 - 一键启动"
echo "==========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 步骤1：检查Docker
echo ""
echo -e "${BLUE}步骤 1/5: 检查Docker环境${NC}"
echo "-----------------------------------"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker未安装${NC}"
    echo "请先安装Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}错误: Docker Compose未安装${NC}"
    echo "请安装Docker Compose或使用Docker Desktop"
    exit 1
fi

echo -e "${GREEN}✓ Docker环境检查通过${NC}"

# 步骤2：验证项目结构
echo ""
echo -e "${BLUE}步骤 2/5: 验证项目结构${NC}"
echo "-----------------------------------"

chmod +x verify_project.sh
./verify_project.sh

if [ $? -ne 0 ]; then
    echo -e "${RED}项目验证失败，请检查上述错误${NC}"
    exit 1
fi

# 步骤3：检查环境配置
echo ""
echo -e "${BLUE}步骤 3/5: 检查环境配置${NC}"
echo "-----------------------------------"

# 检查后端配置
if [ ! -f backend/.env ]; then
    echo -e "${YELLOW}创建后端配置文件...${NC}"
    cp backend/.env.example backend/.env
    echo -e "${GREEN}✓ 后端配置已创建${NC}"
else
    echo -e "${GREEN}✓ 后端配置已存在${NC}"
fi

# 检查前端配置
if [ ! -f frontend/.env ]; then
    echo -e "${YELLOW}创建前端配置文件...${NC}"
    cat > frontend/.env << EOF
VITE_API_URL=http://localhost:8000
VITE_WS_URL=ws://localhost:8000
VITE_APP_TITLE=摔倒检测系统
VITE_APP_VERSION=1.0.0
EOF
    echo -e "${GREEN}✓ 前端配置已创建${NC}"
else
    echo -e "${GREEN}✓ 前端配置已存在${NC}"
fi

# 步骤4：启动服务
echo ""
echo -e "${BLUE}步骤 4/5: 启动服务${NC}"
echo "-----------------------------------"

echo "正在启动所有服务..."
docker-compose up -d

echo "等待服务启动（30秒）..."
sleep 30

# 检查服务状态
echo ""
echo "检查服务状态..."
docker-compose ps

# 步骤5：验证服务
echo ""
echo -e "${BLUE}步骤 5/5: 验证服务${NC}"
echo "-----------------------------------"

# 检查后端健康
echo -n "检查后端服务... "
if curl -s http://localhost:8000/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${RED}✗ 失败${NC}"
    echo "查看后端日志: docker-compose logs backend"
fi

# 检查前端
echo -n "检查前端服务... "
if curl -s http://localhost:3000 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${RED}✗ 失败${NC}"
    echo "查看前端日志: docker-compose logs frontend"
fi

# 检查数据库
echo -n "检查数据库服务... "
if docker-compose exec -T postgres pg_isready -U fall_user -d fall_detection > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 正常${NC}"
else
    echo -e "${RED}✗ 失败${NC}"
    echo "查看数据库日志: docker-compose logs postgres"
fi

# 完成
echo ""
echo "==========================================="
echo -e "${GREEN}启动完成！${NC}"
echo "==========================================="
echo ""
echo "访问地址："
echo "  - 前端界面: http://localhost:3000"
echo "  - 后端API: http://localhost:8000"
echo "  - API文档: http://localhost:8000/docs"
echo ""
echo "数据库连接："
echo "  - 主机: localhost"
echo "  - 端口: 5432"
echo "  - 数据库: fall_detection"
echo "  - 用户: fall_user"
echo "  - 密码: fall_password"
echo ""
echo "常用命令："
echo "  - 查看日志: docker-compose logs -f"
echo "  - 停止服务: docker-compose down"
echo "  - 重启服务: docker-compose restart"
echo "  - 生成测试数据: cd scripts && python generate_test_data.py"
echo ""
echo "ESP32配置："
echo "  - WiFi SSID: 8202"
echo "  - WiFi密码: 88888888"
echo "  - 修改 esp32_firmware/include/config.h"
echo "  - 将 WS_SERVER_HOST 改为你的电脑IP地址"
echo ""
echo "==========================================="

# 询问是否打开浏览器
read -p "是否打开前端界面？(y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    if command -v xdg-open &> /dev/null; then
        xdg-open http://localhost:3000
    elif command -v open &> /dev/null; then
        open http://localhost:3000
    elif command -v start &> /dev/null; then
        start http://localhost:3000
    else
        echo "请手动打开浏览器访问: http://localhost:3000"
    fi
fi
