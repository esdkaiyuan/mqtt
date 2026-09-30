#!/usr/bin/env bash
# R5 触发条件核对：按 docs/T-12_架构重构_实施计划.md「阶段 R5：触发式」的阈值，
# 采集当前数据量并逐项对照，输出报告，用于判断是否该启动 R5 组件。
#
# 用法：bash scripts/r5-trigger-check.sh
# 退出码：0=全部未达阈；1=存在接近阈值；2=存在已触发（可挂 cron 做告警）
#
# 可调环境变量：
#   QPS_WINDOW_SECONDS  上行 QPS 采样窗口（秒），默认 15
#   WARN_RATIO          接近阈值预警比例（%），默认 70

set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
MYSQL_CONTAINER="mqtt-mysql"

# ---- 阈值（与 T-12 R5 表格一致）----
TH_DEVICES=5000
TH_QPS=2000
TH_MESSAGE_ROWS=100000000
TH_BACKEND_LINES=20000

QPS_WINDOW_SECONDS="${QPS_WINDOW_SECONDS:-15}"
WARN_RATIO="${WARN_RATIO:-70}"

GREEN='\033[0;32m'; YELLOW='\033[0;33m'; RED='\033[0;31m'; GRAY='\033[0;90m'; NC='\033[0m'

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

db_scalar() {
    docker exec "$MYSQL_CONTAINER" mysql -u root -p"$DB_ROOT_PASSWORD" "$DB_NAME" -N -B -e "$1" 2>/dev/null | tr -d '[:space:]'
}

TRIGGERED=0; NEAR=0; BELOW=0; UNMEASURED=0

classify() {
    local label=$1 value=$2 threshold=$3 note=${4:-}
    if [ -z "$value" ]; then
        printf '%b[未采集]%b %s  实测 — / 阈值 %s（采集不可用）\n' "$GRAY" "$NC" "$label" "$threshold"
        UNMEASURED=$((UNMEASURED + 1))
        return
    fi
    local pct status color
    pct="$(awk -v v="$value" -v t="$threshold" 'BEGIN{ printf "%.1f", (t>0 ? v*100/t : 0) }')"
    if awk -v v="$value" -v t="$threshold" 'BEGIN{ exit !(v >= t) }'; then
        status="已触发"; color="$RED"; TRIGGERED=$((TRIGGERED + 1))
    elif awk -v v="$value" -v t="$threshold" -v r="$WARN_RATIO" 'BEGIN{ exit !(t>0 && v*100/t >= r) }'; then
        status="接近阈值"; color="$YELLOW"; NEAR=$((NEAR + 1))
    else
        status="未达阈"; color="$GREEN"; BELOW=$((BELOW + 1))
    fi
    printf '%b[%s]%b %s  实测 %s / 阈值 %s（%s%%）%s\n' \
        "$color" "$status" "$NC" "$label" "$value" "$threshold" "$pct" "$note"
}

echo "=========================================="
echo "  R5 触发条件核对"
echo "=========================================="
echo "采集时间：$(date '+%Y-%m-%d %H:%M:%S')"

# ---- 采集：数据库（设备数 / message 行数）----
DB_OK=0
if docker exec "$MYSQL_CONTAINER" mysql -u root -p"$DB_ROOT_PASSWORD" -N -B -e "SELECT 1" >/dev/null 2>&1; then
    DB_OK=1
fi

DEVICES=""; MESSAGE_ROWS=""
if [ "$DB_OK" = "1" ]; then
    DEVICES="$(db_scalar 'SELECT COUNT(*) FROM device;')"
    MESSAGE_ROWS="$(db_scalar 'SELECT COUNT(*) FROM message;')"
fi

# ---- 采集：上行 QPS ----
# 优先用后端已暴露的 ingest_submitted_total 计数器（增量 / 窗口）；端点不可达时
# 回退到 message 表近窗口行数（近似实际落库速率）。两者都取不到则记「未采集」。
PROM_URL="http://localhost:${FRONTEND_PORT}/api/actuator/prometheus"
QPS=""; QPS_SOURCE=""

