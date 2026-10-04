# T-22 OTA 固件升级（OTA Firmware Upgrade）设计文档

> 所属链路：L9 OTA 固件升级（`docs/平台功能链路路线图.md` §3）
> 依赖链路：L5 设备分组与标签（T-18）、L2 命令下发与服务调用（T-15）
> 文档状态：待实施
> 最后更新：2026-10-03

---

## 1. 背景与目标

平台当前已具备「设备接入 → 物模型解析 → 命令下发 → 设备影子 → 告警 → 分组标签 → 消息规则 → 设备日志 → 属性时序与看板」的完整链路，但**固件升级能力完全缺失**：无法登记固件包、无法按批次推送升级、无法回收升级进度。设备侧的固件版本只能靠人工刷写。

现状缺口：

1. 没有固件包的登记与存储，设备侧无从获取固件下载地址。
2. 没有升级任务概念，无法把「一批设备 + 一个固件版本」组织为可追踪的作业。
3. 没有升级进度回传通道，运维只能靠设备离线/上线间接猜测结果。
4. 前端「设备运维」域没有任何固件与升级入口。

本链路目标：

1. **固件包管理**：上传、列表、详情、删除固件包；同产品下版本唯一；记录大小与 MD5 供设备校验。
2. **批次升级**：按「手选设备 ∪ 产品 ∪ 分组（含子分组）∪ 标签」解析目标集合，创建升级任务并逐台下发固件地址。
3. **下发通道复用**：固件地址通过既有 `/cmd/down` Alink 命令通道下发，**不动 ACL、不新增下行主题**。
4. **进度回传**：新增设备上行主题 `device/{key}/ota`，回传 `status/progress`，落库为逐台记录并在任务详情可见。
5. **任务可观测**：任务列表与详情展示总数 / 已下发 / 成功 / 失败 / 进行中，支持对未成功记录一键重投。
6. **端到端闭环**：前端上传固件 → 创建任务 → 设备收到地址 → 设备回传进度 → 详情页可见结果，全流程可用。

---

## 2. 范围

### 2.1 在范围内

| 项 | 内容 |
| --- | --- |
| 固件包表 | `ota_firmware`：登记产品、版本、文件、大小、MD5 |
| 固件存储 | 后端落盘到共享卷，nginx 静态托管为设备可下载 URL |
| 升级任务表 | `ota_upgrade_task`：任务与目标快照、计数汇总 |
| 逐台记录表 | `ota_upgrade_record`：每台设备的状态、进度、命令关联 |
| 目标解析 | 复用并扩展 `DeviceBatchService#resolveTarget`，新增产品维度 |
| 固件地址下发 | 复用 `DeviceCommandService#invoke`，`service` 类型，topic 仍为 `/cmd/down` |
| 上行主题 | 新增 `device/{key}/ota`（QoS 1）第 5 条共享订阅 |
| 进度落库 | `IngestDispatcher` 新增第 6 路 fan-out `otaProgressService.handle` |
| 补投巡检 | 对未下发的 `PENDING` 记录在设备在线后周期性补投 |
| 接口层 | 固件包 Controller、升级任务 Controller、文件上传与下载 |
| 前端 | 固件管理页、升级任务列表页、任务详情页，导航与 API 模块 |

### 2.2 不在范围内

| 项 | 原因 |
| --- | --- |
| 固件差分/增量升级（diff OTA） | 需设备侧配合生成差分包，平台侧无收益，后续按需单列 |
| 分片上传 / 断点续传 | 固件体积受 `10MB~32MB` 约束，整包上传足够 |
| 对象存储（S3/MinIO）适配 | 当前部署为单机 Compose，静态托管已满足；多副本横向扩展列入风险 |
| 固件签名与证书链校验 | 仅记录并下发 MD5，签名体系超出本链路 |
| 设备侧升级实现 | 属设备固件 SDK 范畴，本文档只约束协议格式 |
| 升级批次并发度/灰度编排 | 本链路一次性下发全部目标，不做分批放量与暂停 |
| 回滚（刷回旧版本） | 复用同一机制手工创建旧版本任务即可，不单设能力 |

---

## 3. 术语

| 术语 | 含义 |
| --- | --- |
| 固件包（Firmware） | 某个产品某一版本的完整固件文件及其元数据（`ota_firmware` 一行） |
| 升级任务（Task） | 一次「固件包 × 目标集合」的升级作业（`ota_upgrade_task` 一行） |
| 升级记录（Record） | 任务内针对单台设备的下发与进度载体（`ota_upgrade_record` 一行） |
| 目标集合（Target） | 手选设备 ∪ 产品 ∪ 分组（含子分组）∪ 标签，去重并二次按归属过滤 |
| 固件地址（URL） | 由 `app.ota.public-base-url` + 固件存储相对路径拼成的设备侧下载地址 |
| 下发（Dispatch） | 通过 `/cmd/down` 把固件地址作为一次 `service` 调用投递给设备 |
| 进度回传（Progress） | 设备在 `device/{key}/ota` 上报的状态与百分比 |
| 补投（Re-dispatch） | 对仍处于 `PENDING` 的记录，在设备上线后由巡检重新下发 |

---

## 4. 主题方案

### 4.1 固件存储：nginx 静态托管（而非对象存储）

设备侧固件下载地址必须**无需鉴权即可拉取**（设备只持有 MQTT 凭据，不持有 JWT），且要能承受大文件、可断点、可缓存。当前部署形态为单机 Docker Compose，引入对象存储会新增一个重量级依赖。

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 对象存储（S3/MinIO） | 水平扩展、天然 CDN 友好 | 新增服务与配置、本地/离线部署成本高 | 暂不采用 |
| **nginx 静态托管（选用）** | 复用现有 frontend 容器的 nginx、零新增进程、支持 Range 与缓存 | 单机卷、多副本须共享存储 | **采用** |

落点：

- 后端上传时把文件写入 **共享卷 `ota_firmware`**（容器内 `app.ota.storage-dir`，默认 `/var/lib/mqtt/ota`）。
- frontend 容器**以只读方式挂载同一卷**到 `/usr/share/nginx/html/firmware`。
- nginx 新增 `location ^~ /firmware/`：`^~` 修饰符保证该前缀命中后不再尝试正则与 `location /`（既避开 SPA 回落，也避开静态资源正则），并显式声明固件后缀的 MIME 为 `application/octet-stream`（现有静态资源正则 `\.(js|css|png|...)$` 不含固件后缀，不会误命中；为可读性仍置于 `location /` 之前）。
- 设备侧 URL：`{app.ota.public-base-url}/{firmwareId}/{version}/{fileName}`；`public-base-url` 默认 `http://<host>/firmware`。

