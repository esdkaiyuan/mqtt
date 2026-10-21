# T-35 开放 API 补齐（X-1 批次一） 实施计划

> 版本：v1.0　创建日期：2026-10-11　最后更新：2026-10-11　状态：**已交付**
> 上游依据：[`头部平台对标_尚未补齐功能项清单.md`](./头部平台对标_尚未补齐功能项清单.md)（v1.9）§5 X-1
> 对应缺口：**X-1**「开放 API 覆盖不均」—— **P2 规模化首项**

---

## 1. 计划概览

- **Goal**：把 `/external/v1` 从只覆盖「设备 + 命令 + 影子 + 告警」扩到**平台主线的绝大多数能力域**，
  让第三方系统不必再依赖控制台或直接连数据库取数。
- **Architecture**：`ExternalApiController` 增 12 个只读端点，**零业务逻辑重写** —— 全部转发既有服务，
  控制器只做「API Key → `userId` 转换」+ 响应裁剪。
- **Tech Stack**：Spring Boot 4.1.1 + MyBatis Plus；纯后端，无前端改动。
- **Global Constraints**：
  - **零回归红线**：既有 14 个端点的行为与响应结构**一字不改**；全量后端测试必须全绿。
  - **严格只读**：不开放任何写操作（见 §3.1）。
  - **归属隔离不可绕过**：每个端点都必须经过 `requireOwnedDevice` 或服务方法的 `userId` 校验，二者至少居其一。

---

## 2. 现状基线（实施前已核实）

| 项 | 结论 |
|---|---|
| 端点数 | `/external/v1` 共 **14 个**：设备 6 + 命令 2 + 影子 2 + 告警 4 |
| 完全未开放的能力域 | **属性最新值 / 事件记录 / 属性时序 / 消息历史 / 设备日志 / 消息规则 / OTA / 场景 / 分组标签 / 看板** |
| 可复用服务 | `DeviceDataService`、`PropertyHistoryService`、`HistoryService`、`DeviceLogService`、`RuleService`、`OtaFirmwareService`、`OtaUpgradeService`、`SceneService` —— **全部已存在且已按 userId 做归属校验** |
| 单测 | **完全没有** —— 该控制器此前零测试覆盖 |
| 限流 | `ApiKeyRateLimitFilter` 已按 API Key 维度限流（默认关闭），新增端点自动继承 |

---

## 3. 设计决策

### 3.1 严格只读，不开放写操作

写操作的**审计、回滚、幂等**语义远比读复杂：第三方重复调用建规则接口怎么办？误删了场景怎么恢复？
这些在控制台里有确认弹窗和操作者上下文，开放 API 里没有。控制台也已完整覆盖这些能力。
因此本批**一个写端点都不加**；第三方确需写能力时走既有的命令下发 / 影子期望值写入端点。

### 3.2 归属隔离交给服务层，不在控制器重复判断

所有既有服务方法（`page(userId, ...)`、`detail(userId, id)`、`query(userId, dto)`）本身就做归属校验，
抛 `2003`。这意味着**只要复用服务就自动继承隔离**，控制器不需要（也不应该）再写一遍判断 ——
重复判断会造成两处规则漂移，且其中一处漏改就是数据泄露。

仅设备维度的端点额外走 `requireOwnedDevice`，因为设备是所有数据的入口锚点。

### 3.3 响应显式投影，不外泄内部 JSON

规则返回 `toRuleMap`、场景返回 `toSceneMap`，**显式挑字段**。
不直接返回实体是因为 `target_json`（规则目标快照）、`condition_config` / `step_config`（场景配置）
属于内部结构，直接外泄会让第三方依赖上未来可能变的内部格式。

### 3.4 补上这个控制器缺失的单测

开放接口是最需要守护的面：**API Key 天然跨账号**，一处漏掉归属校验就是数据泄露，
而它此前零测试覆盖。新增的 14 项测试把重心放在
「非归属设备一律 `2003`，且**绝不触达下游服务**」——
后半句同样关键：校验失败但数据已从服务读出，等于没校验。

---

## 4. 实施记录

### P1 端点扩展

- [x] 注入 8 个既有服务（`DeviceDataService` / `PropertyHistoryService` / `HistoryService` /
      `DeviceLogService` / `RuleService` / `OtaFirmwareService` / `OtaUpgradeService` / `SceneService`）。
- [x] 新增 12 个端点（属性最新值、事件、时序、消息历史、日志、规则、OTA 四端点、场景）。
- [x] 3 个辅助方法：`splitIdentifiers` / `toRuleMap` / `toSceneMap`。
- [x] 分页参数统一收敛：页码下限 1、每页上限 100（与既有端点口径一致）。

### P2 门禁

- [x] 后端 `sh scripts/mvnw.sh -B verify`：**1281 项 0 失败**（1267 基线 + 14 新增；含 ArchUnit 5 项）。
- [x] 新增 `ExternalApiControllerTest` 14 项（**该控制器历史上第一份测试**）。
- [x] 前端：本批无改动，维持基线。

### 新增测试覆盖

| 类别 | 用例 |
|---|---|
| 归属隔离（5 项） | 属性最新值 / 消息历史 / 设备日志 / 属性时序 / 事件记录 —— 非归属设备抛 `2003` 且 `verify(never())` 下游未触达 |
| 用户透传（5 项） | 时序 / 规则 / 固件包 / OTA 任务 / 场景 —— 验证把 `currentUserId()` 正确传给服务 |
| 分页边界（2 项） | `size=9999` 收敛到 100；`page=0` 收敛到 1 |
| 正常路径（2 项） | 属性最新值返回 identifier/value 映射；OTA 详情透传 |

---

## 5. 踩坑记录

- **DTO 字段不能靠猜**：`PropertyHistoryQueryDTO.bucket` 是**白名单字符串**（`1m`/`5m`/`15m`/`30m`/`1h`/`6h`/`1d`）
  而非秒数，`HistoryQueryDTO` 用的是 `deviceId` 且**没有 `messageType`**。
  先编译报错才暴露 —— 写这类转发代码前必须打开 DTO 看真实字段。
- `UserPrincipal` 的构造签名是 `(userId, username, role, apiKeyId)`，
  既有测试用 `(userId, null, List.of())` 会报「List 与 String 不兼容」。

---

## 6. 未做 / 遗留

- **X-2 导出**（设备日志 / 属性趋势的 CSV / JSON）与 **X-3 实时推送**（SSE 流式追加）——
  本类剩余两项，为 P2 后续。
- **分组 / 标签的开放 API**未做：这两类数据的写操作依赖控制台的层级语义，
  第三方通常只需「按分组筛设备」，可由现有设备列表端点扩展过滤覆盖，待有真实需求再开。
- **开放 API 写操作**（建规则 / 建场景 / 建 OTA 任务）刻意未开放；若第三方确有自动化编排需求，
  应先补齐**操作审计与幂等键**，再考虑开放。
- 开放接口**未做现场实测**（本机无可运行 Docker 守护进程），正确性由 14 项单测与 ArchUnit 分层校验覆盖。