# T-35 · 开放 API 补齐（X-1 批次一） —— 交付说明

> 交付日期：2026-10-11　状态：**已交付，门禁全绿**　**P2 规模化首项**
> 对应缺口：**X-1**（开放 API 覆盖不均）

## 一句话

`/external/v1` 从「设备 + 命令 + 影子 + 告警」扩到平台主线绝大多数能力域 —— 端点数 **14 → 26**，第三方不必再依赖控制台或直连数据库取数。

---

## 新增的 12 个只读端点

| 能力域 | 端点 | 复用服务 |
|---|---|---|
| 属性最新值 | `GET /devices/{key}/properties` | `DeviceDataService` |
| 事件记录 | `GET /devices/{key}/events` | `DeviceDataService` |
| 属性时序 | `GET /devices/{key}/properties/history` | `PropertyHistoryService` |
| 消息历史 | `GET /devices/{key}/messages` | `HistoryService` |
| 设备日志 | `GET /devices/{key}/logs` | `DeviceLogService` |
| 消息规则 | `GET /rules` | `RuleService` |
| OTA | `/ota/firmwares`、`/ota/tasks`、`/ota/tasks/{id}`、`/ota/tasks/{id}/records` | `OtaFirmwareService` + `OtaUpgradeService` |
| 场景 | `GET /scenes` | `SceneService` |

---

## 四条核心设计

### 1. 零业务逻辑重写

全部转发既有服务，控制器只做「API Key → `userId` 转换 + 响应裁剪」。既有服务本就存在且已按 userId 校验，
因此这一批**零迁移、零 DTO 改造、零新增服务**。

### 2. 归属隔离交给服务层，不在控制器重复判断

所有既有服务方法（`page(userId, …)` / `detail(userId, id)` / `query(userId, dto)`）本身就做归属校验并抛 `2003`。
**复用服务就自动继承隔离**，控制器不需要再写一遍 —— 重复判断会造成两处规则漂移，其中一处漏改就是数据泄露。

仅设备维度端点额外走 `requireOwnedDevice`，因为设备是所有数据的入口锚点。

### 3. 严格只读，不开放写操作

写操作的**审计、回滚、幂等**语义远比读复杂：第三方重复调用建规则接口怎么办？误删了场景怎么恢复？
这些在控制台里有确认弹窗与操作者上下文，开放 API 里没有。因此本批一个写端点都没加。

### 4. 响应显式投影

规则 / 场景用 `toRuleMap` / `toSceneMap` 挑字段，不直接返回实体 ——
否则会把 `target_json`、`condition_config`、`step_config` 等内部 JSON 原文外泄，
第三方会依赖上未来可能变的内部格式。

---

## 补上了一个零覆盖的面

> `ExternalApiController` **此前没有任何单测**。而它是 API Key 天然跨账号、最需要守护的面。

新增 14 项测试，重心放在两条：

1. **非归属设备一律 `2003`**
2. **且绝不触达下游服务** —— `verify(never())`。后半句同样关键：校验失败但数据已从服务读出，等于没校验。

其余覆盖用户透传（5 项）、分页边界收敛（`size=9999 → 100`、`page=0 → 1`）与正常路径。

---

## 门禁

| 项 | 结果 |
|---|---|
| 后端 `mvn -B verify` | **1281 项 0 失败**（1267 基线 + 14 新增，含 ArchUnit 5 项） |
| 既有 14 个端点 | 行为与响应结构**一字未改** |
| 前端 | 本批无改动，维持基线（lint 0 error / 576 项全绿 / build 通过） |

---

## 踩坑

| 坑 | 修正 |
|---|---|
| 猜 DTO 字段：`PropertyHistoryQueryDTO.bucket` 以为是秒数 | 实际是白名单字符串 `1m`/`5m`/`15m`/`30m`/`1h`/`6h`/`1d`；`HistoryQueryDTO` 用 `deviceId` 且无 `messageType` |
| `UserPrincipal` 构造照抄 `(userId, null, List.of())` | 签名是 `(userId, username, role, apiKeyId)` |

**教训**：写转发代码前必须打开 DTO 看真实字段，别凭语义推。

---

## 遗留

- **X-2 导出**（设备日志 / 属性趋势的 CSV / JSON）与 **X-3 实时推送**（SSE 流式追加）—— 本类剩余两项
- **分组 / 标签的开放 API**未做：写操作依赖控制台层级语义，第三方通常只需「按分组筛设备」，可由现有设备列表端点扩展过滤覆盖
- **开放 API 写操作**刻意未开放；若第三方确有自动化编排需求，应先补齐**操作审计与幂等键**
- 开放接口**未做现场实测**（本机无可运行 Docker 守护进程）

---

## 规模化进度

对标清单 §5 X 类共 3 项，现已清 1 项：

| 缺口 | 状态 |
|---|---|
| X-1 开放 API 覆盖不均 | ✅ T-35 |
| X-2 导出能力缺失 | ⬜ |
| X-3 实时推送缺失 | ⬜ |

**下一阶段**：X-2 导出（CSV / JSON）与 X-3 实时推送（SSE 流式追加）。