# T-54 · OTA 固件版本跟踪与降级拦截

> 版本：v1.0　创建日期：2026-10-26　状态：**已交付**
> 定位：补齐 `头部平台对标_尚未补齐功能项清单.md` G-11-5（固件无版本比较 / 降级拦截），并让设备档案具备主流平台标准的「当前固件版本」字段。
> 关联：[T-22 OTA 固件升级](T-22_OTA固件升级_实施计划.md)、[T-32 OTA 灰度分批与固件校验](T-32_OTA灰度分批与固件校验_实施计划.md)、[T-53 平台治理与规模化补强](T-53_平台治理与规模化补强_实施计划.md)。

---

## 1. 背景与目标

T-22 / T-32 已交付固件包管理、灰度分批、进度回传与 MD5 校验，但平台不知道**每台设备当前跑的是哪个固件版本**：

- 运维无法在设备详情直接看到当前版本；
- 建升级任务时无法阻止「目标版本低于设备当前版本」的降级，可能把设备刷回旧固件；
- 设备乱序回传的旧 `success` 可能把版本记录改回旧值。

本任务目标：

1. 设备档案新增 `firmware_version`，由 OTA 成功回传自动维护；
2. 建任务默认拦截降级设备，并在任务详情展示拦截台数；
3. 设备详情 / 开放 API / 设备台账导出带出版本；
4. 保持既有 OTA 语义不变：不改主题、不改 ACL、不新增 `IngestDispatcher` 路、不改变灰度 / 校验逻辑。

---

## 2. 设计决策

### 2.1 版本回写时机

| 场景 | 是否回写 | 说明 |
|---|---|---|
| 任务记录进入 `SUCCESS` | 是 | 由 `OtaProgressServiceImpl` 在 `writeProgress` 后回写 |
| 手工升级（无任务记录）`success` 回传 | 是 | 设备上报 `device/{key}/ota` 即视为可信来源 |
| `failed` / 校验不一致 / 非终态进度 | 否 | 版本未变，不应提前更新 |
| 设备上报版本低于平台记录 | 默认否 | 防乱序旧包回退；`OTA_ALLOW_DOWNGRADE=true` 时按真实回传更新 |

回写失败只记 WARN：版本是最终一致的展示字段，不能因此让进度落库失败。

### 2.2 版本比较规则

`FirmwareVersionComparator`（`util`，纯函数）：

- 数字段按数值比较：`1.2.10 > 1.2.9`；
- 忽略 `v` / `V` 前缀与 `+build` 元数据；
- 缺失数字段按 0：`1.0 == 1.0.0`；
- 非数字后缀视为预发布：`1.0.0-beta < 1.0.0`，`1.0.0-rc1 < 1.0.0-rc2`；
- 无法解析时按字符串兜底，不抛异常。

### 2.3 建任务降级拦截

- 在「产品匹配」之后、「设备级互斥」之前执行；
- 仅比较平台已知的 `firmware_version`；**NULL 放行**，避免老设备被永久卡住；
- 默认 `app.ota.allow-downgrade=false`；为 true 时整段跳过比较（现场回滚固件）；
- 被拦截设备不建记录，任务详情回传 `downgradeSkippedCount`；
- 全部被拦截返回 `6237`（`OTA_TASK_INVALID`），提示可打开 `OTA_ALLOW_DOWNGRADE`。

---

## 3. 数据模型

`V32__device_firmware_version.sql`：

```sql
ALTER TABLE device
    ADD COLUMN firmware_version VARCHAR(64) NULL
        COMMENT '当前固件版本（OTA 成功后回写；NULL=未知）' AFTER parent_id;

ALTER TABLE ota_upgrade_task
    ADD COLUMN downgrade_skipped_count INT NOT NULL DEFAULT 0
        COMMENT '建任务时因当前版本高于目标版本而被跳过的设备数（T-54）' AFTER skipped_count;
```