> 权衡：单机卷在多副本下不可共享，列入 §12 风险；迁移到对象存储时只需替换存储适配层，接口契约不变。

### 4.2 上行主题：新增 `device/{key}/ota`（ACL 零改动）

设备进度回传需要一条**专属上行主题**，与 `data`/`heartbeat`/`lwt`/`reply` 并列。

结论：新增 `device/{key}/ota`，QoS 1。依据 `AclEvaluator`：

- `allowDevice` 中 PUBLISH 仅拒绝 `device/{key}/cmd/` 前缀，`device/{key}/ota` **天然允许设备发布**；
- `allowPlatform` 中 PUBLISH 仅放行 `topic.contains("/cmd/")`，因此**平台不允许向 `device/{key}/ota` 发布**；
- 故固件地址**不能**走 `device/{key}/ota` 下行，必须复用 `/cmd/down`（见 §4.4）。

订阅侧：`MqttMessageHandler#subscribe()` 现为 4 条共享订阅，新增第 5 条：

```java
// OTA 进度回传：设备升级状态与百分比，落库后由 IngestDispatcher 旁路更新升级记录
topics.put("$share/" + group + "/device/+/ota", 1);
```

`messageArrived` 以 `topic.split("/")` 解析，`parts[2] = "ota"` 天然兼容现有 `IngestRecord` 构造，**无需改造解析逻辑**。

### 4.3 目标集合：在 `BatchTargetRequest` 上补产品维度

路线图要求「按产品 / 分组 / 标签批次升级」，而 T-18 的 `BatchTargetRequest` 只有 `deviceIds / groupIds / tagIds`，**缺产品维度**。

选择：**在既有 `BatchTargetRequest` 上追加 `productIds` 字段并扩展 `resolveTarget`**，而非另起一个 OTA 专用目标 DTO。

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 新建 `OtaTargetRequest` | 不动既有类 | 目标解析逻辑二份实现，易分叉 | 不采用 |
| **扩展 `BatchTargetRequest`（选用）** | 单一实现、既有批量端点顺带获得按产品能力 | 需改动 T-18 的类与实现（纯追加，向后兼容） | **采用** |

`productIds` 为空时不参与解析，既有调用方（批量命令 / 启停 / 分组 / 标签）行为完全不变。

### 4.4 固件地址下发：复用 `/cmd/down` 命令通道

由 §4.2，平台不能向 `device/{key}/ota` 发布，因此固件地址只能走既有下行主题 `device/{key}/cmd/down`。复用 `DeviceCommandService#invoke` 的收益：命令记录落库、`/reply` 回执更新状态、超时巡检、前端命令历史全部免费获得。

映射关系：

| 字段 | 值 |
| --- | --- |
| `commandType` | `service` |
| `identifier` | `ota_upgrade`（平台保留服务标识） |
| `callType` | `async` |
| `source` | `OTA`（新增常量 `SOURCE_OTA`） |
| `params` | `{"url":"...","version":"1.2.0","md5":"...","size":123456}` |
| 下行 method | 由既有 `buildPayload` 生成 `thing.service.ota_upgrade` |

**夹点**：`DeviceCommandServiceImpl#validateService` 会按产品物模型校验 `identifier`，未声明的服务将被拒（`COMMAND_IDENTIFIER_UNKNOWN`）。若要求每个产品都手工声明 `ota_upgrade`，则「开箱即用」不成立。

对策：在 `DeviceCommandServiceImpl` 引入**平台内置服务白名单**，`ota_upgrade` 免于物模型声明（其余服务仍严格校验）：

```java
/** 平台保留服务：不要求产品物模型显式声明，入参由调用方（OTA 服务）自行构造并校验。 */
private static final Set<String> RESERVED_SERVICES = Set.of(DeviceCommandService.SERVICE_OTA_UPGRADE);

private void validateService(ThingModelDefinition definition, String identifier, JsonNode params) {
    if (RESERVED_SERVICES.contains(identifier)) {
        return;
    }
    // ... 原有校验不变
}
```

> 权衡：这是对核心命令服务的最小侵入式改动（一处 return），换取 OTA 与命令链路共用同一下发与回执机制；已显式限定为常量白名单，不放开任意服务。

### 4.5 进度回传落点：`IngestDispatcher` 新增第 6 路 fan-out

`IngestDispatcher` 现为「逐事件 webhook + 实时推送」加 3 路批级 fan-out（物模型解析 / 命令回执 / 影子补发）。OTA 报文是**独立消息类型**、不参与物模型解析，因此不走 T-21 那种「解析服务内部旁路」，而是**照 `commandReplyService.handle(events)` 的形式新增第 6 路**：

```java
try {
    otaProgressService.handle(events);
} catch (Exception e) {
    log.warn("OTA 进度回传分发失败，跳过本批: size={}", events.size(), e);
}
```

```java
// OtaProgressServiceImpl：只处理 messageType == "ota" 的事件，逐条隔离
@Override
public void handle(List<ResolvedEvent> events) {
    for (ResolvedEvent event : events) {
        if (!"ota".equals(event.record().messageType())) continue;
        try { applyProgress(event.device(), event.record().payload()); }
        catch (Exception e) { log.warn("OTA 进度落库失败: deviceKey={}", event.device().getDeviceKey(), e); }
    }
}
```

同时 `IngestDispatcher#resolveEventType` 与 `MqttMessageHandler#refreshDeviceStatus` 补 `ota` 分支：前者映射为 `device.ota`（Webhook 事件类型），后者归入 `ONLINE`（收到 OTA 报文即证明设备在线）。

> 与 T-21 的差异说明：T-21 旁路需要物模型解析产出的 `samples`，故必须内嵌在 `ThingModelInterpretService`；OTA 报文无需解析结果，置于 `IngestDispatcher` 与命令回执同级更贴合语义。

### 4.6 状态机

**升级记录（`ota_upgrade_record.status`）**

