# T-32 OTA 灰度分批与固件完整性校验 实施计划

> 版本：v1.0　创建日期：2026-10-11　最后更新：2026-10-11　状态：**已交付**
> 上游依据：[`头部平台对标_尚未补齐功能项清单.md`](./头部平台对标_尚未补齐功能项清单.md)（v1.6）§7 追赶段第一优先
> 对应缺口：**G-11-1**（灰度分批 / 暂停 / 终止）、**G-11-2**（固件完整性校验回执）、**G-11-4**（设备级任务互斥）
> 交付原则：**端到端可用**——批次要真能只发首批、下一批要真能推进、`md5` 不一致要真能拦住，不以「表建好了」为完成。

---

## 1. 计划概览

- **Goal**：把 OTA 从「一经创建即全量即时下发、进度以设备自报为准」改造为主流平台形态 —— **可分批灰度、可暂停 / 终止、可校验固件完整性、同一台设备不会被两个任务同时升级**。
- **Architecture**：三条改造全部落在既有 `ota_*` 三表 + `refreshCounters` 聚合口径上，**不新增下行主题、不改 ACL、不动 `DeviceCommandService`**：
  - **灰度**：新增 `ota_upgrade_batch` 作调度单元；建任务只下发首批；巡检按「上一批已收口 + 间隔到期」推进。
  - **校验**：扩展既有上行主题 `device/{key}/ota` 的载荷（新增可选 `fileMd5`），仍在 `IngestDispatcher` 第六路旁路处理。
  - **互斥**：纯查询判定，建任务期过滤 + 下发期二次校验，不引入锁。
- **Tech Stack**：Spring Boot 4.1.1 + MyBatis Plus + Flyway（V19）+ Vue 3 + Element Plus；后端为主。
- **Global Constraints**：
  - **零回归红线**：T-22 既有 95 项 OTA 测试与全量后端测试必须保持全绿；不改 `DeviceCommandService.invoke` 契约与 `OtaProgressPayload.parse` 的既有必填字段语义。
  - **不改下行协议**：固件地址仍经 `device/{key}/cmd/down` 的 `thing.service.ota_upgrade` 下发，入参仍是 `{url, version, md5, size}` —— 存量设备零改动即可继续升级。
  - **`ALL` 策略必须与改造前完全等价**：新建任务不填灰度参数时走 `batch_mode='ALL'`，一次性全量下发，行为与 T-22 一致。
  - **存量数据不得虚增可信度**：V19 把存量记录的 `verify_status` 回填为 `NOT_REPORTED` 而非 `PASSED` —— 改造前「进度以设备自报为准、未比对 md5」，回填 PASSED 是撒谎。

---

## 2. 现状基线（实施前已核实）

| 事实 | 结论 |
|---|---|
| 下发模型 | `createTask` 解析目标 → 建全部 `ota_upgrade_record` → `dispatchTask` **一次性全量下发**；无任何批次概念 |
| 任务状态 | 仅 `RUNNING/SUCCESS/PARTIAL/FAILED`，全部由 `refreshCounters` 聚合重算，**没有由操作产生的状态** |
| 固件摘要 | `ota_firmware.md5` 已存在且已随参数下发给设备，但**设备从未回传，平台从未比对** |
| 上行载荷 | `{version, status, progress, message}`，`status` ∈ `downloading/flashing/success/failed` |
| 巡检 | `OtaDispatchSweeper` 每轮两件事：补投 `PENDING` + 超时置 `TIMEOUT` |
| 计数口径 | `refreshCounters` 单条 `UPDATE ... JOIN`，`dispatched_count = COUNT(status <> 'PENDING')` |
| 测试基建 | 无 H2 / Testcontainers；SQL 语义靠 `OtaUpgradeRecordMapperSqlTest` 的 MyBatis 动态 SQL 渲染断言 |
| 迁移版本 | V1~V18 已占，V19 可用 |

---

## 3. 设计决策

### 3.1 数据模型（V19）

