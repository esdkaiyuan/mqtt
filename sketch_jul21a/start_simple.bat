@echo off
REM 原始数据采集系统 - 启动脚本 (Windows)
REM 版本: 2.0.0

echo ==========================================
echo   原始数据采集系统
echo   版本: 2.0.0
echo ==========================================

REM 步骤1：检查Docker
echo.
echo 步骤 1/4: 检查Docker环境
docker --version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker未安装
    pause
    exit /b 1
)
echo √ Docker环境正常

REM 步骤2：启动PostgreSQL
echo.
echo 步骤 2/4: 启动PostgreSQL
docker run -d --name fall_detection_db -e POSTGRES_DB=fall_detection -e POSTGRES_USER=fall_user -e POSTGRES_PASSWORD=fall_password -p 5432:5432 -v postgres_data:/var/lib/postgresql/data postgres:15-alpine 2>nul || echo 数据库容器已存在
timeout /t 5 /nobreak

REM 步骤3：初始化数据库
echo.
echo 步骤 3/4: 初始化数据库
docker exec -i fall_detection_db psql -U fall_user -d fall_detection < database\init_simple.sql 2>nul || echo 表已存在
echo √ 数据库已初始化

REM 步骤4：启动后端
echo.
echo 步骤 4/4: 启动后端服务

REM 检查是否需要创建虚拟环境
if not exist "backend\venv" (
    echo 创建Python虚拟环境...
    cd backend
    python -m venv venv
    cd ..
)

REM 激活虚拟环境并安装依赖
cd backend
call venv\Scripts\activate 2>nul || true
pip install -q fastapi uvicorn sqlalchemy asyncpg pydantic-settings 2>nul
cd ..

REM 启动后端（后台运行）
cd backend
start /B uvicorn app.main_simple:app --host 0.0.0.0 --port 8000 --reload
cd ..

timeout /t 3 /nobreak

echo.
echo ==========================================
echo √ 系统启动完成！
echo ==========================================
echo.
echo 访问地址：
echo   后端API: http://localhost:8000
echo   API文档: http://localhost:8000/docs
echo   统计信息: http://localhost:8000/api/stats
echo.
echo 数据库连接：
echo   主机: localhost
echo   端口: 5432
echo   数据库: fall_detection
echo   用户: fall_user
echo   密码: fall_password
echo.
echo ESP32配置：
echo   打开 arduino_firmware\ESP32_Raw_Data\ESP32_Raw_Data.ino
echo   修改 WS_SERVER_HOST 为你的电脑IP
echo.
echo 常用命令：
echo   查看数据库: docker exec -it fall_detection_db psql -U fall_user -d fall_detection
echo   查看统计: curl http://localhost:8000/api/stats
echo   导出数据: curl http://localhost:8000/api/export -o data.csv
echo ==========================================

pause
