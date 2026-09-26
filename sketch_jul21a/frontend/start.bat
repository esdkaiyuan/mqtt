@echo off
title 摔倒检测系统

echo ==================================
echo 摔倒检测系统 - 启动脚本
echo ==================================
echo.

:: 检查 Node.js 是否安装
where node >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到 Node.js
    echo 请先安装 Node.js: https://nodejs.org/
    pause
    exit /b 1
)

:: 显示 Node.js 版本
for /f "tokens=*" %%i in ('node -v') do set NODE_VERSION=%%i
for /f "tokens=*" %%i in ('npm -v') do set NPM_VERSION=%%i
echo ✓ Node.js 版本: %NODE_VERSION%
echo ✓ npm 版本: %NPM_VERSION%
echo.

:: 检查 package.json 是否存在
if not exist "package.json" (
    echo ❌ 错误: 未找到 package.json
    echo 请确保在 frontend 目录中运行此脚本
    pause
    exit /b 1
)

:: 检查是否已安装依赖
if not exist "node_modules" (
    echo 📦 首次运行，正在安装依赖...
    echo 这可能需要几分钟时间...
    echo.
    call npm install
    if %errorlevel% neq 0 (
        echo ❌ 依赖安装失败
        pause
        exit /b 1
    )
    echo.
    echo ✓ 依赖安装完成
) else (
    echo ✓ 依赖已安装
)

echo.
echo 🚀 启动开发服务器...
echo.
echo 访问地址: http://localhost:3000
echo 按 Ctrl+C 停止服务器
echo.
echo ==================================
echo.

:: 启动开发服务器
npm run dev

pause