- `ota_upgrade_task` 增：`batch_mode`、`batch_size`、`batch_interval_ms`、`batch_total`、`current_batch_no`、`skipped_count`。
- `ota_upgrade_record` 增：`batch_no`、`verify_status`、`reported_md5`，并加索引 `idx_task_batch_status (task_id, batch_no, status)`（同时覆盖批次级聚合的过滤与分组，不另建 `idx_task_batch` 以免与既有 `idx_task_status` 冗余）。
- 新增 `ota_upgrade_batch`：唯一键 `uk_task_batch (task_id, batch_no)`。
- **只有 `BATCHED` 策略才建批次行** —— `ALL` 策略与存量任务保持「无批次数据」，前端批次面板据此整块隐藏，减少兼容分支。

### 3.2 状态机扩展

| 集合 | 成员 | 说明 |
|---|---|---|
| 记录非终态 | `PENDING/DISPATCHED/DOWNLOADING/FLASHING/**VERIFYING**` | 校验中是进行中，否则批次会被误判收口而提前推进下一批 |
| 记录终态 | `SUCCESS/FAILED/TIMEOUT/**SKIPPED**` | `SKIPPED` = 任务终止时未下发即跳过 |
| 任务（由操作产生） | **`PAUSED` / `ABORTED`** | **不被聚合覆盖**，见 3.3 |
| 批次 | `PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/SKIPPED` | |

### 3.3 关键约束：`refreshCounters` 必须保留 `PAUSED` / `ABORTED`

`PAUSED` 与 `ABORTED` 表达的是**运维意图**而非记录聚合结果。若被聚合重算，暂停中的任务会被下一次设备回传立刻改回 `RUNNING`，终止中的任务会被改回 `SUCCESS/FAILED` —— **两个操作直接失效**。故 SQL 的 `status` 表达式首个分支即：

```sql
CASE WHEN t.status IN ('PAUSED', 'ABORTED') THEN t.status ...
```

同时 `dispatched_count` 改为 `status <> 'PENDING' AND status <> 'SKIPPED'` —— `SKIPPED` 是「终止时从未下发」，计入已下发会让终止任务的进度条虚高到 100%。`skipped_count` 单列而**不并入 `failed_count`**：终止是运维主动行为，读成「这批设备升级失败」是误导。

### 3.4 灰度推进：以记录表事实为准，不读可能滞后的批次 status

批次推进条件（四项全满足才推进下一批）：

1. 所属任务 `status = 'RUNNING'`（暂停 / 终止不推进）
2. `batch_mode = 'BATCHED'` 且 `batch_interval_ms > 0`（为 0 只手动推进）
3. `batch_no = current_batch_no + 1`（只推下一批，杜绝跳批）
4. **上一批已无任何非终态记录**（`NOT EXISTS`）—— 这是灰度的核心语义：前一批成功率未验证就继续铺量等于放弃灰度；同时也避免「前一批还在刷固件、后一批已在下载」冲击现场网络

间隔起算点取上一批最后一条记录的 `updated_at`。判定刻意**不读 `ota_upgrade_batch.status`**：该列仅在 `refreshBatchCounters` 被调用时刷新，批次刚收口但尚未刷新时仍是 `RUNNING`，会导致本该推进的批次被多压一轮巡检。

### 3.5 校验语义与开关

- `fileMd5` 取值非法（长度不对 / 非十六进制）**按「未回传」处理，不丢弃整条报文** —— 与 `progress` 非法即丢弃的策略刻意不同：md5 是新增可选字段，老设备上报了平台不认识的写法时，不应因此把升级卡死。合法值统一转小写（多数设备端库输出大写，而平台侧存小写）。
- **设备回传了 md5 且与平台不一致 → 一律判 `FAILED`**（记 `MISMATCHED`），并把进度压回「已下载完成」以下，避免详情页出现「进度 100% 但状态失败」。这是全类唯一允许进度回退的分支，且只发生在即将进入终态时。
- 未回传 md5 时：`OTA_VERIFY_REQUIRED=false`（默认）→ `NOT_REPORTED` 并**放行成功**（否则未升级固件的老设备会全量转红）；`=true` → 判失败（仅适用于设备侧已全量支持回传的现场）。

### 3.6 互斥策略

