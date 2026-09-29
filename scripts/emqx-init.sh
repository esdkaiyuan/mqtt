#!/usr/bin/env sh
# 通过 EMQX REST API 下发 HTTP 认证与授权源配置。
# 幂等：先删除同名配置再重建，可重复执行。
# 运行环境为 curlimages/curl（Alpine + busybox sh），故仅使用 POSIX sh 语法。
#
# EMQX 5 的 REST API 不接受 Basic 认证（一律返回 401），必须先 POST /api/v5/login
# 用 Dashboard 账号换取 Bearer token，后续请求统一携带 Authorization: Bearer <token>。
set -eu

EMQX_API="${EMQX_API:-http://emqx:18083/api/v5}"
DASH_USER="${EMQX_DASHBOARD_USER:-admin}"
DASH_PASS="${EMQX_DASHBOARD_PASSWORD:-public}"
INTERNAL="${INTERNAL_TOKEN:?INTERNAL_TOKEN 必须配置}"

# 后端 context-path 为 /api，内部接口实际路径为 /api/internal/**
BACKEND_AUTH_URL="${BACKEND_AUTH_URL:-http://backend:8080/api/internal/emqx/auth}"
BACKEND_ACL_URL="${BACKEND_ACL_URL:-http://backend:8080/api/internal/emqx/acl}"

echo "等待 EMQX 就绪..."
ready=0
for _ in $(seq 1 60); do
  # /status 是公开端点，无需认证
  if curl -fsS --connect-timeout 5 --max-time 10 "$EMQX_API/status" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  echo "EMQX 未在预期时间内就绪，放弃配置" >&2
  exit 1
fi

echo "登录 EMQX Dashboard 获取 token..."
LOGIN_RESP=$(mktemp)
trap 'rm -f "$LOGIN_RESP"' EXIT
curl -fsS --connect-timeout 5 --max-time 20 -X POST "$EMQX_API/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DASH_USER\",\"password\":\"$DASH_PASS\"}" \
  -o "$LOGIN_RESP"
API_TOKEN=$(sed -n 's/.*"token":"\([^"]*\)".*/\1/p' "$LOGIN_RESP")
if [ -z "$API_TOKEN" ]; then
  echo "登录 EMQX Dashboard 失败，未取到 token（检查 EMQX_DASHBOARD_USER / EMQX_DASHBOARD_PASSWORD）" >&2
  exit 1
fi

api() {
  curl -fsS --connect-timeout 5 --max-time 20 -H "Authorization: Bearer $API_TOKEN" "$@"
}

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
        \"headers\": { \"X-Internal-Token\": \"$INTERNAL\" },
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
        \"headers\": { \"X-Internal-Token\": \"$INTERNAL\" },
        \"body\": {
          \"username\": \"\${username}\",
          \"topic\": \"\${topic}\",
          \"action\": \"\${action}\"
        }
      }"

# EMQX 5.0 该接口要求 settings 对象字段齐全（no_match / deny_action / cache 缺一即 400）
echo "设置授权未匹配时默认拒绝..."
api -X PUT "$EMQX_API/authorization/settings" \
  -H 'Content-Type: application/json' \
  -d '{"no_match":"deny","deny_action":"ignore","cache":{"enable":true,"max_size":32,"ttl":"1m"}}'

echo "EMQX 认证与授权配置完成"