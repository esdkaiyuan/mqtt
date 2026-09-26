#!/bin/bash

# 摔倒检测系统验证脚本

set -e

echo "==================================="
echo "摔倒检测系统 - 完整性验证"
echo "==================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 验证结果统计
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

# 验证函数
check_file() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
    else
        echo -e "${RED}✗${NC} $2 - 文件不存在: $1"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

check_dir() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [ -d "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
    else
        echo -e "${RED}✗${NC} $2 - 目录不存在: $1"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
    fi
}

echo ""
echo "1. 检查项目根目录文件"
echo "-----------------------------------"
check_file "docker-compose.yml" "Docker Compose配置"
check_file "README.md" "项目说明文档"
check_file "QUICKSTART.md" "快速开始指南"
check_file ".env.example" "环境配置示例"
check_file ".gitignore" "Git忽略文件"
check_file "start.sh" "Linux启动脚本"
check_file "start.bat" "Windows启动脚本"

echo ""
echo "2. 检查ESP32固件"
echo "-----------------------------------"
check_dir "esp32_firmware" "ESP32固件目录"
check_file "esp32_firmware/platformio.ini" "PlatformIO配置"
check_file "esp32_firmware/include/config.h" "配置文件"
check_file "esp32_firmware/include/mpu6500.h" "MPU6500头文件"
check_file "esp32_firmware/include/websocket_client.h" "WebSocket客户端头文件"
check_file "esp32_firmware/src/main.cpp" "主程序"
check_file "esp32_firmware/src/mpu6500.cpp" "MPU6500驱动"
check_file "esp32_firmware/CONFIGURATION.md" "配置说明文档"

echo ""
echo "3. 检查FastAPI后端"
echo "-----------------------------------"
check_dir "backend" "后端目录"
check_file "backend/app/__init__.py" "应用包初始化"
check_file "backend/app/main.py" "FastAPI入口"
check_file "backend/app/config.py" "配置管理"
check_file "backend/app/database.py" "数据库连接"
check_file "backend/app/models.py" "数据模型"
check_file "backend/app/routers/api.py" "REST API路由"
check_file "backend/app/routers/websocket.py" "WebSocket路由"
check_file "backend/app/services/data_service.py" "数据服务"
check_file "backend/app/services/fall_detection.py" "摔倒检测算法"
check_file "backend/requirements.txt" "Python依赖"
check_file "backend/Dockerfile" "Docker配置"
check_file "backend/alembic.ini" "Alembic配置"
check_file "backend/.env" "环境配置"
check_file "backend/API_DOCUMENTATION.md" "API文档"

echo ""
echo "4. 检查Vue3前端"
echo "-----------------------------------"
check_dir "frontend" "前端目录"
check_file "frontend/package.json" "NPM配置"
check_file "frontend/vite.config.js" "Vite配置"
check_file "frontend/src/main.js" "Vue入口文件"
check_file "frontend/src/App.vue" "根组件"
check_file "frontend/src/router/index.js" "路由配置"
check_file "frontend/src/stores/motion.js" "Pinia状态管理"
check_file "frontend/src/services/api.js" "API服务"
check_file "frontend/src/services/websocket.js" "WebSocket服务"

echo ""
echo "5. 检查前端页面组件"
echo "-----------------------------------"
check_file "frontend/src/views/Dashboard.vue" "仪表板页面"
check_file "frontend/src/views/Waveform.vue" "波形显示页面"
check_file "frontend/src/views/ThreeDView.vue" "3D视图页面"
check_file "frontend/src/views/DataAnnotation.vue" "数据标注页面"
check_file "frontend/src/views/DataExport.vue" "数据导出页面"

echo ""
echo "6. 检查前端通用组件"
echo "-----------------------------------"
check_file "frontend/src/components/RealTimeChart.vue" "实时图表组件"
check_file "frontend/src/components/ThreeScene.vue" "Three.js场景组件"
check_file "frontend/src/components/DataTable.vue" "数据表格组件"
check_file "frontend/src/components/StatsCards.vue" "统计卡片组件"
check_file "frontend/src/components/Navbar.vue" "导航栏组件"
check_file "frontend/src/components/Sidebar.vue" "侧边栏组件"

echo ""
echo "7. 检查数据库配置"
echo "-----------------------------------"
check_dir "database" "数据库目录"
check_file "database/init.sql" "数据库初始化脚本"

echo ""
echo "8. 检查脚本和工具"
echo "-----------------------------------"
check_dir "scripts" "脚本目录"
check_file "scripts/backup.sh" "数据库备份脚本"
check_file "scripts/restore.sh" "数据库恢复脚本"
check_file "scripts/generate_test_data.py" "测试数据生成脚本"

echo ""
echo "9. 检查文档"
echo "-----------------------------------"
check_dir "docs" "文档目录"
check_file "docs/development.md" "开发指南"
check_file "docs/user-guide.md" "用户指南"
check_file "CONFIGURATION_EXAMPLES.md" "配置示例"
check_file "PROJECT_SUMMARY.md" "项目总结"

echo ""
echo "==================================="
echo "验证结果统计"
echo "==================================="
echo -e "总检查项: ${TOTAL_CHECKS}"
echo -e "${GREEN}通过: ${PASSED_CHECKS}${NC}"
echo -e "${RED}失败: ${FAILED_CHECKS}${NC}"

if [ $FAILED_CHECKS -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 所有文件检查通过！项目结构完整。${NC}"
    echo ""
    echo "下一步操作："
    echo "  1. 启动服务: ./start.sh 或 start.bat"
    echo "  2. 访问前端: http://localhost:3000"
    echo "  3. 查看API文档: http://localhost:8000/docs"
    echo "  4. 配置ESP32: 修改 esp32_firmware/include/config.h"
    exit 0
else
    echo ""
    echo -e "${RED}✗ 发现 ${FAILED_CHECKS} 个问题，请检查上述错误。${NC}"
    exit 1
fi