- 建任务期：目标设备若在别的任务中非终态，按 `OTA_DEVICE_CONFLICT_POLICY` 处理 —— `SKIP`（默认，静默剔除并在响应里回传剔除台数）或 `REJECT`（整单拒绝，`6293`）；全部冲突时统一报 `6293` 而非 `6237`，让运维一眼看出是互斥而非配置错。
- **下发期二次校验**：建任务时的检查挡不住「两个任务几乎同时创建」的竞态，故 `dispatchTask` 内做一次批量判定（一次查询构建 deviceId 集合，非 N+1）。冲突设备**保持 `PENDING` 并写明原因**，待对方任务收口后由补投巡检自然重投 —— 不置终态，否则对方任务一旦失败，这些设备就永远升级不上了。
- 互斥查询的 `excludeTaskId` 必须传非空占位（建任务时新任务尚无 id）：SQL 是 `task_id <> #{excludeTaskId}`，传 `null` 会因三值逻辑恒为 false，把所有设备都判成冲突。
- `selectDispatchCandidates` 补 `AND t.status <> 'ABORTED'`：终止任务的待投记录由 `abort` 一次性置 `SKIPPED`，此条件只防 abort 与补投并发时把设备重新拉起来下发。

### 3.7 巡检顺序：补投 → 推进 → 超时

顺序固定。若把超时放最前，会把「刚下发、设备还没来得及回传」的记录误判为超时。

---

## 4. 实施记录

### P1 数据模型与常量

- [x] `V19__ota_canary_and_verify.sql`：task 增 6 列、record 增 3 列 + 1 索引、新建 `ota_upgrade_batch`；新列全部带 DEFAULT，存量行回填后与旧行为等价。
- [x] `OtaStatusValue` 扩 `RECORD_VERIFYING` / `RECORD_SKIPPED` / `TASK_PAUSED` / `TASK_ABORTED` / `BATCH_*` / `VERIFY_*` / `DEVICE_VERIFYING`，并新增 `OPERATOR_HELD_TASK_STATUSES` 常量把「由操作产生的状态」显式化。
- [x] 实体：`OtaUpgradeTask` / `OtaUpgradeRecord` 补字段，新增 `OtaUpgradeBatch`。
- [x] `OtaUpgradeBatchMapper` + XML：`selectDueBatches` / `refreshBatchCounters`。
- [x] `refreshCounters` SQL 三处口径变更（PAUSED/ABORTED 保留、SKIPPED 排除、新增 skipped_count）。

### P2 设备级互斥（G-11-4）

- [x] `selectActiveDeviceIdsExcludingTask(deviceIds, excludeTaskId)` / `selectPendingIdsByTask(taskId)`。
- [x] `OtaUpgradeServiceImpl.excludeConflictingDevices`：SKIP / REJECT 两策略。
- [x] `dispatchTask` 下发前批量二次校验。

### P3 固件完整性校验（G-11-2）

- [x] `OtaProgressPayload` 增 `fileMd5`（32 位十六进制、转小写、非法按未回传）与 `verifying` 状态。
- [x] `OtaProgressServiceImpl.verify(...)` 返回 `Verification` 判定；`applyProgress` 分三支处理（不一致 → 失败；强制校验且无法确认 → 失败；正常前向迁移）。
- [x] 重投时一并清空 `verify_status` / `reported_md5` —— 上一轮的 `MISMATCHED` 是针对上一轮固件写入盘的结论，留着会污染本轮展示。

### P4 灰度分批与控制（G-11-1）

- [x] `OtaTaskCreateRequest` 增 `batchMode` / `batchSize` / `batchIntervalMs`；`resolveCanary` 校验（**非法 `batchMode` 一律拒绝而非归到 `ALL`** —— 静默降级会让运维以为在做灰度、实际全量下发，正是灰度要防的事故）。
- [x] `createBatches` / `createRecords` 按批打标并只返回首批主键。
- [x] `runBatch` / `dispatchBatch`：批号推进用 `lt(current_batch_no, batch_no)` 守卫，防手动与自动推进并发时互相覆盖；即便本批 0 台下发成功也照样推进批号，否则灰度永远推不动。
- [x] `pause` / `resume` / `abort` / `advance` 四个端点 + `listBatches`。
- [x] `sweepBatchAdvance` + `OtaDispatchSweeper` 接入；`retry` / `remove` 的状态前置条件同步扩展。

### P5 前端

- [x] `api/ota.js` 补 5 个方法。
- [x] `UpgradeTasks.vue`：创建表单增灰度方式 / 每批台数 / 推进间隔；列表增「跳过」「下发策略」两列与暂停 / 恢复 / 推进按钮；互斥剔除台数在成功提示中显式告知。
- [x] `UpgradeTaskDetail.vue`：批次时间线面板、灰度批次统计卡、下发策略与互斥剔除元信息、记录表增批次列与固件校验列、四个控制按钮。
- [x] `stat-grid` 改 `auto-fit`（卡片数随条件可见性变化）。

