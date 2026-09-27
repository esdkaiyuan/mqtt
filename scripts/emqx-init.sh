#!/usr/bin/env sh
# 通过 EMQX REST API 下发 HTTP 认证与授权源配置。
# 幂等：先删除同名配置再重建，可重复执行。
# 运行环境为 curlimages/curl（Alpine + busybox sh），故仅使用 POSIX sh 语法。
set -eu

EMQX_API="${EMQX_API:-http://emqx:18083/api/v5}"
DASH_USER="${EMQX_DASHBOARD_USER:-admin}"
DASH_PASS="${EMQX_DASHBOARD_PASSWORD:-public}"
TOKEN="${INTERNAL_TOKEN:?INTERNAL_TOKEN 必须配置}"

# 后端 context-path 为 /api，内部接口实际路径为 /api/internal/**
BACKEND_AUTH_URL="${BACKEND_AUTH_URL:-http://backend:8080/api/internal/emqx/auth}"
BACKEND_ACL_URL="${BACKEND_ACL_URL:-http://backend:8080/api/internal/emqx/acl}"

api() {
  curl -fsS --connect-timeout 5 --max-time 20 -u "$DASH_USER:$DASH_PASS" "$@"
}

echo "等待 EMQX 就绪..."
ready=0
for _ in $(seq 1 60); do
  if api "$EMQX_API/status" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  echo "EMQX 未在预期时间内就绪，放弃配置" >&2
  exit 1
fi

# EMQX 5 中认证器 id 为 "{mechanism}:{backend}"，授权源 id 为其 type
echo "清理既有 HTTP 认证与授权源..."
api -X DELETE "$EMQX_API/authentication/password_based:http" >/dev/null 2>&1 || true
api -X DELETE "$EMQX_API/authorization/sources/http" >/dev/null 2>&1 || true

echo "配置 HTTP 认证..."
api -X POST "$EMQX_API/authentication" \
  -H 'Content-Type: application/json' \
  -d "{
        \"mechanism\": \"password_based\",
        \"backend\": \"http\",
        \"method\": \"post\",
        \"url\": \"$BACKEND_AUTH_URL\",
        \"headers\": { \"X-Internal-Token\": \"$TOKEN\" },
        \"body\": {
          \"username\": \"\${username}\",
          \"password\": \"\${password}\",
          \"clientid\": \"\${clientid}\"
        }
      }"

echo "配置 HTTP 授权源..."
api -X POST "$EMQX_API/authorization/sources" \
  -H 'Content-Type: application/json' \
  -d "{
        \"type\": \"http\",
        \"enable\": true,
        \"method\": \"post\",
        \"url\": \"$BACKEND_ACL_URL\",
        \"headers\": { \"X-Internal-Token\": \"$TOKEN\" },
        \"body\": {
          \"username\": \"\${username}\",
          \"topic\": \"\${topic}\",
          \"action\": \"\${action}\"
        }
      }"

echo "设置授权未匹配时默认拒绝..."
api -X PUT "$EMQX_API/authorization/settings" \
  -H 'Content-Type: application/json' \
  -d '{"no_match":"deny"}'

echo "EMQX 认证与授权配置完成"