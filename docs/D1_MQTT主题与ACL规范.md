# D1 · MQTT 主题与 ACL 规范

> 版本：v1.0　创建日期：2026-10-21　状态：**权威规范（与代码同步）**
> 适用版本：T-25 / T-26 / T-31 / T-37 当前主线代码
> 代码基线：`AclEvaluator`、`TopicPrefixResolver`、`DeviceSecretServiceImpl`、`EmqxAclController`、`EmqxAuthController`、`MqttMessageHandler`、`DeviceCommandServiceImpl`

---

## 1. 身份与认证

| 身份 | 用户名 | 凭据 | 说明 |
|---|---|---|---|
| 平台后端 | `PLATFORM` | `PLATFORM_SECRET` | 后端自身连接 Broker、共享订阅与下发命令 |
| 设备（SECRET） | `{productKey}.{deviceKey}` | 设备密钥明文 | 默认认证方式；密钥仅创建 / 重置时返回一次 |
| 设备（X.509） | `{productKey}.{deviceKey}` | TLS 客户端证书 | 证书 CN 必须等于用户名；必须走 8883 |
| 设备（JWT） | `{productKey}.{deviceKey}` | 设备 JWT（放在 password 位） | 到期需重签 |
| 前端只读（兼容） | `FRONTEND_READONLY` | `FRONTEND_SECRET` | 仅当 `DIRECT_FRONTEND_ENABLED=true` 时存在；只能订阅 `device/+/data`，当前前端已改用 SSE |

认证回调：`POST /internal/emqx/auth`（内部令牌保护）。产品停用或设备禁用时，**认证与 ACL 同时拒绝**，不会出现「认证拒绝、授权放行」的分叉。

---

## 2. 主题模板语法

产品字段 `product.topic_prefix` 定义设备命名空间，默认 `device/{deviceKey}`。

### 2.1 允许的占位符

| 占位符 | 解析值 |
|---|---|
| `{productKey}` | 产品标识 |
| `{deviceKey}` | 设备标识 |

规则（`TopicPrefixResolver.validateTemplate`）：

- 模板长度 ≤ 128；
- 只允许上述两个占位符，未知占位符拒绝；
- **禁止** `+`、`#`、空白字符、ISO 控制字符；
- 末尾多余的 `/` 会被规范化掉，解析结果统一以一个 `/` 结尾；
- 空模板回落默认 `device/{deviceKey}`。

### 2.2 规范约束（必须遵守）

1. **模板必须包含 `{deviceKey}`**。否则同一产品下所有设备共享同一命名空间，ACL 的设备隔离会失效。
   > 当前校验器只校验占位符合法性，**不强制**包含 `{deviceKey}`；这是已知的配置约束，生产接入必须自行保证。
2. **`{deviceKey}` 必须是模板中最后一个占位符**，消息类型紧跟在解析后的前缀末尾。
   - 例如：`{productKey}/{deviceKey}` + `data` → `pk-1/dev-1/data`；
   - 平台按「倒数第二段 = deviceKey、最后一段 = 消息类型」解析上行，模板若在 `{deviceKey}` 后还有字面段，会把字面段误当消息类型。
3. 修改模板后，设备需要重新连接 / 重新订阅；平台共享订阅在启动时按启用产品生成，产品新增或模板变更后应重启 backend 或重新触发订阅刷新（当前实现为启动时订阅）。

### 2.3 解析示例

| 模板 | 设备 | 上行 data Topic | 下行命令 Topic |
|---|---|---|---|
| `device/{deviceKey}` | `sensor-01` | `device/sensor-01/data` | `device/sensor-01/cmd/down` |
| `{productKey}/{deviceKey}` | `sensor-01`（产品 `env`） | `env/sensor-01/data` | `env/sensor-01/cmd/down` |
| `iot/{productKey}/{deviceKey}` | `sensor-01`（产品 `env`） | `iot/env/sensor-01/data` | `iot/env/sensor-01/cmd/down` |

---

## 3. Topic 清单

### 3.1 设备上行（平台订阅）

| 消息类型 | Topic | QoS | 含义 |
|---|---|---|---|
| `data` | `{prefix}data` | 1 | 属性 / 事件上报（Alink 信封，见 D2） |
| `heartbeat` | `{prefix}heartbeat` | 0 | 心跳；刷新在线状态 |
| `lwt` | `{prefix}lwt` | 1 | 遗言；设备离线 |
| `reply` | `{prefix}reply` | 1 | 命令回执 |
| `ota` | `{prefix}ota` | 1 | OTA 进度回传 |

- `{prefix}` 为按产品模板解析后的前缀，末尾带 `/`。
- 平台以 EMQX 共享订阅接收：`$share/{group}/{prefix 中 deviceKey 代入 +}{messageType}`。
- 共享订阅组名由 `MQTT_SHARED_GROUP` 配置；多副本必须一致，否则同一条消息会被重复落库。
- 平台只订阅**启用产品**的模板；停用产品的主题不再订阅。

### 3.2 平台下行

| 用途 | Topic | QoS |
|---|---|---|
| 属性设置 / 服务调用 / OTA / 点表下发 | `{prefix}cmd/down` | 1 |

下行 Topic **按产品模板解析**（`DeviceCommandServiceImpl.commandTopic`），不再写死 `device/{deviceKey}`。
这样自定义模板设备订阅的 `{prefix}cmd/...` 才能与平台下发的主题一致。

