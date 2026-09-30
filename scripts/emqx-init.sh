#!/usr/bin/env sh
# 通过 EMQX REST API 下发 HTTP 认证与授权源配置。
# 幂等：先删除同名配置再重建，可重复执行。
# 运行环境为 curlimages/curl（Alpine + busybox sh），故仅使用 POSIX sh 语法。
#
# EMQX 5 的 REST API 不接受 Basic 认证（一律返回 401），必须先 POST /api/v5/login
# 用 Dashboard 账号换取 Bearer token，后续请求统一携带 Authorization: Bearer <token>。
#
# 关键点：EMQX 默认自带一个 file 授权源，其 acl.conf 末尾是 {allow, all}，
# 会在授权链中直接放行、短路其后的 HTTP 授权源（官方文档亦提示需禁用或移除）。
# 因此本脚本显式禁用 file 授权源，使授权决策完全由后端 HTTP 回调裁决。
set -eu

EMQX_API="${EMQX_API:-http://emqx:18083/api/v5}"
DASH_USER="${EMQX_DASHBOARD_USER:-admin}"
DASH_PASS="${EMQX_DASHBOARD_PASSWORD:-public}"
INTERNAL="${INTERNAL_TOKEN:?INTERNAL_TOKEN 必须配置}"

# 授权结果缓存 TTL。EMQX 默认 1m，会在设备禁用/产品停用后继续放行已连接会话的收发
# 最长 1m（缓存不随后端主动失效而清除）。收紧到 10s，把「禁用生效」的收敛窗口压到秒级；
# 代价是后端 ACL 回调次数相应增加（该路径只做一次 Redis 读，成本低）。
ACL_CACHE_TTL="${ACL_CACHE_TTL:-10s}"

# 后端 context-path 为 /api，内部接口实际路径为 /api/internal/**
BACKEND_AUTH_URL="${BACKEND_AUTH_URL:-http://backend:8080/api/internal/emqx/auth}"
BACKEND_ACL_URL="${BACKEND_ACL_URL:-http://backend:8080/api/internal/emqx/acl}"

# file 授权源被禁用后其 rules 不再参与裁决；此处给一个 fail-closed 的终态规则，
# 万一被误重新启用，也是拒绝而非放行。
FILE_DISABLED_RULES='{deny, all}.'

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

# 取 JSON 中首个字符串字段的值（不含引号）
json_str() {
  grep -o "\"$2\":\"[^\"]*\"" "$1" | head -1 | sed "s/^\"$2\":\"//; s/\"\$//"
}

# 取 JSON 中首个布尔字段的值（true/false）。
# 必须取首个：认证器/授权源的响应里嵌套的 ssl 对象也有 enable 字段。
json_bool() {
  grep -oE "\"$2\":(true|false)" "$1" | head -1 | sed "s/^\"$2\"://"
}

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
LOGIN_RESP="$WORKDIR/login.json"
curl -fsS --connect-timeout 5 --max-time 20 -X POST "$EMQX_API/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DASH_USER\",\"password\":\"$DASH_PASS\"}" \
  -o "$LOGIN_RESP"
API_TOKEN=$(json_str "$LOGIN_RESP" token)
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

# 授权源按顺序求值，任一源给出明确 allow/deny 即短路后续源。
# 内置 file 源末尾的 {allow, all} 会让所有请求在到达 HTTP 源之前就被放行，
# 因此必须禁用它，否则后端 ACL 拒绝决策永远不生效。
# 注意：file 源 schema 必填 rules（PUT 不带 rules 会 400），且不接受 path 字段。
echo "禁用内置 file 授权源（其 {allow, all} 兜底会短路 HTTP 授权）..."
api -X PUT "$EMQX_API/authorization/sources/file" \
  -H 'Content-Type: application/json' \
  -d "{\"type\":\"file\",\"enable\":false,\"rules\":\"$FILE_DISABLED_RULES\"}"

# EMQX 5.0 该接口要求 settings 对象字段齐全（no_match / deny_action / cache 缺一即 400）
echo "设置授权未匹配时默认拒绝，并收紧授权缓存 TTL..."
api -X PUT "$EMQX_API/authorization/settings" \
  -H 'Content-Type: application/json' \
  -d "{\"no_match\":\"deny\",\"deny_action\":\"ignore\",\"cache\":{\"enable\":true,\"max_size\":32,\"ttl\":\"$ACL_CACHE_TTL\"}}"

echo "回读校验配置是否真正生效..."

# 1) HTTP 认证器必须存在且启用
AUTH_RESP="$WORKDIR/auth.json"
api -X GET "$EMQX_API/authentication/password_based:http" -o "$AUTH_RESP"
AUTH_ENABLE=$(json_bool "$AUTH_RESP" enable)
if [ "$AUTH_ENABLE" != "true" ]; then
  echo "HTTP 认证器未启用：enable=$AUTH_ENABLE（预期 true）" >&2
  exit 1
fi

# 2) HTTP 授权源必须启用
ACL_RESP="$WORKDIR/acl.json"
api -X GET "$EMQX_API/authorization/sources/http" -o "$ACL_RESP"
ACL_ENABLE=$(json_bool "$ACL_RESP" enable)
if [ "$ACL_ENABLE" != "true" ]; then
  echo "HTTP 授权源未启用：enable=$ACL_ENABLE（预期 true）" >&2
  exit 1
fi

# 3) file 授权源必须已禁用，否则 {allow, all} 会覆盖 HTTP 决策
FILE_RESP="$WORKDIR/file.json"
api -X GET "$EMQX_API/authorization/sources/file" -o "$FILE_RESP"
FILE_ENABLE=$(json_bool "$FILE_RESP" enable)
if [ "$FILE_ENABLE" != "false" ]; then
  echo "file 授权源仍处于启用状态：enable=$FILE_ENABLE（预期 false），授权兜底未收敛" >&2
  exit 1
fi

# 4) 授权未匹配时必须拒绝（而非默认 allow）
SETTINGS_RESP="$WORKDIR/settings.json"
api -X GET "$EMQX_API/authorization/settings" -o "$SETTINGS_RESP"
NO_MATCH=$(json_str "$SETTINGS_RESP" no_match)
if [ "$NO_MATCH" != "deny" ]; then
  echo "授权设置未生效：no_match=$NO_MATCH（预期 deny）" >&2
  exit 1
fi

# 5) 授权缓存 TTL 必须已收紧，否则禁用/停用后的收发会在缓存窗口内继续放行
CACHE_TTL=$(json_str "$SETTINGS_RESP" ttl)
if [ "$CACHE_TTL" != "$ACL_CACHE_TTL" ]; then
  echo "授权缓存 TTL 未生效：ttl=$CACHE_TTL（预期 $ACL_CACHE_TTL）" >&2
  exit 1
fi

echo "EMQX 认证与授权配置完成，且回读校验通过"