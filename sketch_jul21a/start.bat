@echo off
REM 摔倒检测系统 —— Docker 一键启动
REM Linux/macOS 等价脚本: start.sh
REM
REM 启动 postgres + backend + frontend 三个服务，并等待后端健康检查通过。
REM 若只想在本地直接跑后端（SQLite，无需 Docker），请用 backend\start_backend.bat。

setlocal
cd /d "%~dp0"

where docker >nul 2>&1
if errorlevel 1 (
    echo 错误: 未找到 Docker，请先安装 Docker Desktop
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    set "COMPOSE=docker-compose"
) else (
    set "COMPOSE=docker compose"
)

if not exist "backend\.env" (
    copy /y "backend\.env.example" "backend\.env" >nul
    echo 已创建 backend\.env
)

echo 构建并启动服务...
%COMPOSE% up -d --build
if errorlevel 1 (
    echo 启动失败，请检查上方错误信息
    pause
    exit /b 1
)

echo.
echo 等待后端就绪...
set "READY=0"
for /l %%i in (1,1,30) do (
    if "%READY%"=="0" (
        curl -fsS http://localhost:8000/health >nul 2>&1
        if not errorlevel 1 set "READY=1"
        if "%READY%"=="0" timeout /t 2 /nobreak >nul
    )
)

echo.
%COMPOSE% ps

echo.
if "%READY%"=="1" (
    echo 后端健康检查通过
) else (
    echo 后端在 60 秒内未就绪，请查看日志: %COMPOSE% logs backend
)

echo.
echo ===========================================
echo 访问地址
echo ===========================================
echo   前端界面:  http://localhost:3000
echo   后端 API:  http://localhost:8000
echo   API 文档:  http://localhost:8000/docs
echo.
echo 常用命令
echo   %COMPOSE% logs -f           ^# 查看日志
echo   %COMPOSE% down              ^# 停止服务
echo   %COMPOSE% up -d --build     ^# 重新构建并启动
echo ===========================================
echo.
pause
endlocal