read_ingest_counter() {
    curl -s --max-time 5 "$PROM_URL" 2>/dev/null | awk '/^ingest_submitted_total/ {print $NF; exit}'
}

C0="$(read_ingest_counter)"
if [ -n "$C0" ]; then
    sleep "$QPS_WINDOW_SECONDS"
    C1="$(read_ingest_counter)"
    if [ -n "$C1" ]; then
        QPS="$(awk -v a="$C0" -v b="$C1" -v w="$QPS_WINDOW_SECONDS" 'BEGIN{ printf "%.1f", (b-a)/w }')"
        QPS_SOURCE="，来源 ingest_submitted_total 增量/${QPS_WINDOW_SECONDS}s"
    fi
fi

if [ -z "$QPS" ] && [ "$DB_OK" = "1" ]; then
    RECENT="$(db_scalar "SELECT COUNT(*) FROM message WHERE sent_at > NOW() - INTERVAL ${QPS_WINDOW_SECONDS} SECOND;")"
    if [ -n "$RECENT" ]; then
        QPS="$(awk -v n="$RECENT" -v w="$QPS_WINDOW_SECONDS" 'BEGIN{ printf "%.1f", n/w }')"
        QPS_SOURCE="，来源 message 表近 ${QPS_WINDOW_SECONDS}s 行数"
    fi
fi

# ---- 采集：后端代码行数（本地源码统计，不依赖运行中的栈）----
# 口径：backend/src/main/java 下 .java 文件的「非空行数」合计。
# 不用 `cat | wc -l`：文件末尾缺换行时 wc 会少算，且会把上一文件末行与下一文件首行并成一行。
BACKEND_LINES=""
if [ -d "$ROOT_DIR/backend/src/main/java" ]; then
    BACKEND_LINES="$(find "$ROOT_DIR/backend/src/main/java" -type f -name '*.java' \
        -exec awk 'NF>0 {n++} END {print n}' {} + 2>/dev/null | awk '{s+=$1} END {print s}')"
fi

echo ""
echo "------------------------------------------"
echo "  量化条件"
echo "------------------------------------------"
classify "消息队列 · 设备数" "$DEVICES" "$TH_DEVICES"
classify "消息队列 · 上行 QPS" "$QPS" "$TH_QPS" "$QPS_SOURCE"
classify "时序库 · message 表行数" "$MESSAGE_ROWS" "$TH_MESSAGE_ROWS"
classify "物理多模块 · 后端代码行数" "$BACKEND_LINES" "$TH_BACKEND_LINES"
# 历史查询 P99 当前无指标暴露，需用压测采集；不在此脚本量化
classify "时序库 · 历史查询 P99（ms）" "" "1000"

echo ""
echo "------------------------------------------"
echo "  需求型条件（非数据量，需人工判定）"
echo "------------------------------------------"
printf '%b[人工判定]%b 消息队列 · 跨重启不丢消息\n' "$GRAY" "$NC"
printf '%b[人工判定]%b 消息队列 · 严格单设备有序\n' "$GRAY" "$NC"
printf '%b[人工判定]%b 独立抓取/告警体系 · 需要 SLA 与值班\n' "$GRAY" "$NC"

echo ""
echo "=========================================="
echo "  结论"
echo "=========================================="
echo "已触发 $TRIGGERED 项 / 接近阈值 $NEAR 项 / 未达阈 $BELOW 项 / 未采集 $UNMEASURED 项"
echo ""

if [ "$TRIGGERED" -gt 0 ]; then
    echo -e "${RED}✗ 存在 $TRIGGERED 项触发条件已达标，建议启动对应 R5 组件（见 docs/T-12_架构重构_实施计划.md §阶段 R5）${NC}"
    exit 2
fi

if [ "$NEAR" -gt 0 ]; then
    echo -e "${YELLOW}! 存在 $NEAR 项接近阈值（≥${WARN_RATIO}%），建议提前评估${NC}"
    exit 1
fi

echo -e "${GREEN}✓ R5 各项量化触发条件均未达阈，继续推迟${NC}"
exit 0