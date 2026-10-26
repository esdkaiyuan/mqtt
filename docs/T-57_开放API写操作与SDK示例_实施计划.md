# T-57 · 开放 API 写操作与 SDK 示例

> 版本：v1.0　创建日期：2026-10-26　状态：**已交付**
> 定位：把 `/external/v1` 从「只读 + 命令 / 影子」扩展为**可运营的写入口**，并提供最小 SDK 示例，承接对标清单 G-15-1 / G-15-2 的开放生态缺口。
> 关联：[T-35 开放 API 补齐](T-35_开放API补齐_实施计划.md)、[T-53 平台治理与规模化补强](T-53_平台治理与规模化补强_实施计划.md)、[D6 开放 API 写操作与 SDK 示例](D6_开放API写操作与SDK示例.md)。

---

## 1. 背景与目标

T-35 之后开放 API 有 35 个端点，但除命令下发与影子 desired 外全部只读。第三方可以读设备、告警、OTA、场景，却不能创建设备、维护规则与场景，集成只能停留在「监控」层面。

本任务目标：

1. 新增**设备 / 规则 / 场景**写端点，覆盖第三方运营所需的最小写集；
2. 保持既有安全边界：归属隔离、scope、限流、审计全部复用；
3. 不重写业务逻辑，全部转发既有服务；
4. 给出 curl / JS / Python 最小 SDK 示例与错误处理建议。

---

## 2. 设计决策

### 2.1 写范围

| 域 | 新增写端点 | 说明 |
|---|---|---|
| 设备 | `POST /devices`、`PUT /devices/{deviceKey}`、`DELETE /devices/{deviceKey}` | 创建设备返回一次性密钥；更新 / 删除按 `deviceKey` 归属校验 |
| 规则 | `POST /rules`、`PUT /rules/{id}`、`DELETE /rules/{id}`、`PUT /rules/{id}/enabled` | 复用 `RuleService`，越权与校验口径同控制台 |
| 场景 | `POST /scenes`、`PUT /scenes/{id}`、`DELETE /scenes/{id}`、`PUT /scenes/{id}/enabled`、`POST /scenes/{id}/run` | 复用 `SceneService`；`run` 为手动触发 |

**不开放**：产品 / 物模型写、OTA 任务写、用户 / API Key 管理、通知渠道配置 —— 这些要么涉及全局资源，要么需要更细的审计与回滚语义，仍由控制台负责。

### 2.2 安全边界

- scope：`ApiKeyScopeService` 对非 GET 统一要求 `device:write`，因此所有写端点都需要该 scope；
- 归属：设备写按 `deviceKey` + `ownerId` 校验（越权 `2003`）；规则 / 场景由服务层按 `userId` 隔离（不存在 `6218` / `6240`，越权 `403`）；
- 限流：写操作同样受开放接口按 API Key 限流（`RATE_LIMIT_*`）；
- 审计：`AuditLogInterceptor` 自动记录 `source=OPEN_API` 的写操作，无需在控制器埋点；
- 一次性密钥：创建设备响应中的 `deviceSecret` 只返回一次，平台不提供二次查询。

### 2.3 幂等语义

- `deviceKey` / `productId` 决定设备唯一性，重复创建返回 `2001`，调用方应先按 `deviceKey` 查询确认；
- 规则 / 场景的 `PUT` 为**整体覆盖**语义，可重复执行；
- 未引入 `Idempotency-Key`：写操作的幂等由「先查后建 + 整体覆盖」与调用方重试策略共同保证；如需强幂等可后续加请求头。

---

## 3. API 变更

新增 12 个写端点（详见文档站「开放 API → REST API 参考」）：

| 方法 | 路径 | scope |
|---|---|---|
| POST | `/external/v1/devices` | device:write |
| PUT | `/external/v1/devices/{deviceKey}` | device:write |
| DELETE | `/external/v1/devices/{deviceKey}` | device:write |
| POST | `/external/v1/rules` | device:write |
| PUT | `/external/v1/rules/{id}` | device:write |
| DELETE | `/external/v1/rules/{id}` | device:write |
| PUT | `/external/v1/rules/{id}/enabled` | device:write |
| POST | `/external/v1/scenes` | device:write |
| PUT | `/external/v1/scenes/{id}` | device:write |
| DELETE | `/external/v1/scenes/{id}` | device:write |
| PUT | `/external/v1/scenes/{id}/enabled` | device:write |
| POST | `/external/v1/scenes/{id}/run` | device:write |

无新增错误码：全部复用既有业务码（`2001` / `2003` / `6218` / `6219` / `6220` / `6222` / `6240` / `6241` / `6242` / `6244` / `6245` / `403`）。

---

## 4. 后端变更

| 组件 | 变更 |
|---|---|
| `ExternalApiController` | 新增 T-57 写操作区（12 端点），全部转发 `DeviceService` / `RuleService` / `SceneService` |
| `ExternalApiControllerTest` | 新增 4 项写操作测试：创建设备透传 userId、更新设备越权 2003、创建规则透传、手动执行场景透传 |

---

## 5. 文档

- 新增 [D6 · 开放 API 写操作与 SDK 示例](D6_开放API写操作与SDK示例.md)：认证 / scope / curl / JS / Python 最小示例 / 错误处理；
- 文档站「开放 API」新增「写操作（设备 / 规则 / 场景）」分组，并更新读写边界说明；
- README 端点概览、功能表与文档清单同步。

---

## 6. 测试

- `ExternalApiControllerTest`：写操作透传当前密钥 userId；设备越权不触达下游服务；
- 门禁命令：

```bash
sh scripts/mvnw.sh -B verify
cd frontend && npx --no-install eslint src && npx --no-install vitest run && npx --no-install vite build
```

门禁结果（2026-10-26）：

- `ExternalApiControllerTest` **18 项全绿**（含 4 项新增写操作测试）；
- 后端全量 `mvn -B verify`：**1630 项测试 0 失败 0 错误，BUILD SUCCESS**（含 ArchUnit）；
- 前端 `eslint` **0 error / 0 warning**；`vitest` **683 项 / 87 个文件全绿**；`vite build` 通过；
- 待现场实测：第三方按 D6 示例完成读写闭环。

---

## 7. 未做 / 后续

| 项 | 说明 |
|---|---|
| 产品 / 物模型写、OTA 任务写 | 需要更细的审计与回滚语义，另立任务 |
| `Idempotency-Key` 写幂等 | 当前由先查后建 + 整体覆盖 + 调用方重试保证 |
| 多语言 SDK 正式发布 | 当前提供最小示例；生成型 SDK 待契约稳定后另立 |
| OTA 写 / 通知配置写 | 控制台能力，暂不开放 |
| 现场实测 | 第三方系统按 D6 示例完成读写闭环待现场验收 |

---

## 8. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：12 个写端点、scope / 归属 / 限流 / 审计边界与 SDK 示例交付。 |