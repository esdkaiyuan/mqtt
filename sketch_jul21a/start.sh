#!/bin/bash

# 摔倒检测系统启动脚本

set -e

echo "==================================="
echo "摔倒检测数据采集系统"
echo "==================================="

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "错误: Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 检查.env文件
if [ ! -f .env ]; then
    echo "创建环境配置文件..."
    cp .env.example .env
    echo "已创建.env文件，请根据需要修改配置"
fi

# 启动服务
echo "启动服务..."
docker-compose up -d

# 等待服务启动
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo "检查服务状态..."
docker-compose ps

# 显示访问信息
echo ""
echo "==================================="
echo "服务启动完成！"
echo "==================================="
echo ""
echo "访问地址："
echo "  - 后端API: http://localhost:8000"
echo "  - API文档: http://localhost:8000/docs"
echo "  - 前端界面: http://localhost:3000"
echo ""
echo "数据库连接："
echo "  - 主机: localhost"
echo "  - 端口: 5432"
echo "  - 数据库: fall_detection"
echo "  - 用户: fall_user"
echo "  - 密码: fall_password"
echo ""
echo "ESP32配置："
echo "  - WiFi SSID: 8202"
echo "  - WiFi密码: 88888888"
echo "  - WebSocket服务器: 修改esp32_firmware/include/config.h中的IP地址"
echo ""
echo "常用命令："
echo "  - 查看日志: docker-compose logs -f"
echo "  - 停止服务: docker-compose down"
echo "  - 重启服务: docker-compose restart"
echo "  - 查看后端日志: docker-compose logs -f backend"
echo "  - 查看数据库: docker-compose exec postgres psql -U fall_user -d fall_detection"
echo ""
echo "==================================="
