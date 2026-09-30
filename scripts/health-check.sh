#!/usr/bin/env bash

echo "=========================================="
echo "  MQTT云平台健康检查"
echo "=========================================="

PASS=0
FAIL=0

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

read_env() {
    local key=$1 default=$2 value=""
    if [ -f "$ENV_FILE" ]; then
        value="$(grep -E "^${key}=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '[:space:]')"
    fi
    echo "${value:-$default}"
}

DB_ROOT_PASSWORD="$(read_env DB_ROOT_PASSWORD root_password)"
DB_NAME="$(read_env DB_NAME mqtt_cloud)"
FRONTEND_PORT="$(read_env FRONTEND_PORT 80)"

pass() { echo -e "\033[0;32m✓ $1\033[0m"; PASS=$((PASS + 1)); }
fail() { echo -e "\033[0;31m✗ $1\033[0m"; FAIL=$((FAIL + 1)); }

check_container_health() {
    local name=$1 status
    status="$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || true)"
    echo -n "检查 $name ... "
    if [ "$status" = "healthy" ]; then
        pass "正常"
    else
        fail "异常（状态: ${status:-unknown}）"
    fi
}

check_http() {
    local name=$1 url=$2 expected=${3:-200} code
    code="$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null || true)"
    echo -n "检查 $name ... "
    if [ "$code" = "$expected" ]; then
        pass "正常（HTTP $code）"
    else
        fail "异常（HTTP ${code:-无响应}，期望 $expected）"
    fi
}

check_container_health mqtt-mysql
check_container_health mqtt-redis
check_container_health mqtt-emqx
# 后端不发布宿主机端口（多副本），经 nginx 网关探活
check_http "后端API（经网关）" "http://localhost:${FRONTEND_PORT}/api/health" "200"
check_http "前端" "http://localhost:${FRONTEND_PORT}/" "200"

# 表名必须逐个加单引号：写成裸标识符时 MySQL 会把它们当列名解析，
# 报 ERROR 1054 Unknown column（错误被 2>/dev/null 吞掉后恒判为 0/7）。
REQUIRED_TABLES="'sys_user','device','device_status_history','message','history_record','api_key','webhook_config'"
REQUIRED_COUNT=7

echo -n "检查数据库表 ... "
TABLE_COUNT="$(docker exec mqtt-mysql mysql -u root -p"$DB_ROOT_PASSWORD" -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name IN (${REQUIRED_TABLES});" \
    2>/dev/null | tr -d '[:space:]')"

# 查询失败（TABLE_COUNT 为空）与真查不到表必须区分，否则会掩盖脚本自身缺陷。
if [ -z "$TABLE_COUNT" ]; then
    fail "异常（查询失败，非表缺失）"
elif [ "$TABLE_COUNT" = "$REQUIRED_COUNT" ]; then
    pass "正常（${TABLE_COUNT}/${REQUIRED_COUNT} 张核心表）"
else
    fail "异常（${TABLE_COUNT:-0}/${REQUIRED_COUNT} 张核心表）"
fi

echo -n "检查默认管理员 ... "
ADMIN_COUNT="$(docker exec mqtt-mysql mysql -u root -p"$DB_ROOT_PASSWORD" "$DB_NAME" -N -B -e \
    "SELECT COUNT(*) FROM sys_user WHERE username='admin';" 2>/dev/null | tr -d '[:space:]')"
if [ "${ADMIN_COUNT:-0}" -ge 1 ]; then
    pass "正常"
else
    fail "异常（admin用户不存在）"
fi

echo ""
echo "=========================================="
echo "  健康检查结果"
echo "=========================================="
echo -e "通过：\033[0;32m$PASS\033[0m"
echo -e "失败：\033[0;31m$FAIL\033[0m"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo -e "\033[0;32m✓ 所有服务正常\033[0m"
    exit 0
fi

echo -e "\033[0;31m✗ 存在异常服务，请检查日志\033[0m"
echo ""
echo "查看日志："
for c in mqtt-mysql mqtt-redis mqtt-emqx mqtt-frontend; do
    echo "  docker logs $c"
done
echo "  docker compose --env-file .env -f docker/docker-compose.yml logs backend"
exit 1