### P6 门禁

- [x] 后端 `sh scripts/mvnw.sh -B verify`：**1233 项测试 0 失败**（含 ArchUnit 5 项分层校验）。
- [x] 前端 `npx eslint src`：**0 error 0 warning**。
- [x] 前端 `npx vitest run`：**568 项全绿（70 files）**。
- [x] 前端 `vite build`：通过（25.87s）。
- [x] 文档：`DEPLOYMENT.md` 补 5 个配置项 + 灰度 / 互斥 / 校验说明；对标清单升 v1.7；路线图升 v2.8；总督促升 v1.35；`ARCHITECTURE.md` 补 OTA 链路要点。

### 新增测试

| 文件 | 项数 | 覆盖 |
|---|---|---|
| `OtaCanaryAndVerifyTest`（新增） | 32 | 灰度切批、只发首批、互斥 SKIP/REJECT/全冲突、暂停/恢复/终止/推进、巡检推进、md5 四种判定路径、载荷归一化 |
| `OtaUpgradeRecordMapperSqlTest`（扩） | +7 | 批次 mapper 的推进条件与聚合口径、`SKIPPED` 保留、`PAUSED/ABORTED` 不被覆盖、`VERIFYING` 算非终态 |
| `OtaUpgradeServiceImplTest` / `OtaProgressServiceImplTest`（改） | — | 构造器新增依赖、fixture 补新列 |
| `UpgradeTasks.spec.js` / `UpgradeTaskDetail.spec.js`（扩） | +15 | 灰度配置提交、批次进度展示、暂停/恢复/推进/终止、互斥提示、校验列 |

---

## 5. 踩坑记录

- **`MyBatis-Plus` 的 Wrapper 参数是惰性渲染的**：`eq()` / `in()` 把值挂在 `appendSqlSegments` 的 supplier 上，**必须先调 `getSqlSegment()`（或 `getSqlSet()`）才会填进 `paramNameValuePairs`**。直接在 `mock.selectList(any())` 的 answer 里读参数表会拿到空 Map，导致「只下发首批」这类断言失真（返回空列表 → 0 次下发）。同理 `getSqlSet()` 只含列名与 `#{...}` 占位符，实际值要去 `getParamNameValuePairs().values()` 取。
- **`refreshBatchCounters` 不能用 batchId 定位**：写成 `(SELECT task_id FROM ota_upgrade_batch WHERE id = #{batchId})` 会直接撞 MySQL 的 `You can't specify target table 'ota_upgrade_batch' for update in FROM clause`（UPDATE 子查询引用目标表）。改用 `(task_id, batch_no)` 复合键，唯一键保证命中单行且完全规避该限制。
- **构造器签名变更会同步打断既有测试的 `setUp`**：新增 `OtaUpgradeBatchMapper` 等依赖后，既有 `OtaUpgradeServiceImplTest` / `OtaProgressServiceImplTest` 报 `NoSuchMethod '<init>'`，需同步 `setUp` 与 fixture（`task()` 补灰度列、`record()` 补 `batchNo` 与 `verifyStatus`）。
- **mock 的 `selectById` 恒返回初始值**：`pause()` / `resume()` 内部 `return detail(...)` 会重新读库，mock 下拿到的仍是旧状态。断言应针对**实际下发的 UPDATE**（`getSqlSet()` 的列名 + 参数表的值），而不是 `detail` 投影。
- **`'A'` 是合法十六进制**：`"A".repeat(32)` 经 `toLowerCase()` 后是合法 md5，不能用来构造「非十六进制」用例，得用 `'g'`。

## 6. 未做 / 遗留

- `OTA_VERIFY_REQUIRED=true` 的现场条件（设备侧已全量支持回传 `fileMd5`）**未做真机实测**，需配合设备固件验证。
- 多批次灰度的**真实推进节奏未现场实测**（需多台真实设备回传进度才能验证 `NOT EXISTS` 收口判定与间隔锚点）。
- G-11-3（对象存储 / CDN）、G-11-5（版本比较 / 降级拦截）、G-11-6（OTA 开放 API）不在本批范围，仍为 P2。