- 只加列，不改既有列；
- `DeviceMapper.xml` 的 `findByDeviceKey` / `findByDeviceKeys` / `baseColumns` / `pageQuery` / `findActiveById` / `findByParentId` 同步投影该列；
- 摄取链路拿到的设备对象（`findByDeviceKeys`）也带版本，进度回写防回退才能生效；
- `downgrade_skipped_count` 让任务详情在创建后的任意一次刷新中都能展示拦截台数（不只创建响应）。

---

## 4. 后端变更

| 组件 | 变更 |
|---|---|
| `Device` | 新增 `firmwareVersion` 字段 |
| `DeviceService` / `Impl` | 新增 `updateFirmwareVersion(deviceId, version)`，空值忽略 |
| `FirmwareVersionComparator` | 新增版本比较工具 |
| `OtaProperties` | 新增 `allowDowngrade=false` |
| `OtaProgressServiceImpl` | 注入 `DeviceService`；任务成功 / 手工升级成功回写版本，默认防回退 |
| `OtaUpgradeServiceImpl` | 建任务增加 `filterVersionDowngrades`，任务详情回传 `downgradeSkippedCount` |
| `OtaUpgradeTask` / `OtaTaskVO` | 持久化 `downgradeSkippedCount`，列表 / 详情 / 外部 API 均可读取 |
| `OtaTaskDetailVO` | 新增 `downgradeSkippedCount` |
| `ExportService` | 设备台账 CSV 增加「固件版本」列 |
| `ExternalApiController` | 设备投影增加 `firmwareVersion` |
| `DeviceImportServiceImpl` | 可选 `firmwareVersion` 列，导入时写入已知版本 |

---

## 5. 配置

| 变量 | 默认 | 说明 |
|---|---|---|
| `OTA_ALLOW_DOWNGRADE` | `false` | false=建任务跳过当前版本高于目标版本的设备；true=允许降级 |

已同步 `application.yml`、`.env.example`、`docker/docker-compose.yml`。

---

## 6. 前端变更

- 设备详情「基本信息」新增「固件版本」，未上报显示「未上报」；
- OTA 任务详情新增「降级拦截」行，展示建任务时被跳过的台数；
- 设备导入对话框与模板说明增加可选 `firmwareVersion` 列；
- 文档站功能指南补充版本回写 / 降级拦截说明；平台运维补充 `OTA_ALLOW_DOWNGRADE`。

---

## 7. 测试

- `FirmwareVersionComparatorTest`：数字段、前缀 / 元数据、缺失段、预发布、降级判定、相等版本；
- `OtaProgressServiceImplTest`：成功回写、手工升级回写、低版本防回退；
- `OtaUpgradeServiceImplTest`：高版本设备跳过 + `downgradeSkippedCount`、全部降级拒绝；
- `DeviceImportServiceImplTest`：模板包含 `firmwareVersion`。

门禁命令：

```bash
sh scripts/mvnw.sh -B verify
cd frontend && npx --no-install eslint src && npx --no-install vitest run && npx --no-install vite build
```

**门禁结果（2026-10-26）**：

- 后端全量 `mvn -B verify`：**1613 项测试 0 失败 0 错误，BUILD SUCCESS**（含 ArchUnit；T-54 定向 92 项全绿）；
- 前端：`eslint src` **0 error / 0 warning**；`vitest run` **683 项 / 87 个文件全绿**；`vite build` 通过；
- 待现场实测：真实设备 `success` 回写、乱序旧包防回退、灰度任务降级拦截。

---

## 8. 未做 / 后续

| 项 | 说明 |
|---|---|
| 版本号强格式校验 | 当前宽松比较，兼容现场多种写法；若后续要求严格 semver，可增加产品级开关 |
| 固件签名 / SHA-256 | 当前仍为 MD5；签名校验需密钥体系，另立任务 |
| 对象存储 / CDN | 仍为共享卷 + nginx |
| 版本分布统计 | 设备台账可导出后统计；控制台版本分布图未做 |
| 现场实测 | 真实设备上报 `success` 回写、灰度任务降级拦截待现场验收 |

---

## 9. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：版本字段 / 回写 / 比较器 / 降级拦截 / 前端与测试交付记录。 |