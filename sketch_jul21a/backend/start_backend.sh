#!/bin/bash

# 原始数据采集系统 - 启动脚本（SQLite版本）
# 无需Docker或PostgreSQL

echo "=========================================="
echo "  原始数据采集系统 - 启动脚本"
echo "  版本: 2.0.0 (SQLite)"
echo "=========================================="
echo ""

# 进入后端目录
cd backend

# 检查虚拟环境
if [ ! -d "venv" ]; then
    echo "创建Python虚拟环境..."
    python -m venv venv
fi

# 激活虚拟环境
source venv/Scripts/activate 2>/dev/null || source venv/bin/activate 2>/dev/null || true

# 检查依赖
echo "检查依赖..."
python -c "import fastapi" 2>/dev/null || pip install fastapi uvicorn aiosqlite

echo ""
echo "启动后端服务..."
echo ""

# 启动服务
python -m uvicorn app.main_sqlite:app --host 0.0.0.0 --port 8000 --reload
