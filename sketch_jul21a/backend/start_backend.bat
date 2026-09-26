@echo off
REM 后端本地开发启动（SQLite，无需 Docker / PostgreSQL）
REM Linux/macOS 等价脚本: start_backend.sh
REM
REM 默认读取 backend\.env；首次运行会从 .env.example 复制一份。

setlocal
cd /d "%~dp0"

if not exist ".env" (
    copy /y ".env.example" ".env" >nul
    echo 已创建 .env（默认使用 SQLite）
)

if not exist "venv" (
    echo 创建虚拟环境...
    python -m venv venv
)

call "venv\Scripts\activate.bat"

pip install -q -r requirements.txt
if errorlevel 1 (
    echo 依赖安装失败
    pause
    exit /b 1
)

echo.
echo 启动后端: http://localhost:8000  ^(API 文档 /docs^)
echo.
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

endlocal