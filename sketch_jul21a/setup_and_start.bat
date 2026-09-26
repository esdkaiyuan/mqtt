@echo off
REM 摔倒检测系统 - 一键启动和验证脚本 (Windows)

echo ===========================================
echo 摔倒检测数据采集系统 - 一键启动
echo ===========================================

REM 步骤1：检查Docker
echo.
echo 步骤 1/5: 检查Docker环境
echo -----------------------------------

docker --version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker未安装
    echo 请先安装Docker Desktop: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)

docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker Compose未安装
    echo 请安装Docker Compose或使用Docker Desktop
    pause
    exit /b 1
)

echo √ Docker环境检查通过

REM 步骤2：验证项目结构
echo.
echo 步骤 2/5: 验证项目结构
echo -----------------------------------

call verify_project.bat

if errorlevel 1 (
    echo 项目验证失败，请检查上述错误
    pause
    exit /b 1
)

REM 步骤3：检查环境配置
echo.
echo 步骤 3/5: 检查环境配置
echo -----------------------------------

REM 检查后端配置
if not exist backend\.env (
    echo 创建后端配置文件...
    copy backend\.env.example backend\.env
    echo √ 后端配置已创建
) else (
    echo √ 后端配置已存在
)

REM 检查前端配置
if not exist frontend\.env (
    echo 创建前端配置文件...
    (
        echo VITE_API_URL=http://localhost:8000
        echo VITE_WS_URL=ws://localhost:8000
        echo VITE_APP_TITLE=摔倒检测系统
        echo VITE_APP_VERSION=1.0.0
    ) > frontend\.env
    echo √ 前端配置已创建
) else (
    echo √ 前端配置已存在
)

REM 步骤4：启动服务
echo.
echo 步骤 4/5: 启动服务
echo -----------------------------------

echo 正在启动所有服务...
docker-compose up -d

echo 等待服务启动（30秒）...
timeout /t 30 /nobreak

REM 检查服务状态
echo.
echo 检查服务状态...
docker-compose ps

REM 步骤5：验证服务
echo.
echo 步骤 5/5: 验证服务
echo -----------------------------------

REM 检查后端健康
echo 检查后端服务...
curl -s http://localhost:8000/health >nul 2>&1
if errorlevel 1 (
    echo × 后端服务启动失败
    echo 查看后端日志: docker-compose logs backend
) else (
    echo √ 后端服务正常
)

REM 检查前端
echo 检查前端服务...
curl -s http://localhost:3000 >nul 2>&1
if errorlevel 1 (
    echo × 前端服务启动失败
    echo 查看前端日志: docker-compose logs frontend
) else (
    echo √ 前端服务正常
)

REM 完成
echo.
echo ===========================================
echo 启动完成！
echo ===========================================
echo.
echo 访问地址：
echo   - 前端界面: http://localhost:3000
echo   - 后端API: http://localhost:8000
echo   - API文档: http://localhost:8000/docs
echo.
echo 数据库连接：
echo   - 主机: localhost
echo   - 端口: 5432
echo   - 数据库: fall_detection
echo   - 用户: fall_user
echo   - 密码: fall_password
echo.
echo 常用命令：
echo   - 查看日志: docker-compose logs -f
echo   - 停止服务: docker-compose down
echo   - 重启服务: docker-compose restart
echo   - 生成测试数据: cd scripts ^&^& python generate_test_data.py
echo.
echo ESP32配置：
echo   - WiFi SSID: 8202
echo   - WiFi密码: 88888888
echo   - 修改 esp32_firmware\include\config.h
echo   - 将 WS_SERVER_HOST 改为你的电脑IP地址
echo.
echo ===========================================

REM 打开浏览器
set /p OPEN_BROWSER="是否打开前端界面？(y/N): "
if /i "%OPEN_BROWSER%"=="y" (
    start http://localhost:3000
)

pause
