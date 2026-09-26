@echo off
echo ============================================
echo   数据采集系统 - 一键启动
echo   版本: 2.1.0
echo ============================================
echo.

REM 启动后端
echo 启动后端服务（端口 8001）...
cd backend\backend
start /B python -m uvicorn app.main_fixed:app --host 0.0.0.0 --port 8001 --reload
cd ..\..

REM 等待后端启动
timeout /t 3 /nobreak

REM 启动前端
echo 启动前端服务（端口 3000）...
cd frontend
start /B python -m http.server 3000
cd ..

REM 等待前端启动
timeout /t 2 /nobreak

echo.
echo ============================================
echo ✓ 服务启动完成！
echo ============================================
echo.
echo 前端界面: http://localhost:3000
echo 后端API:  http://localhost:8001
echo API文档:  http://localhost:8001/docs
echo ============================================
echo.
echo 按任意键关闭...
pause > nul