```
PENDING ──下发成功──> DISPATCHED ──设备回传 downloading──> DOWNLOADING ──> FLASHING ──> SUCCESS
   │                       │                                   │              │
   └──设备始终离线（巡检超时）──> TIMEOUT                        └──────────────┴──设备回传 failed──> FAILED
```

- `PENDING`：记录已创建、尚未成功下发（设备离线或发布失败）。
- `DISPATCHED`：命令已 `SENT`，等待设备首次进度回传。
- `DOWNLOADING` / `FLASHING`：设备回传的中间态。
- `SUCCESS` / `FAILED` / `TIMEOUT`：终态，不再变更。
- 进度百分比单调不减；回传 `progress` 小于当前值时不回退，只更新 `message`。

**任务（`ota_upgrade_task.status`）**

| 取值 | 判定 |
| --- | --- |
| `RUNNING` | 存在非终态记录 |
| `SUCCESS` | 全部记录为 `SUCCESS` |
| `PARTIAL` | 存在终态记录且既有 `SUCCESS` 又有 `FAILED`/`TIMEOUT` |
| `FAILED` | 全部记录为 `FAILED`/`TIMEOUT` |

任务状态由 `refreshTaskCounters(taskId)` 在每次进度落库后按聚合 SQL 重算，避免逐条增减漂移。

---

## 5. 数据模型

新增迁移 `V11__ota_firmware.sql`（当前最新为 `V10__property_history.sql`）。**只加表、不改既有表**，应用版本可安全回滚（回滚后旧代码忽略新表）。三张表均无逻辑删除列（不受 `logic-delete-field: deleted` 影响）。

### 5.1 `ota_firmware` 固件包

```sql
-- OTA 固件包（T-22）
-- 说明：按产品 + 版本唯一；文件落盘到 app.ota.storage-dir，本表只存元数据与相对路径。
CREATE TABLE IF NOT EXISTS ota_firmware (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '上传者（sys_user.id）',
    product_id BIGINT NOT NULL COMMENT '所属产品（product.id）',
    version VARCHAR(64) NOT NULL COMMENT '固件版本号（产品内唯一）',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '存储相对路径（{productId}/{version}/{fileName}）',
    file_size BIGINT NOT NULL COMMENT '文件字节数',
    md5 CHAR(32) NOT NULL COMMENT '文件 MD5（小写十六进制），供设备校验',
    description VARCHAR(255) DEFAULT NULL COMMENT '版本说明',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_product_version (product_id, version),
    INDEX idx_user (user_id),
    INDEX idx_product (product_id),
    CONSTRAINT fk_ota_firmware_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_firmware_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 固件包';
```

### 5.2 `ota_upgrade_task` 升级任务

```sql
-- OTA 升级任务（T-22）
-- 说明：target_json 记录创建时的目标快照（deviceIds/productIds/groupIds/tagIds），
--       计数由 refreshTaskCounters 聚合 ota_upgrade_record 重算。
CREATE TABLE IF NOT EXISTS ota_upgrade_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '创建者（sys_user.id）',
    firmware_id BIGINT NOT NULL COMMENT '固件包（ota_firmware.id）',
    product_id BIGINT NOT NULL COMMENT '固件所属产品（冗余，便于筛选）',
    name VARCHAR(64) NOT NULL COMMENT '任务名称',
    target_json JSON NOT NULL COMMENT '目标快照（deviceIds/productIds/groupIds/tagIds）',
    total_count INT NOT NULL DEFAULT 0 COMMENT '目标设备总数',
    dispatched_count INT NOT NULL DEFAULT 0 COMMENT '已成功下发数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '升级成功数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '升级失败/超时数',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_firmware (firmware_id),
    CONSTRAINT fk_ota_task_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_task_firmware FOREIGN KEY (firmware_id) REFERENCES ota_firmware(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 升级任务';
```

> `fk_ota_task_firmware` 不级联删除：固件被任务引用时禁止删除（`6234`），由服务层前置拦截。

### 5.3 `ota_upgrade_record` 逐台记录

```sql
-- OTA 逐台升级记录（T-22）
-- 说明：任务下一台设备一行；command_id 关联 device_command_record.command_id，便于跳转命令详情。
CREATE TABLE IF NOT EXISTS ota_upgrade_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '升级任务（ota_upgrade_task.id）',
    device_id BIGINT NOT NULL COMMENT '设备（device.id）',
    firmware_id BIGINT NOT NULL COMMENT '固件包（ota_firmware.id）',
    version VARCHAR(64) NOT NULL COMMENT '目标版本号（冗余）',
    status VARCHAR(16) NOT NULL COMMENT 'PENDING/DISPATCHED/DOWNLOADING/FLASHING/SUCCESS/FAILED/TIMEOUT',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度百分比 0~100',
    message VARCHAR(255) DEFAULT NULL COMMENT '设备回传或系统填写的信息',
    command_id VARCHAR(64) DEFAULT NULL COMMENT '关联命令 ID（device_command_record.command_id）',
    dispatched_at DATETIME(3) DEFAULT NULL COMMENT '最近一次成功下发时间',
    last_report_at DATETIME(3) DEFAULT NULL COMMENT '最近一次进度回传时间',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_task_device (task_id, device_id),
    INDEX idx_device (device_id),
    INDEX idx_status (status),
    INDEX idx_task_status (task_id, status),
    CONSTRAINT fk_ota_record_task FOREIGN KEY (task_id) REFERENCES ota_upgrade_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_ota_record_device FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OTA 逐台升级记录';
```

### 5.4 保留口径

- 三张表**不做自动清理**：升级历史是运维审计依据，`ota_upgrade_task` / `ota_upgrade_record` 仅随任务删除级联清理。
- 固件**文件**在删除固件包时同步删除磁盘文件；被任务引用时拒绝删除（`6234`）。
- 无逻辑删除列，删除即硬删除。

---

## 6. 接口设计

基础路径 `/api/ota`（context-path `/api` 由应用统一前缀，Controller 内 `@RequestMapping("/ota/...")`）。

### 6.1 固件包上传

