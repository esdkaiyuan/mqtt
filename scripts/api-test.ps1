<#
.SYNOPSIS
    MQTT 云平台后端接口集成测试（T-09）。

.DESCRIPTION
    覆盖认证、产品、设备、消息、历史、统计分析、API Key、Webhook、外部开放接口，
    以及 T-11 的产品模板、一机一密凭据下发/重置/导出、内部回调令牌校验等路径。

    依赖：本机已启动后端（默认 http://127.0.0.1:18080/api）、MySQL、Redis，
    以及可连接的 MQTT Broker（默认 tcp://localhost:1883）。
    退出码：全部通过为 0，存在失败为失败用例数。

.PARAMETER Base
    后端 API 基地址，默认 http://127.0.0.1:18080/api

.PARAMETER BehindGateway
    当 -Base 指向 nginx 网关（而非直连后端）时置位。
    网关对 /internal/emqx/* 使用 deny all，无令牌请求在网关层即返回 403、
    不会到达后端，故 P8/P9 的期望放宽为 401/403。

.PARAMETER SelfBase
    Webhook 正向投递的目标基地址，必须由「后端进程自身」可达，默认取 -Base。
    经网关执行时后端在容器内，须显式指定后端自身地址（如 http://localhost:8080/api），
    否则目标 http://127.0.0.1/api/auth/logout 在容器内指向容器自身而非网关。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/api-test.ps1
    powershell -ExecutionPolicy Bypass -File scripts/api-test.ps1 -Base http://127.0.0.1:8080/api
    pwsh -File scripts/api-test.ps1 -Base http://127.0.0.1/api -BehindGateway -SelfBase http://localhost:8080/api
#>
param(
    [string]$Base = 'http://127.0.0.1:18080/api',
    [switch]$BehindGateway,
    [string]$SelfBase = ''
)

$ErrorActionPreference = 'Continue'
$BASE = $Base.TrimEnd('/')
$SELF_BASE = $(if ($SelfBase) { $SelfBase.TrimEnd('/') } else { $BASE })

$TMPDIR = Join-Path ([System.IO.Path]::GetTempPath()) 'mqtt-cloud-api-test'
New-Item -ItemType Directory -Force -Path $TMPDIR | Out-Null
$BODYFILE = Join-Path $TMPDIR 'resp.json'
$JSONFILE = Join-Path $TMPDIR 'req.json'

$script:PASS = 0
$script:FAIL = 0
$script:ROWS = @()

<#
    统一的请求封装。
    注意：查询参数一律通过 curl 的 -G/--data-urlencode 传递，
    不在 URL 字符串里拼 "&"，避免 PowerShell 调用原生 exe 时参数被拆分的坑。
#>
function Call {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token = $null,
        [string]$ApiKey = $null,
        [hashtable]$Query = $null
    )

    $a = @('-s', '-o', $BODYFILE, '-w', '%{http_code}', '-X', $Method, "$BASE$Path")

    if ($Query) {
        $a += '-G'
        foreach ($k in $Query.Keys) {
            $a += @('--data-urlencode', "$k=$($Query[$k])")
        }
    }
    if ($Token) { $a += @('-H', "Authorization: Bearer $Token") }
    if ($ApiKey) { $a += @('-H', "X-API-Key: $ApiKey") }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 8 -Compress
        [System.IO.File]::WriteAllText($JSONFILE, $json, (New-Object System.Text.UTF8Encoding($false)))
        $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$JSONFILE")
    }

    $code = (& curl.exe @a 2>$null | Out-String).Trim()
    $body = ''
    if (Test-Path $BODYFILE) { $body = [System.IO.File]::ReadAllText($BODYFILE) }
    return @{ http = [int]$code; body = $body }
}

function J {
    param($Result)
    if ($Result.body) {
        try { return ($Result.body | ConvertFrom-Json) } catch { return $null }
    }
    return $null
}

function Check {
    param(
        [string]$Id,
        [string]$Desc,
        $Result,
        [int[]]$ExpectHttp,
        [string]$ExpectCode = $null
    )
    $code = $null
    if ($Result.body) {
        try { $code = ($Result.body | ConvertFrom-Json).code } catch { }
    }
    $ok = ($ExpectHttp -contains $Result.http)
    if ($ExpectCode -and "$code" -ne "$ExpectCode") { $ok = $false }
    if ($ok) { $script:PASS++ } else { $script:FAIL++ }
    $script:ROWS += [pscustomobject]@{
        Id = $Id; Desc = $Desc; Http = $Result.http; ExpHttp = ($ExpectHttp -join '/')
        Code = $code; ExpCode = $ExpectCode
        Result = $(if ($ok) { 'PASS' } else { 'FAIL' })
    }
    return $Result
}

Write-Host '=========== T-09 API Integration Test ===========' -ForegroundColor Cyan
Write-Host "target: $BASE`n"

$suffix = Get-Random -Minimum 10000 -Maximum 99999

# ---------- 认证 ----------
$r = Check '1' 'POST /auth/register' (Call -Method POST -Path '/auth/register' -Body @{ username = "t09user$suffix"; password = 'Passw0rd!23'; email = "t09user$suffix@test.local" }) 200 200
$r = Check '2' 'POST /auth/login (admin)' (Call -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'admin123' }) 200 200
$TOKEN = (J $r).data.token
$r = Check '3' 'GET /auth/current' (Call -Method GET -Path '/auth/current' -Token $TOKEN) 200 200
$r = Check '4' 'POST /auth/change-password' (Call -Method POST -Path '/auth/change-password' -Body @{ oldPassword = 'admin123'; newPassword = 'admin1234' } -Token $TOKEN) 200 200
$r = Check '4b' 'POST /auth/change-password (restore)' (Call -Method POST -Path '/auth/change-password' -Body @{ oldPassword = 'admin1234'; newPassword = 'admin123' } -Token $TOKEN) 200 200
$r = Check '5' 'POST /auth/logout' (Call -Method POST -Path '/auth/logout' -Token $TOKEN) 200 200
Start-Sleep -Seconds 2
$r = Check '5b' 'POST /auth/login (re-login after logout)' (Call -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'admin123' }) 200 200
$TOKEN = (J $r).data.token
Write-Host "  [info] re-login token length = $($TOKEN.Length)"

# ---------- 产品（T-11：设备类型模板） ----------
$pk = "t11-prod-$suffix"
$r = Check 'P1' 'POST /products' (Call -Method POST -Path '/products' -Body @{ productKey = $pk; productName = "T11产品$suffix"; topicPrefix = 'device/{deviceKey}'; payloadFormat = 'JSON' } -Token $TOKEN) 200 200
$prodId = (J $r).data.id
$r = Check 'P2' 'GET /products' (Call -Method GET -Path '/products' -Token $TOKEN) 200 200
$r = Check 'P3' 'POST /products (duplicate key -> 409/6001)' (Call -Method POST -Path '/products' -Body @{ productKey = $pk; productName = '重复产品' } -Token $TOKEN) 409 6001
$r = Check 'P4' 'PUT /products/{id}' (Call -Method PUT -Path "/products/$prodId" -Body @{ productKey = $pk; productName = "T11产品改名$suffix" } -Token $TOKEN) 200 200

# ---------- 设备 ----------
$dk = "t09-dev-$suffix"
$r = Check '6' 'POST /devices' (Call -Method POST -Path '/devices' -Body @{ productId = $prodId; deviceName = "T09设备$suffix"; deviceKey = $dk; deviceType = 'sensor'; topic = "device/$dk/data" } -Token $TOKEN) 200 200
$devId = (J $r).data.id
$devCred = J $r
$credOk = [bool]($devCred.data.deviceSecret) -and ($devCred.data.username -eq "$pk.$dk")
if ($credOk) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = '6b'; Desc = 'POST /devices returns one-time credential (username=productKey.deviceKey)'; Http = $r.http; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($credOk) { 'PASS' } else { 'FAIL' }) }
$r = Check '7' 'GET /devices' (Call -Method GET -Path '/devices' -Token $TOKEN -Query @{ pageNum = 1; pageSize = 10 }) 200 200
$r = Check '8' 'GET /devices/{id}' (Call -Method GET -Path "/devices/$devId" -Token $TOKEN) 200 200
$r = Check '9' 'PUT /devices/{id}' (Call -Method PUT -Path "/devices/$devId" -Body @{ deviceName = "T09设备改名$suffix"; deviceType = 'sensor'; topic = "device/$dk/data" } -Token $TOKEN) 200 200
$r = Check '11' 'GET /devices/online' (Call -Method GET -Path '/devices/online' -Token $TOKEN) 200 200
$r = Check '12' 'GET /devices/{id}/status' (Call -Method GET -Path "/devices/$devId/status" -Token $TOKEN) 200 200

# ---------- 设备禁用 / 启用（R3-4） ----------
$r = Check 'D1' 'POST /devices/{id}/disable' (Call -Method POST -Path "/devices/$devId/disable" -Token $TOKEN) 200 200
$r = Call -Method GET -Path "/devices/$devId" -Token $TOKEN
$devDisabled = ((J $r).data.enabled -eq 0)
if ($devDisabled) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = 'D2'; Desc = 'GET /devices/{id} shows enabled=0 after disable'; Http = $r.http; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($devDisabled) { 'PASS' } else { 'FAIL' }) }
$r = Check 'D3' 'POST /devices/{id}/disable again (idempotent)' (Call -Method POST -Path "/devices/$devId/disable" -Token $TOKEN) 200 200
$r = Check 'D4' 'POST /devices/{id}/enable' (Call -Method POST -Path "/devices/$devId/enable" -Token $TOKEN) 200 200
$r = Call -Method GET -Path "/devices/$devId" -Token $TOKEN
$devEnabled = ((J $r).data.enabled -eq 1)
if ($devEnabled) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = 'D5'; Desc = 'GET /devices/{id} shows enabled=1 after enable'; Http = $r.http; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($devEnabled) { 'PASS' } else { 'FAIL' }) }

# ---------- 产品停用 / 启用（R3-4） ----------
$r = Check 'D6' 'POST /products/{id}/disable' (Call -Method POST -Path "/products/$prodId/disable" -Token $TOKEN) 200 200
$r = Call -Method GET -Path '/products' -Token $TOKEN
$prodDisabled = @((J $r).data | Where-Object { $_.id -eq $prodId })[0].status -eq 'DISABLED'
if ($prodDisabled) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = 'D7'; Desc = 'GET /products shows status=DISABLED after disable'; Http = $r.http; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($prodDisabled) { 'PASS' } else { 'FAIL' }) }
$r = Check 'D8' 'POST /products/{id}/disable again (idempotent)' (Call -Method POST -Path "/products/$prodId/disable" -Token $TOKEN) 200 200
$r = Check 'D9' 'POST /products/{id}/enable' (Call -Method POST -Path "/products/$prodId/enable" -Token $TOKEN) 200 200

# ---------- 产品删除保护 / 凭据管理 / 内部接口令牌（T-11） ----------
$r = Check 'P5' 'DELETE /products/{id} with devices -> 409/6003' (Call -Method DELETE -Path "/products/$prodId" -Token $TOKEN) 409 6003
$r = Check 'P6' 'POST /devices/{id}/reset-secret' (Call -Method POST -Path "/devices/$devId/reset-secret" -Token $TOKEN) 200 200
$r = Check 'P7' 'POST /devices/export-credentials' (Call -Method POST -Path '/devices/export-credentials' -Token $TOKEN) 200 200
# 无令牌访问内部回调：直连后端时由内部令牌过滤器返回 401；经 nginx 网关时由
# `location /api/internal/ { deny all; return 403; }` 在网关层返回 403、请求不到达后端。
# 两者都表示「外部无凭据不可用」，故经网关执行时接受 401/403。
$internalExpect = $(if ($BehindGateway) { @(401, 403) } else { @(401) })
$r = Check 'P8' "POST /internal/emqx/auth without token -> $($internalExpect -join '/')" (Call -Method POST -Path '/internal/emqx/auth' -Body @{ username = 'x'; password = 'y' }) $internalExpect
$r = Check 'P9' "POST /internal/emqx/acl without token -> $($internalExpect -join '/')" (Call -Method POST -Path '/internal/emqx/acl' -Body @{ username = 'x'; action = 'publish'; topic = 'device/x/data' }) $internalExpect

# ---------- 消息 ----------
$r = Check '13' 'POST /messages/publish' (Call -Method POST -Path '/messages/publish' -Body @{ topic = "device/$dk/data"; payload = '{"temp":26.5}'; qos = 1 } -Token $TOKEN) 200 200

# 13b：发布 -> Broker -> 订阅 -> 入库 的端到端验证（异步投递，做有限重试）
$rtOk = $false
$rtHttp = 0
for ($i = 0; $i -lt 12; $i++) {
    $rr = Call -Method GET -Path '/messages/recent' -Token $TOKEN -Query @{ limit = 100 }
    $rtHttp = $rr.http
    if ($rr.body -and $rr.body.Contains("device/$dk/data")) { $rtOk = $true; break }
    Start-Sleep -Milliseconds 400
}
if ($rtOk) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = '13b'; Desc = 'Published topic visible in /messages/recent'; Http = $rtHttp; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($rtOk) { 'PASS' } else { 'FAIL' }) }

$r = Check '14' 'GET /messages' (Call -Method GET -Path '/messages' -Token $TOKEN -Query @{ pageNum = 1; pageSize = 10 }) 200 200
$r = Check '15' 'GET /messages/recent' (Call -Method GET -Path '/messages/recent' -Token $TOKEN -Query @{ limit = 10 }) 200 200
$r = Check '16' 'GET /messages/trend' (Call -Method GET -Path '/messages/trend' -Token $TOKEN -Query @{ days = 7 }) 200 200

# ---------- 历史 ----------
$r = Check '17' 'GET /history' (Call -Method GET -Path '/history' -Token $TOKEN -Query @{ pageNum = 1; pageSize = 10 }) 200 200
$r = Check '18' 'GET /history/device/{id}' (Call -Method GET -Path "/history/device/$devId" -Token $TOKEN -Query @{ pageNum = 1; pageSize = 10 }) 200 200

# ---------- 健康检查 ----------
$r = Call -Method GET -Path '/health'
$okH = ($r.http -eq 200 -and ($r.body -match '"status":"UP"'))
if ($okH) { $script:PASS++ } else { $script:FAIL++ }
$script:ROWS += [pscustomobject]@{ Id = '19'; Desc = 'GET /health (status=UP)'; Http = $r.http; ExpHttp = 200; Code = $null; ExpCode = $null; Result = $(if ($okH) { 'PASS' } else { 'FAIL' }) }

# ---------- 统计分析 ----------
$r = Check '20' 'GET /analytics/overview' (Call -Method GET -Path '/analytics/overview' -Token $TOKEN) 200 200
$r = Check '21' 'GET /analytics/devices/status' (Call -Method GET -Path '/analytics/devices/status' -Token $TOKEN) 200 200
$r = Check '22' 'GET /analytics/devices/type' (Call -Method GET -Path '/analytics/devices/type' -Token $TOKEN) 200 200
$r = Check '23' 'GET /analytics/messages/trend' (Call -Method GET -Path '/analytics/messages/trend' -Token $TOKEN -Query @{ days = 7 }) 200 200

# ---------- API Key ----------
$r = Check '24' 'GET /api-keys' (Call -Method GET -Path '/api-keys' -Token $TOKEN) 200 200
$r = Check '25' 'POST /api-keys' (Call -Method POST -Path '/api-keys' -Body @{ name = "t09-key-$suffix"; permissions = '["device:read","device:write"]' } -Token $TOKEN) 200 200
$keyId = (J $r).data.id
$APIKEY = (J $r).data.keyValue

# ---------- Webhook ----------
$r = Check '28' 'GET /webhooks' (Call -Method GET -Path '/webhooks' -Token $TOKEN) 200 200
$r = Check '29' 'POST /webhooks' (Call -Method POST -Path '/webhooks' -Body @{ name = "t09-hook-$suffix"; url = 'http://127.0.0.1:1/hook'; events = '["device.data"]' } -Token $TOKEN) 200 200
$hookId = (J $r).data.id
$r = Check '30' 'GET /webhooks/{id}' (Call -Method GET -Path "/webhooks/$hookId" -Token $TOKEN) 200 200
$r = Check '31' 'PUT /webhooks/{id}' (Call -Method PUT -Path "/webhooks/$hookId" -Body @{ name = "t09-hook-upd-$suffix"; url = 'http://127.0.0.1:1/hook2'; events = '["device.data"]' } -Token $TOKEN) 200 200
$r = Check '33' 'POST /webhooks/{id}/test (unreachable -> 502/5001)' (Call -Method POST -Path "/webhooks/$hookId/test" -Token $TOKEN) 502 5001
$r = Check '32' 'DELETE /webhooks/{id}' (Call -Method DELETE -Path "/webhooks/$hookId" -Token $TOKEN) 200 200

# Webhook 正向投递：以本服务 /auth/logout 作为可达且返回 2xx 的 POST 目标，
# 通过自定义请求头带上一个专用 Token（登出只会失效该 Token，不影响 $TOKEN）。
# 目标必须由「后端进程自身」可达：直连时即 $BASE；经网关时后端在容器内，
# 需用 $SELF_BASE（见 -SelfBase 参数说明）。
$hookToken = (J (Call -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'admin123' })).data.token
$hookHeaders = @{ Authorization = "Bearer $hookToken" } | ConvertTo-Json -Compress
$r = Check '33a' 'POST /webhooks (reachable 2xx target)' (Call -Method POST -Path '/webhooks' -Body @{ name = "t09-hook-ok-$suffix"; url = "$SELF_BASE/auth/logout"; events = '["device.data"]'; headers = $hookHeaders } -Token $TOKEN) 200 200
$hookOkId = (J $r).data.id
$r = Check '33b' 'POST /webhooks/{id}/test (2xx -> 200)' (Call -Method POST -Path "/webhooks/$hookOkId/test" -Token $TOKEN) 200 200
$r = Check '33c' 'DELETE /webhooks/{id} (reachable target)' (Call -Method DELETE -Path "/webhooks/$hookOkId" -Token $TOKEN) 200 200

# ---------- 外部开放接口（X-API-Key） ----------
$r = Check '34' 'GET /external/v1/devices' (Call -Method GET -Path '/external/v1/devices' -ApiKey $APIKEY) 200 200
$r = Check '35' 'GET /external/v1/devices/{key}' (Call -Method GET -Path "/external/v1/devices/$dk" -ApiKey $APIKEY) 200 200
$r = Check '37' 'GET /external/v1/devices/{key}/status' (Call -Method GET -Path "/external/v1/devices/$dk/status" -ApiKey $APIKEY) 200 200
# 命令下发依赖物模型：先给产品写入含 rw 属性的最小 TSL，再验新契约
$tsl = '{"schemaVersion":"1.0","properties":[{"identifier":"power","name":"开关","dataType":{"type":"bool"},"accessMode":"rw"}],"events":[],"services":[]}'
$r = Check '36a' 'PUT /products/{id}/thing-model (setup for command)' (Call -Method PUT -Path "/products/$prodId/thing-model" -Body @{ thingModel = $tsl } -Token $TOKEN) 200 200
$r = Check '36' 'POST /external/v1/devices/{key}/command (type/params/callType)' (Call -Method POST -Path "/external/v1/devices/$dk/command" -Body @{ type = 'property_set'; params = @{ power = $true }; callType = 'async' } -ApiKey $APIKEY) 200 200
$r = Check '36b' 'POST command with unknown identifier -> 400/6203' (Call -Method POST -Path "/external/v1/devices/$dk/command" -Body @{ type = 'property_set'; params = @{ nope = 1 }; callType = 'async' } -ApiKey $APIKEY) 400 6203
$r = Check '38' 'GET /external/v1/stats' (Call -Method GET -Path '/external/v1/stats' -ApiKey $APIKEY) 200 200

# ---------- 异常路径 ----------
$r = Check 'E1' 'No token GET /devices -> 401/401' (Call -Method GET -Path '/devices') 401 401
$r = Check 'E2' 'Invalid token GET /devices -> 401/3003' (Call -Method GET -Path '/devices' -Token 'not.a.valid.token') 401 3003
$r = Check 'E3' 'Duplicate username register -> 409/1001' (Call -Method POST -Path '/auth/register' -Body @{ username = 'admin'; password = 'Passw0rd!23' }) 409 1001
$r = Check 'E4' 'Wrong password login -> 401/3001' (Call -Method POST -Path '/auth/login' -Body @{ username = 'admin'; password = 'definitely-wrong' }) 401 3001
$r = Check 'E6' 'Duplicate deviceKey in same product -> 409/2001' (Call -Method POST -Path '/devices' -Body @{ productId = $prodId; deviceName = 'dup'; deviceKey = $dk; deviceType = 'sensor'; topic = "device/$dk/data" } -Token $TOKEN) 409 2001
$r = Check 'E6b' 'Create device with unknown product -> 404/6002' (Call -Method POST -Path '/devices' -Body @{ productId = 99999999; deviceName = 'noprod'; deviceKey = "noprod-$suffix"; deviceType = 'sensor'; topic = "device/noprod-$suffix/data" } -Token $TOKEN) 404 6002
$r = Check 'E7' 'Invalid API Key -> 401/3501' (Call -Method GET -Path '/external/v1/devices' -ApiKey 'deadbeef') 401 3501
$r = Check 'E10' 'Unmapped route -> 404/404' (Call -Method GET -Path '/no-such-endpoint' -Token $TOKEN) 404 404

# ---------- 越权隔离（userB 访问 userA 资源） ----------
$userB = "t09usb$suffix"
$null = Call -Method POST -Path '/auth/register' -Body @{ username = $userB; password = 'Passw0rd!23' }
$rb = Call -Method POST -Path '/auth/login' -Body @{ username = $userB; password = 'Passw0rd!23' }
$TOKENB = (J $rb).data.token
$r = Check 'E5' 'userB reads userA device -> 403/2003' (Call -Method GET -Path "/devices/$devId" -Token $TOKENB) 403 2003
$r = Check 'E11' 'userB disables userA device -> 403/2003' (Call -Method POST -Path "/devices/$devId/disable" -Token $TOKENB) 403 2003
$r = Check 'E12' 'userB disables product (ADMIN only) -> 403/403' (Call -Method POST -Path "/products/$prodId/disable" -Token $TOKENB) 403 403
$r = Check 'E8' 'userB deletes userA api key -> 403/403' (Call -Method DELETE -Path "/api-keys/$keyId" -Token $TOKENB) 403 403
$r = Call -Method POST -Path '/webhooks' -Body @{ name = "hookA-$suffix"; url = 'http://127.0.0.1:1/h'; events = '["device.data"]' } -Token $TOKEN
$hookA = (J $r).data.id
$r = Check 'E9' 'userB reads userA webhook -> 403/403' (Call -Method GET -Path "/webhooks/$hookA" -Token $TOKENB) 403 403
$null = Call -Method DELETE -Path "/webhooks/$hookA" -Token $TOKEN

# ---------- 清理 ----------
$r = Check '27' 'DELETE /api-keys/{id}' (Call -Method DELETE -Path "/api-keys/$keyId" -Token $TOKEN) 200 200
$r = Check '10' 'DELETE /devices/{id}' (Call -Method DELETE -Path "/devices/$devId" -Token $TOKEN) 200 200
$r = Check 'P10' 'DELETE /products/{id} (after devices removed)' (Call -Method DELETE -Path "/products/$prodId" -Token $TOKEN) 200 200

Write-Host "`n================ RESULTS ================" -ForegroundColor Cyan
foreach ($row in $script:ROWS) {
    $color = if ($row.Result -eq 'PASS') { 'Green' } else { 'Red' }
    Write-Host ("{0,-4} | {1,-56} | http={2}/{3} | code={4}/{5} | {6}" -f `
        $row.Id, $row.Desc, $row.Http, $row.ExpHttp, $row.Code, $row.ExpCode, $row.Result) -ForegroundColor $color
}
Write-Host "`nPASS=$script:PASS  FAIL=$script:FAIL  TOTAL=$($script:ROWS.Count)" -ForegroundColor Cyan

exit $script:FAIL