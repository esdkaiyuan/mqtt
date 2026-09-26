#!/bin/bash
# MQTT Cloud Platform - Startup Script
set -e

echo "========================================"
echo "  MQTT Cloud Platform - Startup Script"
echo "========================================"
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java is not installed or not in PATH"
    exit 1
fi
echo "[OK] Java: $(java -version 2>&1 | head -1)"

# Check Mosquitto
if ! tasklist | grep -q "mosquitto.exe"; then
    echo "[WARN] Mosquitto is not running. Starting..."
    "/c/Program Files/Mosquitto/mosquitto.exe" -c "$(pwd)/mosquitto.conf" &
    sleep 3
else
    echo "[OK] Mosquitto is running"
fi

# Check MySQL
if ! tasklist | grep -q "mysqld.exe"; then
    echo "[WARN] MySQL is not running"
else
    echo "[OK] MySQL is running"
fi

# Check Redis
if ! tasklist | grep -q "redis-server.exe"; then
    echo "[WARN] Redis is not running"
else
    echo "[OK] Redis is running"
fi

# Kill existing backend on port 8080
if netstat -ano | grep -q ":8080.*LISTENING"; then
    echo "[INFO] Stopping existing backend..."
    PID=$(netstat -ano | grep ":8080.*LISTENING" | awk '{print $NF}' | head -1)
    taskkill //F //PID "$PID" 2>/dev/null || true
    sleep 2
fi

# Start backend
echo "[INFO] Starting Spring Boot backend..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$SCRIPT_DIR/backend/target/fat-jar-contents/BOOT-INF/classes"
for jar in "$SCRIPT_DIR/backend/target/fat-jar-contents/BOOT-INF/lib/"*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done
CLASSPATH="$CLASSPATH:/c/Users/28916/.m2/repository/org/projectlombok/lombok/1.18.32/lombok-1.18.32.jar"

java -cp "$CLASSPATH" com.mqtt.cloud.MqttCloudApplication --spring.profiles.active=dev > /tmp/mqtt-backend.log 2>&1 &
echo "[INFO] Backend PID: $!"

echo "[INFO] Waiting for backend to start..."
sleep 12

# Health check
if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo ""
    echo "========================================"
    echo "  System is ready!"
    echo "========================================"
    echo "  API:        http://localhost:8080/api"
    echo "  Health:     http://localhost:8080/api/health"
    echo "  MQTT TCP:   localhost:1883"
    echo "  MQTT WS:    localhost:8083"
    echo ""
    echo "  Login: admin / admin123"
    echo "========================================"
else
    echo "[ERROR] Backend failed to start. Check /tmp/mqtt-backend.log"
    exit 1
fi