`POST /api/ota/firmwares`，`multipart/form-data`：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productId` | Long | 是 | 所属产品 |
| `version` | String | 是 | 版本号，`^[0-9A-Za-z._-]{1,64}$` |
| `file` | File | 是 | 固件文件，≤ `app.ota.max-file-size` |
| `description` | String | 否 | 版本说明，≤ 255 |

响应 `Result<FirmwareVO>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 12,
    "productId": 3,
    "productName": "ESP32 温湿度节点",
    "version": "1.2.0",
    "fileName": "esp32-node-1.2.0.bin",
    "fileSize": 858624,
    "md5": "9f2c1b7d4e6a8c0f3b5d7e9a1c2b4d6f",
    "downloadUrl": "http://192.168.1.10/firmware/12/1.2.0/esp32-node-1.2.0.bin",
    "description": "修复 WiFi 重连抖动",
    "createdAt": "2026-10-03T10:20:30.123"
  }
}
```

错误：产品不存在 `3002`；版本非法 / 文件缺失 / 超限 `6232`；同产品版本重复 `6233`；落盘失败 `6235`。

### 6.2 接口清单

| 方法 | 路径 | 说明 | 主要错误码 |
| --- | --- | --- | --- |
| POST | `/ota/firmwares` | 上传固件包（multipart） | 6232 / 6233 / 6235 |
| GET | `/ota/firmwares` | 固件包列表（可按 `productId` 过滤） | — |
| GET | `/ota/firmwares/{id}` | 固件包详情 | 6231 |
| GET | `/ota/firmwares/{id}/download` | 后端流式下载（控制台调试用） | 6231 |
| DELETE | `/ota/firmwares/{id}` | 删除固件包（含磁盘文件） | 6231 / 6234 |
| POST | `/ota/tasks` | 创建升级任务 | 6231 / 6237 |
| GET | `/ota/tasks` | 任务列表（分页） | — |
| GET | `/ota/tasks/{id}` | 任务详情（含目标快照与计数） | 6236 |
| GET | `/ota/tasks/{id}/records` | 任务逐台记录（分页，可按 `status` 过滤） | 6236 |
| POST | `/ota/tasks/{id}/retry` | 重投未成功（`PENDING`/`FAILED`/`TIMEOUT`）记录 | 6236 / 6238 |
| DELETE | `/ota/tasks/{id}` | 删除任务及其记录 | 6236 / 6238 |

`POST /ota/tasks` 请求体：

```json
{
  "name": "ESP32 节点升级 1.2.0",
  "firmwareId": 12,
  "target": {
    "deviceIds": [101, 102],
    "productIds": [3],
    "groupIds": [8],
    "tagIds": [5]
  }
}
```

约束：`firmwareId` 必须存在且归属当前用户（否则 `6231`）；目标解析后非空且 ≤ `app.ota.task-max-devices`（否则 `6237`）；**目标设备的产品必须与固件产品一致**，不一致的设备被剔除，若剔除后为空则 `6237`。

`GET /ota/tasks/{id}/records` 响应（分页）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 42,
    "pages": 3,
    "current": 1,
    "records": [
      {
        "deviceId": 101,
        "deviceName": "车间-01",
        "deviceKey": "esp32-0001",
        "version": "1.2.0",
        "status": "DOWNLOADING",
        "progress": 45,
        "message": "下载中 45%",
        "commandId": "3f0a...e1",
        "dispatchedAt": "2026-10-03T10:21:00.000",
        "lastReportAt": "2026-10-03T10:21:35.220"
      }
    ]
  }
}
```

### 6.3 设备侧协议（非 HTTP）

**下行**（平台 → 设备，topic `device/{key}/cmd/down`，QoS 1）：

```json
{
  "id": "3f0a5c8e-...-e1",
  "version": "1.0",
  "method": "thing.service.ota_upgrade",
  "params": {
    "url": "http://192.168.1.10/firmware/12/1.2.0/esp32-node-1.2.0.bin",
    "version": "1.2.0",
    "md5": "9f2c1b7d4e6a8c0f3b5d7e9a1c2b4d6f",
    "size": 858624
  }
}
```

设备对下行命令的应答仍走既有 `device/{key}/reply`（由 `CommandReplyService` 处理，命令记录置 `ACKED`）。

**上行**（设备 → 平台，topic `device/{key}/ota`，QoS 1）：

```json
{ "version": "1.2.0", "status": "downloading", "progress": 45, "message": "下载中" }
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `version` | 是 | 固件版本，须与记录的目标版本一致，否则忽略该报文（记 WARN） |
| `status` | 是 | `downloading` / `flashing` / `success` / `failed` |
| `progress` | 否 | `0~100`，缺省按状态推算（success=100，其余不推进） |
| `message` | 否 | 描述，≤ 255，超长截断 |

报文非法（非 JSON 对象 / `status` 未知 / `progress` 越界）时**丢弃并记 WARN**，不回错误（设备侧无错误通道）。

---

## 7. 后端实现

### 7.1 分层类清单

| 层 | 类 | 说明 |
| --- | --- | --- |
| 迁移 | `db/migration/V11__ota_firmware.sql` | 3 张新表 |
| 实体 | `entity/OtaFirmware.java` | `@TableName("ota_firmware")` |
| 实体 | `entity/OtaUpgradeTask.java` | `@TableName("ota_upgrade_task")` |
| 实体 | `entity/OtaUpgradeRecord.java` | `@TableName("ota_upgrade_record")` |
| Mapper | `mapper/OtaFirmwareMapper.java` | BaseMapper |
| Mapper | `mapper/OtaUpgradeTaskMapper.java` | BaseMapper + `refreshCounters` |
| Mapper | `mapper/OtaUpgradeRecordMapper.java` | BaseMapper + `selectDispatchCandidates` / `selectLatestActiveByDevice` |
| Mapper XML | `resources/com/mqtt/cloud/mapper/OtaUpgradeTaskMapper.xml` | 计数聚合 SQL |
| Mapper XML | `resources/com/mqtt/cloud/mapper/OtaUpgradeRecordMapper.xml` | 补投候选、按设备取活跃记录 |
| DTO | `dto/request/OtaTaskCreateRequest.java` | `name` / `firmwareId` / `target(BatchTargetRequest)` |
| DTO | `dto/response/OtaFirmwareVO.java` | 含 `downloadUrl`（拼装后） |
| DTO | `dto/response/OtaTaskVO.java` | 任务摘要 |
| DTO | `dto/response/OtaTaskDetailVO.java` | 任务 + 目标快照 + 计数 |
| DTO | `dto/response/OtaRecordVO.java` | 逐台记录（含设备名/键） |
| 只读模型 | `service/OtaProgressPayload.java` | 设备上行报文解析结果（record） |
| Service 接口 | `service/OtaFirmwareService.java` | 上传 / 列表 / 详情 / 删除 / 下载 |
| Service 接口 | `service/OtaUpgradeService.java` | 建任务 / 列表 / 详情 / 记录 / 重投 / 删除 / 计数刷新 |
| Service 接口 | `service/OtaProgressService.java` | 进度回传处理（供 Dispatcher 调用） |
| Service 实现 | `service/impl/OtaFirmwareServiceImpl.java` | 落盘、MD5、唯一性、引用校验 |
| Service 实现 | `service/impl/OtaUpgradeServiceImpl.java` | 目标解析、逐台下发、计数聚合 |
| Service 实现 | `service/impl/OtaProgressServiceImpl.java` | 报文解析、状态机推进 |
| 巡检 | `config/OtaDispatchSweeper.java` | `PENDING` 记录补投 + 超时置 `TIMEOUT` |
| 配置 | `config/OtaProperties.java` | `@ConfigurationProperties("app.ota")` |
| 常量 | `common/constant/OtaStatusValue.java` | 记录/任务状态常量 |
| 错误码 | `common/ResultCode.java` | 追加 `6231~6239` |
| 摄取改动 | `mqtt/MqttMessageHandler.java` | 第 5 条订阅 + `ota` 状态映射 |
| 摄取改动 | `ingest/IngestDispatcher.java` | 第 6 路 fan-out + `device.ota` 事件类型 |
| 命令改动 | `service/impl/DeviceCommandServiceImpl.java` | 平台保留服务白名单 |
| 目标改动 | `dto/request/BatchTargetRequest.java`、`service/impl/DeviceBatchServiceImpl.java` | 补 `productIds` 维度 |
| 接口层 | `controller/OtaFirmwareController.java` | 固件包端点 |
| 接口层 | `controller/OtaUpgradeController.java` | 升级任务端点 |
| 配置 | `config/SecurityConfig.java` | `/ota/**` 角色约束 |

> `OtaFirmwareService` / `OtaUpgradeService` / `OtaProgressService` **不得依赖 `..mapper..`**（ArchUnit 门禁），持久化细节只出现在 `service.impl`。

### 7.2 聚合 SQL（`OtaUpgradeTaskMapper.xml`）

```xml
<!-- 按 ota_upgrade_record 重算任务计数与状态，避免逐条增减漂移 -->
<update id="refreshCounters">
    UPDATE ota_upgrade_task t
    SET t.total_count = agg.total_count,
        t.dispatched_count = agg.dispatched_count,
        t.success_count = agg.success_count,
        t.failed_count = agg.failed_count,
        t.status = agg.status,
        t.updated_at = CURRENT_TIMESTAMP(3)
    FROM (
        SELECT task_id,
               COUNT(*) AS total_count,
               SUM(CASE WHEN status &lt;&gt; 'PENDING' THEN 1 ELSE 0 END) AS dispatched_count,
               SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN status IN ('FAILED','TIMEOUT') THEN 1 ELSE 0 END) AS failed_count,
               CASE
                   WHEN SUM(CASE WHEN status IN ('PENDING','DISPATCHED','DOWNLOADING','FLASHING') THEN 1 ELSE 0 END) &gt; 0 THEN 'RUNNING'
                   WHEN SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) = COUNT(*) THEN 'SUCCESS'
                   WHEN SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) = 0 THEN 'FAILED'
                   ELSE 'PARTIAL'
               END AS status
        FROM ota_upgrade_record
        WHERE task_id = #{taskId}
        GROUP BY task_id
    ) agg
    WHERE t.id = agg.task_id
</update>
```

> MySQL 8 支持 `UPDATE ... JOIN`，实现时若目标方言不便使用 `FROM` 子查询，改写为 `UPDATE ota_upgrade_task t JOIN (...) agg ON t.id = agg.task_id SET ...`，语义不变。

### 7.3 服务层流程

**创建任务（`OtaUpgradeServiceImpl#createTask`）**

1. 校验 `firmwareId` 存在且 `user_id == 当前用户`，取回固件（`product_id` / `version`）。
2. `deviceBatchService.resolveTarget(userId, target)` 解析目标集合（含产品维度）。
3. 剔除产品与固件产品不一致的设备；空集或超 `task-max-devices` → `6237`。
4. 插入 `ota_upgrade_task`（`status=RUNNING`，`target_json` 保存快照）。
5. 批量为每台设备插入 `ota_upgrade_record`（`status=PENDING`，`progress=0`）。
6. 调用 `dispatch(taskId, recordIds)` 尝试即时下发（见下）。
7. `refreshCounters(taskId)` 后返回任务详情。

**单台下发（`dispatch`）**

1. 置 `PENDING → DISPATCHED` 前置：构造 `CommandInvoke(deviceId, productId, "service", "ota_upgrade", paramsJson, "async", SOURCE_OTA, operatorId)`。
2. `deviceCommandService.invoke(...)`；成功则回写 `command_id` / `dispatched_at` / `status=DISPATCHED`。
3. 单台异常（发布失败、设备不存在等）只记该条 `message`，**保持 `PENDING` 待巡检补投**，不阻断整批。

**补投巡检（`OtaDispatchSweeper#sweep`）**

- 取 `PENDING` 且**设备在线**的记录，按 `dispatch-batch-size` 分批调用 `dispatch`。
- 另取 `DISPATCHED/DOWNLOADING/FLASHING` 且 `last_report_at`（或 `dispatched_at`）早于 `now - task-timeout-ms` 的记录，置 `TIMEOUT`。
- 每轮结束 `refreshCounters` 受影响任务；`sweep()` 整体 try/catch，异常只记 WARN。

**进度处理（`OtaProgressServiceImpl#applyProgress`）**

1. 解析 payload → `OtaProgressPayload(version, status, progress, message)`；非法则丢弃记 WARN。
2. `selectLatestActiveByDevice(deviceId)`：取该设备最新的非终态记录；无则忽略（可能来自手工升级，记 DEBUG）。
3. 版本不一致 → 忽略记 WARN；终态记录 → 忽略。
4. 状态机推进（§4.6）：进度单调不减，`status` 只允许前向迁移。
5. 更新 `progress` / `message` / `last_report_at`；终态时触发所属任务 `refreshCounters`。

**重投（`retry`）**

- 对任务下 `PENDING`/`FAILED`/`TIMEOUT` 的记录重置为 `PENDING` 并清空 `message`，再走 `dispatch`；`RUNNING` 以外的任务状态允许重投（`SUCCESS` 任务重投为 `6238`）。

### 7.4 不复用理由

- **不复用 `property_set` 下发固件地址**：`property_set` 语义为属性期望值且会写影子 `desired`，固件地址非属性，误写会污染影子。
- **不新增下行主题**：`AclEvaluator` 明确禁止平台向 `device/{key}/ota` 发布，新增下行主题须同步改 ACL 与设备固件，成本更高且破坏既有安全边界。
- **不新建目标解析实现**：见 §4.3，避免二份解析逻辑分叉。
- **不新增 `IngestRecord` 字段**：`ota` 作为 `messageType` 已可区分，新增字段会波及全链路序列化与死信结构。

---

## 8. 前端实现

### 8.1 新增物

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 页面 | `views/workbench/ota/FirmwareList.vue` | 固件包列表 + 上传对话框 |
| 页面 | `views/workbench/ota/UpgradeTasks.vue` | 升级任务列表 + 新建任务对话框 |
| 页面 | `views/workbench/ota/UpgradeTaskDetail.vue` | 任务详情 + 逐台记录表 + 重投 |
| 单测 | `views/workbench/ota/__tests__/FirmwareList.spec.js` | 上传校验与列表渲染 |
| 单测 | `views/workbench/ota/__tests__/UpgradeTasks.spec.js` | 建任务目标选择与提交 |
| 单测 | `views/workbench/ota/__tests__/UpgradeTaskDetail.spec.js` | 进度渲染与重投 |
| API | `api/ota.js` | `otaApi`：firmwares / tasks 全量方法 |
| 单测 | `api/__tests__/ota.spec.js` | API 路径与参数归一化 |
| 路由 | `router/index.js` | 新增 `ota/firmwares`、`ota/tasks`、`ota/tasks/:id` |
| 导航 | `components/layout/NavMenu.vue` | 新增分组「设备运维」 |

路由（插入 `boards/:id` 之后）：

```js
{
  path: 'ota/firmwares',
  name: 'FirmwareList',
  component: () => import('@/views/workbench/ota/FirmwareList.vue'),
  meta: { title: '固件管理', icon: 'package', group: 'fleet', roles: ['ADMIN', 'OPERATOR'] }
},
{
  path: 'ota/tasks',
  name: 'UpgradeTasks',
  component: () => import('@/views/workbench/ota/UpgradeTasks.vue'),
  meta: { title: '升级任务', icon: 'operation', group: 'fleet', roles: ['ADMIN', 'OPERATOR'] }
},
{
  path: 'ota/tasks/:id',
  name: 'UpgradeTaskDetail',
  component: () => import('@/views/workbench/ota/UpgradeTaskDetail.vue'),
  meta: { title: '任务详情', hidden: true, group: 'fleet', roles: ['ADMIN', 'OPERATOR'] }
}
```

导航分组（`navGroups` 追加，位于 `access` 之前）：

```js
{
  key: 'fleet',
  label: '设备运维',
  items: [
    { path: '/workbench/ota/firmwares', title: '固件管理', icon: 'package', roles: ['ADMIN', 'OPERATOR'] },
    { path: '/workbench/ota/tasks', title: '升级任务', icon: 'operation', roles: ['ADMIN', 'OPERATOR'] }
  ]
}
```

> 图标沿用既有 `assets/svg/index.js` 中已注册的 `package` / `operation`，不新增图标资源。

API 模块（`api/ota.js`，遵循 `dashboard.js` 范式）：

```js
import api from './axios'

export const otaApi = {
  /** 固件包列表：可按 productId 过滤，返回 OtaFirmwareVO[]。 */
  listFirmwares: (params) => api.get('/ota/firmwares', { params }),
  /** 固件包详情。 */
  firmwareDetail: (id) => api.get(`/ota/firmwares/${id}`),
  /** 上传固件包：data 为 FormData(productId, version, file, description)。 */
  uploadFirmware: (formData) =>
    api.post('/ota/firmwares', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  /** 删除固件包：被任务引用返回 6234。 */
  removeFirmware: (id) => api.delete(`/ota/firmwares/${id}`),
  /** 升级任务列表（分页）。 */
  listTasks: (params) => api.get('/ota/tasks', { params }),
  /** 任务详情。 */
  taskDetail: (id) => api.get(`/ota/tasks/${id}`),
  /** 任务逐台记录（分页 + status 过滤）。 */
  taskRecords: (id, params) => api.get(`/ota/tasks/${id}/records`, { params }),
  /** 重投未成功记录。 */
  retryTask: (id) => api.post(`/ota/tasks/${id}/retry`),
  /** 删除任务及其记录。 */
  removeTask: (id) => api.delete(`/ota/tasks/${id}`)
}

export default otaApi
```

### 8.2 交互要点

1. **上传固件**：选择产品 + 版本 + 文件（前端按 `OTA_MAX_FILE_SIZE` 提示大小上限），提交后刷新列表并提示「上传成功」；版本重复（`6233`）就地提示。
2. **新建任务**：固件包下拉（选定后锁定产品），目标选择复用设备选择器（手选 / 分组 / 标签），产品维度默认取固件所属产品且不可改。目标为空在前端即拦截。
3. **任务列表**：展示进度条（`success/total`）、状态标签（RUNNING 蓝 / SUCCESS 绿 / PARTIAL 橙 / FAILED 红），点击进入详情。
4. **任务详情**：顶部计数卡片 + 目标快照；下方记录表按 `status` 过滤，逐台展示进度条与 `message`；「重投未成功」按钮二次确认后调用 `retry`。
5. **错误码映射**：`6231/6234/6236/6237/6238` 统一走 `ElMessage.error` 展示后端 `message`。

---

## 9. 错误码与配置

### 9.1 错误码（`ResultCode` 追加）

当前最大为 `6230 DASHBOARD_LIMIT_EXCEEDED`（第 89 行以 `;` 收尾），**追加前须把该行 `;` 改为 `,`**，再续写：

```java
OTA_FIRMWARE_NOT_FOUND(6231, "固件包不存在", HttpStatus.NOT_FOUND),
OTA_FIRMWARE_INVALID(6232, "固件包参数非法（版本格式、文件缺失或超出大小上限）", HttpStatus.BAD_REQUEST),
OTA_FIRMWARE_DUPLICATE(6233, "同产品下该固件版本已存在", HttpStatus.BAD_REQUEST),
OTA_FIRMWARE_IN_USE(6234, "固件包已被升级任务引用，无法删除", HttpStatus.BAD_REQUEST),
OTA_UPLOAD_FAILED(6235, "固件文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR),
OTA_TASK_NOT_FOUND(6236, "升级任务不存在", HttpStatus.NOT_FOUND),
OTA_TASK_INVALID(6237, "升级任务参数非法（目标为空、超限或与固件产品不匹配）", HttpStatus.BAD_REQUEST),
OTA_TASK_STATE_INVALID(6238, "升级任务状态不允许该操作", HttpStatus.BAD_REQUEST),
OTA_PROGRESS_INVALID(6239, "OTA 进度回传报文字段非法", HttpStatus.BAD_REQUEST);
```

### 9.2 配置（`application.yml`，插在 `dashboard` 之后、`logging` 之前）

```yaml
  # OTA 固件升级（T-22）：固件上传、批次下发与进度回传
  ota:
    enabled: ${OTA_ENABLED:true}                                       # 是否启用 OTA 能力（关闭后上传与建任务均被拒）
    storage-dir: ${OTA_STORAGE_DIR:/var/lib/mqtt/ota}                  # 固件落盘目录（容器内共享卷挂载点）
    public-base-url: ${OTA_PUBLIC_BASE_URL:http://localhost/firmware}  # 设备侧固件下载地址前缀（nginx 静态托管）
    max-file-size: ${OTA_MAX_FILE_SIZE:33554432}                       # 单固件大小上限（字节，默认 32MB）
    task-max-devices: ${OTA_TASK_MAX_DEVICES:500}                      # 单任务目标设备数上限
    dispatch-interval-ms: ${OTA_DISPATCH_INTERVAL_MS:60000}            # 待下发补投巡检间隔（毫秒）
    dispatch-batch-size: ${OTA_DISPATCH_BATCH_SIZE:200}                # 单轮补投设备数上限
    task-timeout-ms: ${OTA_TASK_TIMEOUT_MS:86400000}                   # 单台升级超时（毫秒，默认 24 小时）
```

同时需在 `spring.servlet.multipart` 放宽上传上限至不小于 `max-file-size`（`max-file-size` 与 `max-request-size`）。

`.env.example`（插在 `DASHBOARD_MAX_PANELS` 之后、运行 profile 之前）：

```dotenv
# OTA 固件升级（T-22）
OTA_ENABLED=true
OTA_STORAGE_DIR=/var/lib/mqtt/ota
OTA_PUBLIC_BASE_URL=http://localhost/firmware
OTA_MAX_FILE_SIZE=33554432
OTA_TASK_MAX_DEVICES=500
OTA_DISPATCH_INTERVAL_MS=60000
OTA_DISPATCH_BATCH_SIZE=200
OTA_TASK_TIMEOUT_MS=86400000
```

`docker-compose.yml`：

- backend `environment` 追加上述 `OTA_*`（采用 `${VAR:-default}` 形式，插在 `DASHBOARD_MAX_PANELS` 与 `healthcheck` 之间）；
- 新增共享卷 `ota_firmware` 并挂载：backend `ota_firmware:/var/lib/mqtt/ota`（读写）、frontend `ota_firmware:/usr/share/nginx/html/firmware:ro`（只读）；
- 顶层 `volumes:` 追加 `ota_firmware: { driver: local }`。

`docker/nginx.conf`：

- `client_max_body_size` 由 `10m` 提升到 `64m`（固件上传经 `/api/ota/firmwares` 走 server 级限制）；
- 在 `location /` **之前**新增固件静态托管：

```nginx
# OTA 固件静态托管（T-22）：置于 location / 之前，避免被 SPA 回落吞掉
location ^~ /firmware/ {
    alias /usr/share/nginx/html/firmware/;
    types { application/octet-stream bin hex apk; }
    default_type application/octet-stream;
    add_header Cache-Control "public, max-age=2592000";
    access_log off;
}
```

`config/SecurityConfig.java`：新增 `requestMatchers("/ota/**").hasAnyRole("ADMIN", "OPERATOR")`（与 `/messages/**`、`/analytics/**` 同档）。固件文件由 nginx 直接托管，**不经过后端鉴权**（设备无法携带 JWT）。

---

## 10. 测试与验收

### 10.1 后端

| 测试类 | 覆盖点 |
| --- | --- |
| `OtaFirmwareServiceImplTest` | 上传落盘与 MD5、版本重复 `6233`、超限 `6232`、删除被引用 `6234` |
| `OtaUpgradeServiceImplTest` | 目标解析（含产品维度）、产品不匹配剔除、`6237`、逐台下发隔离单台失败、计数聚合 |
| `OtaProgressServiceImplTest` | 状态机前向迁移、进度不回退、版本不一致忽略、非法报文丢弃、终态不覆盖 |
| `OtaDispatchSweeperTest` | `PENDING` 补投、超时置 `TIMEOUT`、异常不冒泡 |
| `OtaFirmwareControllerTest` / `OtaUpgradeControllerTest` | 端点契约、参数校验、错误码映射、鉴权 |
| `BatchTargetResolveTest`（扩展） | 既有目标解析回归 + 新增 `productIds` 分支 |
| `IngestDispatcherTest`（扩展） | 第 6 路 fan-out 隔离失败、`device.ota` 事件类型映射 |
| `DeviceCommandServiceImplTest`（扩展） | 保留服务 `ota_upgrade` 免物模型声明、其余服务仍严格校验 |
| ArchUnit `LayeringRulesTest` | `OtaFirmwareService` 等接口不依赖 `..mapper..` |

### 10.2 前端

- `api/__tests__/ota.spec.js`：各方法路径、`params` 透传、multipart 头。
- `FirmwareList.spec.js`：列表渲染、上传大小/版本前端校验、`6233` 提示。
- `UpgradeTasks.spec.js`：固件选择锁定产品、目标为空拦截、提交载荷形状。
- `UpgradeTaskDetail.spec.js`：计数卡片、记录进度渲染、重投二次确认。
- 路由快照测试：新增三条路由的 `group` / `roles` 断言。

### 10.3 验收对照（对应路线图 L9 验收）

| 编号 | 路线图验收 | 本设计落点 | 验证方式 |
| --- | --- | --- | --- |
| A1 | 固件包管理可用 | §5.1 + §6.2 + `FirmwareList.vue` | 前端上传后列表出现，磁盘与 `ota_firmware` 一致 |
| A2 | 创建升级任务后设备**收到固件地址** | §4.4 + §7.3 `dispatch` | 在线设备订阅 `/cmd/down` 收到 `thing.service.ota_upgrade` 及 `url` |
| A3 | 按产品 / 分组 / 标签批次升级 | §4.3 + §7.3 `createTask` | 三种目标分别建任务，`ota_upgrade_record` 条数与预期一致 |
| A4 | 进度**可回传** | §4.2 + §4.5 + §7.3 `applyProgress` | 设备向 `device/{key}/ota` 发报文，记录 `progress` 更新 |
| A5 | 进度**在任务详情可见** | §6.2 + `UpgradeTaskDetail.vue` | 详情页记录表展示 `status`/`progress`/`message`，计数同步 |

**实测结论（2026-10-04，已通过）**：起 `docker/docker-compose.yml` 编排栈（全 `healthy`），以真实 HTTP 请求 + `mosquitto_pub`（经 `docker_mqtt-network` 接 EMQX）模拟设备上行，A1~A5 逐条实测 **PASS=34 / FAIL=0** —— A1 磁盘文件与 `ota_firmware` 记录的 `md5` / `file_size` / 落盘路径三者一致；A2 订阅端实收 `thing.service.ota_upgrade` 且 `params` 含 `url` / `version` / `md5` / `size`；A3 三种目标各建 1 任务、逐任务记录数 = 目标设备数；A4 记录 `progress` 由 `downloading@30` 推进至 `success@100`、`status` / `message` 落库；A5 记录表暴露 `status` / `progress` / `message`、三项计数与任务状态同步（前端页面渲染沿用既有契约、未做浏览器实测）。对应实施计划 P6 与 `总督促文档.md` §3.15 已同步勾选。

---

## 11. 兼容与回滚

- **迁移**：`V11` 仅新增三张表，不修改任何既有列；应用回滚到旧版本后，旧代码忽略新表，数据无副作用。
- **ACL**：零改动。新增的 `device/{key}/ota` 上行在 `allowDevice` PUBLISH 判定下天然放行，无需改 `AclEvaluator`。
- **命令服务**：`RESERVED_SERVICES` 只新增 `ota_upgrade` 一个标识，其余服务校验路径不变，既有命令行为不受影响。
- **目标解析**：`BatchTargetRequest.productIds` 为可选字段，为空即跳过；既有批量端点行为不变。
- **nginx / compose**：`client_max_body_size` 提升只放宽上限、不影响既有请求；新增 `location ^~ /firmware/` 与 `location /` 无冲突（前缀更长且置于前）；新增共享卷仅新增挂载，不动既有卷。
- **回滚开关**：`app.ota.enabled=false` 时上传与建任务返回 `OTA_*` 拒绝，订阅与巡检可保留（无记录即空跑），无需回退二进制。

---

## 12. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| 固件文件公开可下载（无鉴权） | 固件被未授权方拉取 | URL 含不透明 `firmwareId`；文档显式标注为已知取舍；如需可后续加签名 token |
| 静态托管为单机卷 | 多副本部署时各副本 nginx 卷不一致 | 当前为单机 Compose；横向扩展时切换为共享存储/NAS 或对象存储，接口契约不变 |
| `client_max_body_size` 提升到 64m | 应用层被大体积请求冲击 | 后端 `max-file-size` 独立约束为 32MB；`/api/ota/` 端点鉴权为 ADMIN/OPERATOR |
| 大文件上传占用请求线程 | 上传期间并发下降 | 单文件上限 32MB、上传限 ADMIN/OPERATOR；后续可改分片上传 |
| 设备长期离线导致记录悬挂 | 任务长期 `RUNNING` | `task-timeout-ms` 巡检置 `TIMEOUT`，任务转为 `PARTIAL`/`FAILED` 终态 |
| 设备回传报文版本与任务不一致 | 误更新其它任务 | 仅匹配「该设备最新非终态记录 + 版本一致」，否则忽略记 WARN |
| 进度回传频率过高 | 库写放大 | 单设备单记录更新，进度单调；`refreshCounters` 按任务聚合而非逐条 |
| 巡检线程预算紧张 | 影响既有 5 个 Sweeper | `ScheduleConfig` 线程池当前为 3；OTA 巡检采用 `dispatch-interval-ms` 低频（默认 60s）并限制单轮批量，必要时提升池大小 |
| 平台保留服务绕过物模型校验 | 参数缺乏物模型约束 | 仅 `ota_upgrade` 一个标识，入参由 `OtaUpgradeServiceImpl` 自行构造校验；白名单常量集中管理 |

---

## 13. 与路线图的对应

- **链路**：L9 OTA 固件升级（`docs/平台功能链路路线图.md` §3 第 112~116 行）。
- **目标对应**：固件包管理（§5.1 / §6.2）、按产品/分组/标签批次升级（§4.3 / §7.3）、进度回传（§4.2 / §4.5 / §7.3）。
- **范围对应**：固件包表与静态托管（§4.1 / §5.1）、升级任务与批次（§5.2 / §5.3）、进度与结果回传（§6.3）、前端固件与升级任务页（§8）。
- **依赖对应**：L5 分组/标签目标解析（T-18，扩展 `resolveTarget`）、L2 下发通道（T-15，复用 `DeviceCommandService#invoke`）。
- **验收对应**：§10.3 A1~A5 与路线图四条验收逐条对齐。
- **文档回填**：交付后回填 `总督促文档.md`、`ARCHITECTURE.md`（新增 OTA 模块、主题与数据流）、`DEPLOYMENT.md`（`OTA_*` 配置、固件卷、nginx `location` 与 `client_max_body_size`），并在路线图 L9 追加交付记录。
