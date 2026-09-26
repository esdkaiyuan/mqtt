@echo off
chcp 65001 >nul
echo ========================================
echo   MQTT Cloud Platform - Startup Script
echo ========================================
echo.

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed or not in PATH
    pause
    exit /b 1
)

REM Check if Mosquitto is running
tasklist | findstr "mosquitto.exe" >nul 2>&1
if errorlevel 1 (
    echo [WARN] Mosquitto is not running. Starting...
    start "Mosquitto" /B "C:\Program Files\Mosquitto\mosquitto.exe" -c "%~dp0mosquitto.conf"
    timeout /t 3 /nobreak >nul
) else (
    echo [OK] Mosquitto is running
)

REM Check if MySQL is running
tasklist | findstr "mysqld.exe" >nul 2>&1
if errorlevel 1 (
    echo [WARN] MySQL is not running. Please start MySQL first.
    pause
    exit /b 1
) else (
    echo [OK] MySQL is running
)

REM Check if Redis is running
tasklist | findstr "redis-server.exe" >nul 2>&1
if errorlevel 1 (
    echo [WARN] Redis is not running. Please start Redis first.
    pause
    exit /b 1
) else (
    echo [OK] Redis is running
)

REM Kill any existing backend process on port 8080
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [INFO] Stopping existing backend...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do taskkill //F //PID %%a >nul 2>&1
    timeout /t 2 /nobreak >nul
)

REM Start the backend
echo [INFO] Starting Spring Boot backend...
start "MQTT Backend" /B java -jar "%~dp0backend\target\mqtt-cloud-backend-1.0.0.jar" --spring.profiles.active=dev

REM Wait for backend to start
echo [INFO] Waiting for backend to start...
timeout /t 12 /nobreak >nul

REM Check health
curl -s http://localhost:8080/api/health >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Backend failed to start. Check logs.
    pause
    exit /b 1
)

echo [OK] Backend is running!
echo.
echo ========================================
echo   System is ready!
echo ========================================
echo   API:  http://localhost:8080/api
echo   Health: http://localhost:8080/api/health
echo   MQTT Broker: localhost:1883 (TCP)
echo   MQTT WebSocket: localhost:8083
echo.
echo   Default login: admin / admin123
echo ========================================
pause
