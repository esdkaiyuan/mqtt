#!/usr/bin/env bash
#
# 后端本地开发启动（SQLite，无需 Docker / PostgreSQL）
# Windows 等价脚本: start_backend.bat
#
# 默认读取 backend/.env；首次运行会从 .env.example 复制一份。

set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env ]; then
    cp .env.example .env
    echo "已创建 .env（默认使用 SQLite）"
fi

if [ ! -d venv ]; then
    echo "创建虚拟环境..."
    python -m venv venv
fi

# shellcheck disable=SC1091
source venv/bin/activate 2>/dev/null || source venv/Scripts/activate

pip install -q -r requirements.txt

echo ""
echo "启动后端: http://localhost:8000  (API 文档 /docs)"
echo ""
exec python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload