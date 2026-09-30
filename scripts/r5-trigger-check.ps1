<#
.SYNOPSIS
    R5 触发条件核对（T-12「阶段 R5：触发式」）。

.DESCRIPTION
    按 docs/T-12_架构重构_实施计划.md 的 R5 阈值，采集当前数据量并逐项对照，
    输出报告，用于判断是否该启动 R5 组件。与 scripts/r5-trigger-check.sh 同口径。

    依赖：本机已启动 Docker Compose 栈（mqtt-mysql 容器）与 nginx 网关。
    退出码：0=全部未达阈；1=存在接近阈值；2=存在已触发（可挂计划任务做告警）。

.PARAMETER QpsWindowSeconds
    上行 QPS 采样窗口（秒），默认 15。

.PARAMETER WarnRatio
    接近阈值预警比例（%），默认 70。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/r5-trigger-check.ps1
    pwsh -File scripts/r5-trigger-check.ps1 -QpsWindowSeconds 60
#>
param(
    [int]$QpsWindowSeconds = 15,
    [int]$WarnRatio = 70
)

$ErrorActionPreference = 'Continue'

$ROOT_DIR = Split-Path -Parent $PSScriptRoot
$ENV_FILE = Join-Path $ROOT_DIR '.env'
$MYSQL_CONTAINER = 'mqtt-mysql'

# 阈值（与 T-12 R5 表格一致）
$TH_DEVICES = 5000
$TH_QPS = 2000
$TH_MESSAGE_ROWS = 100000000
$TH_BACKEND_LINES = 20000

$script:TRIGGERED = 0
$script:NEAR = 0
$script:BELOW = 0
$script:UNMEASURED = 0

function Read-EnvValue {
    param([string]$Key, [string]$Default)
    if (Test-Path -LiteralPath $ENV_FILE) {
        $pattern = '^' + [regex]::Escape($Key) + '='
        $line = Get-Content -LiteralPath $ENV_FILE | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($line) {
            $value = ($line -replace $pattern, '').Trim()
            if ($value) { return $value }
        }
    }
    return $Default
}

$DB_ROOT_PASSWORD = Read-EnvValue 'DB_ROOT_PASSWORD' 'root_password'
$DB_NAME = Read-EnvValue 'DB_NAME' 'mqtt_cloud'
$FRONTEND_PORT = Read-EnvValue 'FRONTEND_PORT' '80'

function Get-DbScalar {
    param([string]$Sql)
    $out = & docker exec $MYSQL_CONTAINER mysql -u root -p"$DB_ROOT_PASSWORD" $DB_NAME -N -B -e $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($out | Out-String).Trim()
}

function Write-CheckLine {
    param([string]$Label, $Value, $Threshold, [string]$Note = '')
    if ($null -eq $Value -or "$Value" -eq '') {
        Write-Host ("[{0}] {1}  实测 — / 阈值 {2}（采集不可用）" -f '未采集', $Label, $Threshold) -ForegroundColor DarkGray
        $script:UNMEASURED++
        return
    }
    $v = [double]$Value
    $t = [double]$Threshold
    $pct = if ($t -gt 0) { '{0:N1}' -f ($v * 100 / $t) } else { '0.0' }
    if ($v -ge $t) {
        $status = '已触发'; $color = 'Red'; $script:TRIGGERED++
    } elseif ($t -gt 0 -and ($v * 100 / $t) -ge $WarnRatio) {
        $status = '接近阈值'; $color = 'Yellow'; $script:NEAR++
    } else {
        $status = '未达阈'; $color = 'Green'; $script:BELOW++
    }
    Write-Host ("[{0}] {1}  实测 {2} / 阈值 {3}（{4}%）{5}" -f $status, $Label, $Value, $Threshold, $pct, $Note) -ForegroundColor $color
}

Write-Host '=========================================='
Write-Host '  R5 触发条件核对'
Write-Host '=========================================='
Write-Host ("采集时间：{0}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))

# ---- 采集：数据库（设备数 / message 行数）----
$dbOk = $false
& docker exec $MYSQL_CONTAINER mysql -u root -p"$DB_ROOT_PASSWORD" -N -B -e 'SELECT 1' > $null 2>&1
if ($LASTEXITCODE -eq 0) { $dbOk = $true }

$devices = $null
$messageRows = $null
if ($dbOk) {
    $devices = Get-DbScalar 'SELECT COUNT(*) FROM device;'
    $messageRows = Get-DbScalar 'SELECT COUNT(*) FROM message;'
}

# ---- 采集：上行 QPS ----
# 优先用后端已暴露的 ingest_submitted_total 计数器（增量 / 窗口）；端点不可达时
# 回退到 message 表近窗口行数（近似实际落库速率）。两者都取不到则记「未采集」。
$promUrl = "http://localhost:$FRONTEND_PORT/api/actuator/prometheus"
$qps = $null
$qpsSource = ''

function Get-IngestCounter {
    try {
        $resp = Invoke-WebRequest -Uri $promUrl -TimeoutSec 5 -UseBasicParsing
        foreach ($line in ($resp.Content -split "`n")) {
            if ($line -match '^ingest_submitted_total(\{[^}]*\})?\s+([0-9.eE+-]+)') { return [double]$matches[2] }
        }
    } catch { }
    return $null
}

$c0 = Get-IngestCounter
if ($null -ne $c0) {
    Start-Sleep -Seconds $QpsWindowSeconds
    $c1 = Get-IngestCounter
    if ($null -ne $c1) {
        $qps = [math]::Round(($c1 - $c0) / $QpsWindowSeconds, 1)
        $qpsSource = "，来源 ingest_submitted_total 增量/$($QpsWindowSeconds)s"
    }
}
if ($null -eq $qps -and $dbOk) {
    $recent = Get-DbScalar "SELECT COUNT(*) FROM message WHERE sent_at > NOW() - INTERVAL $QpsWindowSeconds SECOND;"
    if ($recent) {
        $qps = [math]::Round([double]$recent / $QpsWindowSeconds, 1)
        $qpsSource = "，来源 message 表近 $($QpsWindowSeconds)s 行数"
    }
}

# ---- 采集：后端代码行数（本地源码统计，不依赖运行中的栈）----
# 口径：backend/src/main/java 下 .java 文件的「非空行数」合计，与 bash 版一致。
$backendLines = $null
$javaDir = Join-Path $ROOT_DIR 'backend\src\main\java'
if (Test-Path -LiteralPath $javaDir) {
    $total = 0
    foreach ($f in Get-ChildItem -LiteralPath $javaDir -Recurse -Filter *.java -File) {
        $lines = @(Get-Content -LiteralPath $f.FullName)
        $total += @($lines | Where-Object { $_.Trim() -ne '' }).Count
    }
    $backendLines = $total
}

Write-Host ''
Write-Host '------------------------------------------'
Write-Host '  量化条件'
Write-Host '------------------------------------------'
Write-CheckLine '消息队列 · 设备数' $devices $TH_DEVICES
Write-CheckLine '消息队列 · 上行 QPS' $qps $TH_QPS $qpsSource
Write-CheckLine '时序库 · message 表行数' $messageRows $TH_MESSAGE_ROWS
Write-CheckLine '物理多模块 · 后端代码行数' $backendLines $TH_BACKEND_LINES
# 历史查询 P99 当前无指标暴露，需用压测采集；不在此脚本量化
Write-CheckLine '时序库 · 历史查询 P99（ms）' $null 1000

Write-Host ''
Write-Host '------------------------------------------'
Write-Host '  需求型条件（非数据量，需人工判定）'
Write-Host '------------------------------------------'
Write-Host '[人工判定] 消息队列 · 跨重启不丢消息' -ForegroundColor DarkGray
Write-Host '[人工判定] 消息队列 · 严格单设备有序' -ForegroundColor DarkGray
Write-Host '[人工判定] 独立抓取/告警体系 · 需要 SLA 与值班' -ForegroundColor DarkGray

Write-Host ''
Write-Host '=========================================='
Write-Host '  结论'
Write-Host '=========================================='
Write-Host ("已触发 {0} 项 / 接近阈值 {1} 项 / 未达阈 {2} 项 / 未采集 {3} 项" -f $script:TRIGGERED, $script:NEAR, $script:BELOW, $script:UNMEASURED)
Write-Host ''

if ($script:TRIGGERED -gt 0) {
    Write-Host ("✗ 存在 {0} 项触发条件已达标，建议启动对应 R5 组件（见 docs/T-12_架构重构_实施计划.md §阶段 R5）" -f $script:TRIGGERED) -ForegroundColor Red
    exit 2
}

if ($script:NEAR -gt 0) {
    Write-Host ("! 存在 {0} 项接近阈值（≥{1}%），建议提前评估" -f $script:NEAR, $WarnRatio) -ForegroundColor Yellow
    exit 1
}

Write-Host '✓ R5 各项量化触发条件均未达阈，继续推迟' -ForegroundColor Green
exit 0