### 3.3 旧版前端直连（兼容）

`FRONTEND_READONLY` 只能订阅：

```
device/+/data
```

该规则使用固定根 `device/`，**不随产品模板变化**；仅用于未升级前端，当前控制台实时数据走 SSE。

---

## 4. ACL 判定模型

判定入口：`POST /internal/emqx/acl`，请求体：

```json
{ "username": "env.sensor-01", "topic": "env/sensor-01/data", "action": "publish" }
```

响应：

```json
{ "result": "allow" }
```

或 `{ "result": "deny" }`。`action` 只接受 `publish` / `subscribe`；用户名、动作或主题为空一律拒绝。

> `ACCESS_CONTROL_ENFORCE_AUTH=false` 时全部放行，仅用于迁移期双轨运行，生产必须为 `true`。

### 4.1 设备自有命名空间

设 `prefix = resolve(product.topic_prefix, productKey, deviceKey)`：

| 动作 | 规则 |
|---|---|
| `publish` | 主题必须以前缀开头，且**不得**进入 `{prefix}cmd/` |
| `subscribe` | 只允许 `{prefix}cmd/` 下的主题 |

因此设备可以发布 `data / heartbeat / lwt / reply / ota` 以及其它非 `cmd/` 主题，但**不能伪造下行命令**；只能订阅自己的命令主题。

### 4.2 网关代理子设备

当主题不在网关自身命名空间时，进入委托判定：

1. 用产品模板从主题反解目标 `deviceKey`；
2. 当前设备必须是 `node_type=GATEWAY`；
3. 目标设备必须存在、`node_type=SUB`、未被禁用，且 `parent_id` 等于当前网关的设备 ID；
4. 然后按**目标设备的命名空间**执行与 4.1 完全相同的 publish / subscribe 语义。

任一条不成立即拒绝：非本人子设备、已解绑、目标设备禁用/产品停用、目标不在模板形状内，全部落在拒绝侧。

### 4.3 平台后端账号

平台账号 ACL 的命名空间来自**所有启用产品**的模板（设备位代入 `+`）：

| 动作 | 规则 |
|---|---|
| `subscribe` | 主题匹配任一启用产品的前缀（用于共享订阅） |
| `publish` | 主题匹配任一启用产品前缀，且路径中包含 `/cmd/` |

平台不能发布非命令主题，也不能发布到停用产品的命名空间。

### 4.4 前端只读账号（兼容）

仅当 `DIRECT_FRONTEND_ENABLED=true` 时：

- 只允许 `subscribe`；
- 主题必须以 `device/` 开头、以 `/data` 结尾；
- 固定根，不随产品模板变化。

### 4.5 明确拒绝清单

以下情形一律 `deny`：

- 用户名不在 `PLATFORM` / `FRONTEND_READONLY` / `{productKey}.{deviceKey}` 三种形态内；
- 用户名含 0 个或多个 `.`、任一侧为空；
- 产品不存在 / 已停用，设备不存在 / 已禁用；
- 设备 publish 进入 `{prefix}cmd/`；
- 设备 subscribe 到 `{prefix}cmd/` 之外；
- 网关代理目标不是当前网关的子设备 / 目标已解绑 / 目标被禁用；
- 平台 publish 不含 `/cmd/`；
- 平台访问停用产品的命名空间；
- `topic`、`action`、`username` 为空；
- 未知 action。

---

## 5. 回执（Reply）与状态

设备收到下行命令后，向 `{prefix}reply` 发布：

```json
{ "id": "<下行命令 id>", "code": 200, "data": { "handled": true } }
```

- `id`：必须与下行 `cmd/down` 的 `id` 完全一致；
- `code`：数字 `200` 表示成功，其它值 / 缺失按失败处理；
- `data`：任意 JSON，成功时写入命令记录的结果，失败时用于拼装错误信息；
- 回执只更新命令记录，不解析为属性；`messageType=reply` 不会刷新设备在线状态。

命令状态：`PENDING → SENT → ACKED / FAILED / TIMEOUT`；离线属性设置会先进入 `QUEUED`，上线后补发。

---

## 6. 源码位置

| 规则 | 源码 |
|---|---|
| 主题模板校验 / 解析 / 反解 | `backend/src/main/java/com/mqtt/cloud/service/TopicPrefixResolver.java` |
| ACL 判定（含网关委托） | `backend/src/main/java/com/mqtt/cloud/service/AclEvaluator.java` |
| ACL 回调 | `backend/src/main/java/com/mqtt/cloud/controller/internal/EmqxAclController.java` |
| 用户名解析 | `backend/src/main/java/com/mqtt/cloud/service/impl/DeviceSecretServiceImpl.java` |
| 上行订阅与解析 | `backend/src/main/java/com/mqtt/cloud/mqtt/MqttMessageHandler.java` |
| 下行主题与载荷 | `backend/src/main/java/com/mqtt/cloud/service/impl/DeviceCommandServiceImpl.java` |
| 回执处理 | `backend/src/main/java/com/mqtt/cloud/service/impl/CommandReplyServiceImpl.java` |

---

## 7. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-21 | 首次成文：按 `AclEvaluator` / `TopicPrefixResolver` / `MqttMessageHandler` / `DeviceCommandServiceImpl` 实际代码提取主题、ACL、网关委托与回执规则；记录「模板必须含 `{deviceKey}` 且为最后一个占位符」的配置约束 |
