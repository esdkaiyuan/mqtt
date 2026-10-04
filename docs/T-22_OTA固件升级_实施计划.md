# T-22 OTA 固件升级（OTA Firmware Upgrade） — 实施计划

> 版本：v1.1　创建日期：2026-10-03　最后更新：2026-10-04
> 上游依据：[`T-22_OTA固件升级_设计文档.md`](./T-22_OTA固件升级_设计文档.md)
> 交付原则：端到端可用（迁移 + 实体 + Mapper + 服务 + 接口 + 前端固件管理页 / 升级任务列表页 / 任务详情页 + 归档与静态托管 + 测试 + 文档），不做只有后端没有入口的半成品。
> 主题策略：**新建三张表**（`ota_firmware` / `ota_upgrade_task` / `ota_upgrade_record`，Flyway `V11`，只加表）；固件文件落盘共享卷 `ota_firmware`，由 frontend 容器 nginx 静态托管；固件地址**复用既有 `/cmd/down` 命令通道**（`DeviceCommandService#invoke`，`service` / `ota_upgrade`），**不改 ACL、不新增下行主题**；新增上行主题 `device/{key}/ota`，由 `IngestDispatcher` **第 6 路 fan-out** 落库；目标解析扩展 `BatchTargetRequest.productIds`；**不改 `V1`~`V10`**；回滚只需 `app.ota.enabled=false` 或摘除端点 / 页面 / 订阅，可选删表。

---

## P1 迁移、实体、DTO、常量与错误码

- [x] `resources/db/migration/V11__ota_firmware.sql`（**只加表**，三段 `CREATE TABLE IF NOT EXISTS`，无 `ALTER`；设计文档 §5.1~§5.3）
  - `ota_firmware`：`id` / `user_id` / `product_id` / `version VARCHAR(64)` / `file_name VARCHAR(255)` / `file_path VARCHAR(512)` / `file_size BIGINT` / `md5 CHAR(32)` / `description VARCHAR(255) NULL` / `created_at DATETIME(3)` / `updated_at DATETIME(3) ON UPDATE CURRENT_TIMESTAMP(3)`
    - `UNIQUE KEY uk_product_version (product_id, version)`；索引 `idx_user (user_id)` / `idx_product (product_id)`
    - 外键 `fk_ota_firmware_user` → `sys_user(id) ON DELETE CASCADE`、`fk_ota_firmware_product` → `product(id) ON DELETE CASCADE`
  - `ota_upgrade_task`：`id` / `user_id` / `firmware_id` / `product_id`（冗余）/ `name VARCHAR(64)` / `target_json JSON` / `total_count INT DEFAULT 0` / `dispatched_count INT DEFAULT 0` / `success_count INT DEFAULT 0` / `failed_count INT DEFAULT 0` / `status VARCHAR(16)` / `created_at` / `updated_at`
    - 索引 `idx_user (user_id)` / `idx_firmware (firmware_id)`
    - 外键 `fk_ota_task_user` → `sys_user(id) ON DELETE CASCADE`、`fk_ota_task_firmware` → `ota_firmware(id)`（**不级联**，被引用固件禁止删除）
  - `ota_upgrade_record`：`id` / `task_id` / `device_id` / `firmware_id` / `version VARCHAR(64)`（冗余）/ `status VARCHAR(16)` / `progress INT DEFAULT 0` / `message VARCHAR(255) NULL` / `command_id VARCHAR(64) NULL` / `dispatched_at DATETIME(3) NULL` / `last_report_at DATETIME(3) NULL` / `created_at` / `updated_at`
    - `UNIQUE KEY uk_task_device (task_id, device_id)`；索引 `idx_device (device_id)` / `idx_status (status)` / `idx_task_status (task_id, status)`
    - 外键 `fk_ota_record_task` → `ota_upgrade_task(id) ON DELETE CASCADE`、`fk_ota_record_device` → `device(id) ON DELETE CASCADE`
  - 三表**均无逻辑删除列**（不受 `logic-delete-field: deleted` 影响）；头部中文注释说明：只加表、不做自动清理、回滚可 `DROP TABLE`
- [x] `entity/OtaFirmware.java`（`@TableName("ota_firmware")`，`@Data`）
  - 字段 `id` / `userId` / `productId` / `version` / `fileName` / `filePath` / `fileSize`（`Long`）/ `md5` / `description` / `createdAt`（`LocalDateTime`）/ `updatedAt`
  - **不含** `deleted`（表无该列）
- [x] `entity/OtaUpgradeTask.java`（`@TableName("ota_upgrade_task")`，`@Data`）
  - 字段 `id` / `userId` / `firmwareId` / `productId` / `name` / `targetJson`（`String`，承载原始 JSON，由服务层 `ObjectMapper` 解析，**实体不耦合 Jackson 注解**）/ `totalCount` / `dispatchedCount` / `successCount` / `failedCount` / `status` / `createdAt` / `updatedAt`
- [x] `entity/OtaUpgradeRecord.java`（`@TableName("ota_upgrade_record")`，`@Data`）
  - 字段 `id` / `taskId` / `deviceId` / `firmwareId` / `version` / `status` / `progress`（`Integer`）/ `message` / `commandId` / `dispatchedAt`（`LocalDateTime`）/ `lastReportAt`（`LocalDateTime`）/ `createdAt` / `updatedAt`
- [x] `common/constant/OtaStatusValue.java`（对齐既有 `common/constant/DeviceStatusValue` 范式）
  - 记录状态 `RECORD_PENDING="PENDING"` / `RECORD_DISPATCHED="DISPATCHED"` / `RECORD_DOWNLOADING="DOWNLOADING"` / `RECORD_FLASHING="FLASHING"` / `RECORD_SUCCESS="SUCCESS"` / `RECORD_FAILED="FAILED"` / `RECORD_TIMEOUT="TIMEOUT"`
  - 任务状态 `TASK_RUNNING="RUNNING"` / `TASK_SUCCESS="SUCCESS"` / `TASK_PARTIAL="PARTIAL"` / `TASK_FAILED="FAILED"`
  - 设备上行状态 `DEVICE_DOWNLOADING="downloading"` / `DEVICE_FLASHING="flashing"` / `DEVICE_SUCCESS="success"` / `DEVICE_FAILED="failed"`
  - 辅助集合 `Set<String> NON_TERMINAL_STATUSES`（`PENDING/DISPATCHED/DOWNLOADING/FLASHING`）、`Set<String> TERMINAL_STATUSES`（`SUCCESS/FAILED/TIMEOUT`）
- [x] `common/ResultCode.java` **先把第 89 行** `DASHBOARD_LIMIT_EXCEEDED(6230, "看板数量超出上限", HttpStatus.BAD_REQUEST);` **的 `;` 改为 `,`**，再续写 `6231~6239`（设计文档 §9.1）
  - `OTA_FIRMWARE_NOT_FOUND(6231, NOT_FOUND)` / `OTA_FIRMWARE_INVALID(6232, BAD_REQUEST)` / `OTA_FIRMWARE_DUPLICATE(6233, BAD_REQUEST)` / `OTA_FIRMWARE_IN_USE(6234, BAD_REQUEST)` / `OTA_UPLOAD_FAILED(6235, INTERNAL_SERVER_ERROR)` / `OTA_TASK_NOT_FOUND(6236, NOT_FOUND)` / `OTA_TASK_INVALID(6237, BAD_REQUEST)` / `OTA_TASK_STATE_INVALID(6238, BAD_REQUEST)` / `OTA_PROGRESS_INVALID(6239, BAD_REQUEST)`
  - 产品不存在 / 设备越权**复用**既有 `6002` / `2002` / `2003`，不新增同义码
- [x] `service/DeviceCommandService.java` 新增两个常量（**只加常量，不改既有方法**）
  - `String SOURCE_OTA = "OTA";`（来源：OTA 固件升级，仅用于区分来源）
  - `String SERVICE_OTA_UPGRADE = "ota_upgrade";`（平台保留服务标识）
- [x] `dto/request/OtaTaskCreateRequest.java`（`@Data`，`@Valid`）
  - `String name`（非空、≤ 64）/ `Long firmwareId`（非空）/ `BatchTargetRequest target`（非空，**复用 T-18 目标 DTO**）
- [x] `dto/request/BatchTargetRequest.java` 追加字段 `List<Long> productIds`（**纯追加、向后兼容**：`deviceIds` / `groupIds` / `tagIds` 保留）
- [x] `dto/response/OtaFirmwareVO.java`：`id` / `productId` / `productName` / `version` / `fileName` / `fileSize` / `md5` / `downloadUrl`（服务层按 `public-base-url + file_path` 拼装）/ `description` / `createdAt`
  - 实施校正：`createdAt` 定为 **`LocalDateTime`**（原写 `String`）。依据 `application.yml` 的 `spring.jackson.date-format: yyyy-MM-dd HH:mm:ss` 与既有 `DashboardDetailVO` / `DashboardSummaryVO` 范式一致，避免同一项目内两种时间契约
- [x] `dto/response/OtaTaskVO.java`（列表项）：`id` / `name` / `firmwareId` / `version` / `productId` / `productName` / `totalCount` / `dispatchedCount` / `successCount` / `failedCount` / `status` / `createdAt`
- [x] `dto/response/OtaTaskDetailVO.java`（详情）：`OtaTaskVO` 全字段 + `target`（目标快照，解析 `target_json` 后的 `BatchTargetRequest`）
  - 实施校正：写为**独立类、字段展开**（不继承 `OtaTaskVO`），与既有 VO 一律平铺的风格一致
- [x] `dto/response/OtaRecordVO.java`（逐台记录）：`deviceId` / `deviceName` / `deviceKey` / `version` / `status` / `progress` / `message` / `commandId` / `dispatchedAt` / `lastReportAt`
- [x] `service/OtaProgressPayload.java`（**只读模型，`record`**）：`String version` / `String status` / `Integer progress` / `String message`
  - 静态工厂 `parse(String payload)`：实施校正为 **`parse(ObjectMapper objectMapper, String payload)`**——`ObjectMapper` 由调用方注入（与项目其余服务一致，不在本类持有静态实例）
  - 非法情形（非 JSON 对象 / 必填缺失 / `status` 未知 / `progress` 越界）一律返回 `null` 交调用方丢弃并记 WARN；`success` 恒推 `progress=100`；`message` 按 255 截断

## P2 Mapper 与聚合 SQL

- [x] `mapper/OtaFirmwareMapper.java`（`@Mapper`，继承 `BaseMapper<OtaFirmware>`，纯单表 CRUD，无自定义 SQL / 无 XML）
  - 唯一性由 `uk_product_version` 兜底，服务层先查后插并在捕获 `DuplicateKeyException` 时抛 `6233`
- [x] `mapper/OtaUpgradeTaskMapper.java`（`@Mapper`，继承 `BaseMapper<OtaUpgradeTask>` + 一个自定义方法）
  - `int refreshCounters(@Param("taskId") Long taskId)`
- [x] `resources/com/mqtt/cloud/mapper/OtaUpgradeTaskMapper.xml`（**XML 位置与既有 `DevicePropertyHistoryMapper.xml` 一致**）
  - 实施校正：目标方言为 **MySQL 8**（`driver-class-name: com.mysql.cj.jdbc.Driver`），MySQL 不支持 `UPDATE ... SET ... FROM (...)`，故**采用 `UPDATE ota_upgrade_task t JOIN (...) agg ON t.id = agg.task_id SET ...` 形式**（设计文档 §7.2 已授权的等价改写）；外层另加 `WHERE t.id = #{taskId}` 保证仅命中一行
  - `refreshCounters`（设计文档 §7.2）：`UPDATE ota_upgrade_task t SET ... FROM (SELECT task_id, COUNT(*), SUM(CASE WHEN status <> 'PENDING' ...), ... , CASE WHEN ... END AS status FROM ota_upgrade_record WHERE task_id = #{taskId} GROUP BY task_id) agg WHERE t.id = agg.task_id`
    - XML 中 `<>` 写 `&lt;&gt;`、`>` 写 `&gt;`；`status` 判定：存在非终态 → `RUNNING`；全 `SUCCESS` → `SUCCESS`；`SUCCESS` 数为 0 → `FAILED`；否则 `PARTIAL`
    - 实现时若目标方言不便用 `FROM` 子查询，改写为 `UPDATE ota_upgrade_task t JOIN (...) agg ON t.id = agg.task_id SET ...`，语义不变
    - **全 `#{}` 占位符，无 `${}`**
- [x] `mapper/OtaUpgradeRecordMapper.java`（`@Mapper`，继承 `BaseMapper<OtaUpgradeRecord>` + 两个自定义方法；设计文档 §7.1）
  - `List<OtaUpgradeRecord> selectDispatchCandidates(@Param("status") String status, @Param("limit") int limit)`：`PENDING` 且设备在线（`JOIN device` 取 `status=ONLINE`）待补投记录
  - `OtaUpgradeRecord selectLatestActiveByDevice(@Param("deviceId") Long deviceId)`：该设备最新一条非终态记录（`ORDER BY id DESC LIMIT 1`）
- [x] `resources/com/mqtt/cloud/mapper/OtaUpgradeRecordMapper.xml`
  - `selectDispatchCandidates`：`SELECT r.* FROM ota_upgrade_record r JOIN device d ON d.id = r.device_id WHERE r.status = #{status} AND d.status = 'ONLINE' ORDER BY r.id ASC LIMIT #{limit}`（`device.status` 取值以既有 `DeviceStatusValue` 为准，实施时核对常量）
  - `selectLatestActiveByDevice`：`SELECT * FROM ota_upgrade_record WHERE device_id = #{deviceId} AND status IN ('PENDING','DISPATCHED','DOWNLOADING','FLASHING') ORDER BY id DESC LIMIT 1`
  - 头部注释说明：候选命中 `idx_status` / `idx_task_status`，活跃记录命中 `idx_device`
  - 实施校正：落库列改为显式 `<sql id="recordColumns">` 片段 + `<include>`（照 `DeviceCommandRecordMapper.xml` 的 `commandColumns` 范式），替代 `SELECT r.*`，避免后续加列时投影漂移

## P3 服务层、配置与摄取旁路

- [x] `service/OtaFirmwareService.java`（接口，**不得依赖 `..mapper..`**，ArchUnit 门禁）
  - `OtaFirmwareVO upload(Long userId, Long productId, String version, String description, MultipartFile file)`
  - `List<OtaFirmwareVO> list(Long userId, Long productId)`
  - `OtaFirmwareVO detail(Long userId, Long id)`
  - `void remove(Long userId, Long id)`
  - `OtaFirmwareService.Download download(Long userId, Long id)`；嵌套 `record Download(String fileName, String filePath, long fileSize)`
- [x] `service/OtaUpgradeService.java`（接口，**不得依赖 `..mapper..`**）
  - `OtaTaskDetailVO createTask(Long userId, OtaTaskCreateRequest request)`
  - `IPage<OtaTaskVO> listTasks(Long userId, long page, long size)`
  - `OtaTaskDetailVO detail(Long userId, Long id)`
  - `IPage<OtaRecordVO> listRecords(Long userId, Long id, String status, long page, long size)`
  - `int retry(Long userId, Long id)`
  - `void remove(Long userId, Long id)`
  - `int dispatchTask(Long taskId, List<Long> recordIds)`（供巡检 / 建任务共用，**含单台隔离**）
  - `int sweepPending(int limit)`；`int sweepTimeout(long timeoutMs)`
- [x] `service/OtaProgressService.java`（接口，**不得依赖 `..mapper..`**）
  - `void handle(List<ResolvedEvent> events)`（**形状照 `CommandReplyService#handle`**：逐条 `try/catch` 隔离）
- [x] `service/impl/OtaFirmwareServiceImpl.java`（流程见设计文档 §7.1）
  - 构造注入 `OtaFirmwareMapper` / `OtaUpgradeTaskMapper` / `OtaProperties` / `ObjectMapper`
  - `enabled=false` → 上传 / 建任务直接抛 `OTA_FIRMWARE_INVALID`（`6232`）
  - **上传**：校验产品存在（复用 `ProductService` / `ProductMapper`，不存在 → `6002`）；`version` 匹配 `^[0-9A-Za-z._-]{1,64}$` 否则 `6232`；`file` 非空且 `size <= max-file-size` 否则 `6232`；同产品版本已存在 → `6233`
  - 落盘：相对路径 `{productId}/{version}/{fileName}`，绝对路径 `{storage-dir}/{相对路径}`；父目录不存在则创建；计算 MD5（FileChannel + `MessageDigest` 流式）；落盘异常 → `6235`；`insert` 记录
  - **删除**：存在引用（`ota_upgrade_task.firmware_id`）→ `6234`；否则删磁盘文件 + 删记录（`firmware` 不存在 → `6231`）
  - **download**：返回 `Download`（Controller 用 `Resource` / `InputStreamResource` 流式写回）
- [x] `service/impl/OtaUpgradeServiceImpl.java`（流程见设计文档 §7.3）
  - 构造注入 `OtaFirmwareMapper` / `OtaUpgradeTaskMapper` / `OtaUpgradeRecordMapper` / `DeviceMapper` / `DeviceBatchService` / `DeviceCommandService` / `OtaProperties` / `ObjectMapper`
  - **createTask**：固件存在且 `user_id == userId` 否则 `6231` → `deviceBatchService.resolveTarget(userId, target)` 解析（**含产品维度**）→ 剔除产品与固件产品不一致的设备 → 空集或超 `task-max-devices` → `6237` → 插入任务（`status=RUNNING`，`target_json` 快照）→ 批量插记录（`status=PENDING`，`progress=0`，`version` 冗余）→ `dispatchTask(taskId, recordIds)` 即时下发 → `refreshCounters(taskId)` → 返回详情
  - **dispatch（单台）**：构造 `new DeviceCommandService.CommandInvoke(deviceId, productId, DeviceCommandService.TYPE_SERVICE, DeviceCommandService.SERVICE_OTA_UPGRADE, paramsJson, DeviceCommandService.CALL_TYPE_ASYNC, DeviceCommandService.SOURCE_OTA, operatorId)`；`paramsJson = {"url":..., "version":..., "md5":..., "size":...}`；成功回写 `command_id` / `dispatched_at` / `status=DISPATCHED`；**单台异常只记该条 `message`，保持 `PENDING` 待补投，不阻断整批**
  - **retry**：任务不存在 → `6236`；`status=SUCCESS` → `6238`；将 `PENDING/FAILED/TIMEOUT` 记录重置为 `PENDING`、清 `message`，再 `dispatchTask`
  - **remove**：`RUNNING` 任务删除 → `6238`；否则删除任务（记录 `ON DELETE CASCADE`）
  - **sweepTimeout**：`DISPATCHED/DOWNLOADING/FLASHING` 且 `COALESCE(last_report_at, dispatched_at) < now - timeoutMs` → `TIMEOUT`，随后对受影响任务 `refreshCounters`
- [x] `service/impl/OtaProgressServiceImpl.java`（`@Slf4j @Service @RequiredArgsConstructor`，**逐条 try/catch + micrometer 计数**，形状照 `CommandReplyServiceImpl`）
  - 常量 `MESSAGE_TYPE_OTA = "ota"`、`ERROR_MESSAGE_MAX = 255`、`CODE_*` 计数标签
  - `handle`：`events` 空短路；逐条 `if (!MESSAGE_TYPE_OTA.equals(event.record().messageType())) continue;` → `applyProgress(event.device(), event.record().payload())`；异常 `count("invalid")` + `log.warn`
  - `applyProgress`：解析 payload → `OtaProgressPayload`，非法丢弃记 WARN → `selectLatestActiveByDevice(deviceId)`，无则忽略（DEBUG）→ 版本不一致忽略记 WARN → 终态记录忽略 → 状态机前向迁移（进度单调不减、`status` 只允许前向）→ 更新 `progress` / `message`（超长截断 255）/ `last_report_at`；终态时 `refreshCounters(taskId)`
  - 计数器 `meterRegistry.counter("ota_progress_total", "result", result).increment()`
- [x] `config/OtaProperties.java`（`@Data @Component @ConfigurationProperties(prefix = "app.ota")`）
  - 字段 `enabled=true` / `storageDir="/var/lib/mqtt/ota"` / `publicBaseUrl="http://localhost/firmware"` / `maxFileSize=33554432` / `taskMaxDevices=500` / `dispatchIntervalMs=60000` / `dispatchBatchSize=200` / `taskTimeoutMs=86400000`（带中文注释）
- [x] `config/OtaDispatchSweeper.java`（`SchedulingConfigurer`，**形状照 `config/PropertyHistorySweeper`**）
  - `enabled=false` 或 `dispatchIntervalMs<=0` → **不注册**任务（`log.info`）
  - 单次 `sweep()`：`sweepPending(dispatchBatchSize)` + `sweepTimeout(taskTimeoutMs)`；整体 `try/catch` 只记 WARN，**不冒泡、不中断调度线程**
- [x] `service/impl/DeviceCommandServiceImpl.java` 新增**平台保留服务白名单**（设计文档 §4.4）
  - `private static final Set<String> RESERVED_SERVICES = Set.of(DeviceCommandService.SERVICE_OTA_UPGRADE);`
  - `validateService(...)` 首行 `if (RESERVED_SERVICES.contains(identifier)) { return; }`（**其余校验路径不变**）
- [x] `service/impl/DeviceBatchServiceImpl.java` 扩展 `resolveTarget`（设计文档 §4.3）
  - 追加产品维度：`productIds` 非空时 `deviceMapper` 按 `product_id IN (...)` 取设备并入集合；为空即跳过（**既有 `deviceIds`/`groupIds`/`tagIds` 行为完全不变**）；末尾仍按归属二次过滤 + 去重
- [x] `mqtt/MqttMessageHandler.java` 新增第 5 条订阅 + `ota` 状态映射（设计文档 §4.2）
  - `subscribe()`：追加 `topics.put("$share/" + group + "/device/+/ota", 1);`（**`parts[2]="ota"` 天然兼容 `IngestRecord` 构造，无需改解析**）
  - `refreshDeviceStatus`：`ota` 归入 `ONLINE`（收到 OTA 报文即证明设备在线）
- [x] `ingest/IngestDispatcher.java` 新增第 6 路 fan-out + `device.ota` 事件类型（设计文档 §4.5）
  - 注入 `OtaProgressService`；与 `commandReplyService.handle(events)` 同级：
    ```java
    try { otaProgressService.handle(events); }
    catch (Exception e) { log.warn("OTA 进度回传分发失败，跳过本批: size={}", events.size(), e); }
    ```
  - `resolveEventType` 的 `switch(messageType)` 补 `case "ota" -> "device.ota";`
- [x] `resources/application.yml`：`app` 下新增 `ota` 块，**紧随 `dashboard` 块之后、`# 日志配置` 之前**（设计文档 §9.2）
  ```yaml
  app:
    dashboard:
      max-count: ${DASHBOARD_MAX_COUNT:20}
      max-panels: ${DASHBOARD_MAX_PANELS:12}
    # OTA 固件升级（T-22）：固件上传、批次下发与进度回传
    ota:
      enabled: ${OTA_ENABLED:true}
      storage-dir: ${OTA_STORAGE_DIR:/var/lib/mqtt/ota}
      public-base-url: ${OTA_PUBLIC_BASE_URL:http://localhost/firmware}
      max-file-size: ${OTA_MAX_FILE_SIZE:33554432}
      task-max-devices: ${OTA_TASK_MAX_DEVICES:500}
      dispatch-interval-ms: ${OTA_DISPATCH_INTERVAL_MS:60000}
      dispatch-batch-size: ${OTA_DISPATCH_BATCH_SIZE:200}
      task-timeout-ms: ${OTA_TASK_TIMEOUT_MS:86400000}
  ```
- [x] `resources/application.yml`：`spring.servlet.multipart` 放宽上传上限至**不小于** `max-file-size`（`max-file-size` 与 `max-request-size`，实施时核对既有 `multipart` 配置块并只在必要时提升）
- [x] `docker/docker-compose.yml`
  - backend `environment`：在 `DASHBOARD_MAX_PANELS` 与 `healthcheck` 之间追加 `OTA_*` 8 项（`${VAR:-default}` 形式）
  - backend `volumes`：追加 `ota_firmware:/var/lib/mqtt/ota`
  - frontend `volumes`（**现无该段，新增**）：`ota_firmware:/usr/share/nginx/html/firmware:ro`
  - 顶层 `volumes:`：追加 `ota_firmware: { driver: local }`
- [x] `docker/nginx.conf`
  - `client_max_body_size` `10m` → `64m`（固件上传经 `/api/ota/firmwares` 走 server 级限制）
  - 在 `location /` **之前**新增固件静态托管：
    ```nginx
    # OTA 固件静态托管（T-22）：^~ 保证命中后不再尝试正则与 location /
    location ^~ /firmware/ {
        alias /usr/share/nginx/html/firmware/;
        types { application/octet-stream bin hex apk; }
        default_type application/octet-stream;
        add_header Cache-Control "public, max-age=2592000";
        access_log off;
    }
    ```
- [x] `.env.example`：在 `DASHBOARD_MAX_PANELS=12` 与 `# ========== 运行 profile ==========` 之间追加 `OTA_*` 8 项（`OTA_ENABLED` / `OTA_STORAGE_DIR` / `OTA_PUBLIC_BASE_URL` / `OTA_MAX_FILE_SIZE` / `OTA_TASK_MAX_DEVICES` / `OTA_DISPATCH_INTERVAL_MS` / `OTA_DISPATCH_BATCH_SIZE` / `OTA_TASK_TIMEOUT_MS`）

## P4 接口层

- [x] `controller/OtaFirmwareController.java`
  - `@RestController`、`@RequestMapping("/ota/firmwares")`、`@Tag(name="OTA 固件包", description="固件包上传、列表、详情、下载与删除；仅 ADMIN/OPERATOR")`
  - `@PostMapping` `upload(@RequestParam Long productId, @RequestParam String version, @RequestParam(required=false) String description, @RequestPart MultipartFile file)`：`Long userId = SecurityUtils.requireUserId();`
  - `@GetMapping` `list(@RequestParam(required=false) Long productId)` / `@GetMapping("/{id}")` `detail` / `@GetMapping("/{id}/download")` `download`（`ResponseEntity<Resource>` + `Content-Disposition: attachment`）/ `@DeleteMapping("/{id}")` `remove`
  - `@Operation` 描述逐条含错误码（`6231` / `6232` / `6233` / `6234` / `6235` / `6002`）
- [x] `controller/OtaUpgradeController.java`
  - `@RestController`、`@RequestMapping("/ota/tasks")`、`@Tag(name="OTA 升级任务", description="升级任务创建、列表、详情、逐台记录与重投")`
  - `@PostMapping` `create(@Valid @RequestBody OtaTaskCreateRequest request)` / `@GetMapping` `list(@RequestParam page/size)` / `@GetMapping("/{id}")` `detail` / `@GetMapping("/{id}/records")` `records(@RequestParam(required=false) String status, page, size)` / `@PostMapping("/{id}/retry")` `retry` / `@DeleteMapping("/{id}")` `remove`
  - 全部 `Long userId = SecurityUtils.requireUserId();` 后传入服务层；`@Operation` 含 `6231` / `6236` / `6237` / `6238`
- [x] `config/SecurityConfig.java`：在既有 `/messages/**`,`/analytics/**` 之后追加 `requestMatchers("/ota/**").hasAnyRole("ADMIN", "OPERATOR")`（**requestMatcher 用去掉 context-path `/api` 的路径**）
  - 固件文件由 nginx 直接托管，**不经后端鉴权**（设备无法携带 JWT）
- [x] 路由冲突检查：`/ota/**` 为**新顶级前缀**，与既有 `/devices` / `/products` / `/analytics` / `/properties` / `/dashboards` 无重叠

## P5 前端

- [x] `src/api/ota.js`（`otaApi`，**照 `api/dashboard.js` 范式**；设计文档 §8.1）
  - `listFirmwares(params)` / `firmwareDetail(id)` / `uploadFirmware(formData)`（`headers: { 'Content-Type': 'multipart/form-data' }`）/ `removeFirmware(id)` / `listTasks(params)` / `taskDetail(id)` / `taskRecords(id, params)` / `retryTask(id)` / `removeTask(id)`
  - 实施校正：另提供 `downloadFirmware(id)`（`responseType: 'blob'`，供附件流下载），合计 11 个方法，覆盖设计文档 §8.1「firmwares / tasks 全量方法」
- [x] `src/api/__tests__/ota.spec.js`：各方法路径、`params` 透传、multipart 头（13 用例）
- [x] `src/views/workbench/ota/FirmwareList.vue`：固件包列表（产品过滤）+ 上传对话框（产品 / 版本 / 文件 / 说明，前端按 `OTA_MAX_FILE_SIZE` 提示上限）+ 删除（二次确认，`6234` 提示）
  - 实施校正：前端无 `VITE_OTA_MAX_FILE_SIZE` 变量，改为硬编码常量 `MAX_FILE_SIZE = 32 * 1024 * 1024`（与后端 `OTA_MAX_FILE_SIZE` 默认值一致）；版本前端校验 `VERSION_PATTERN = /^[0-9A-Za-z._-]{1,64}$/`，与后端一致
- [x] `src/views/workbench/ota/UpgradeTasks.vue`：任务列表（分页）+ 进度条（`success/total`）+ 状态标签（RUNNING 蓝 / SUCCESS 绿 / PARTIAL 橙 / FAILED 红）+ 新建任务对话框（固件下拉锁定产品；目标选择复用设备选择器，产品维度默认锁定固件产品且不可改；目标为空前端拦截）
  - 实施校正：目标维度实现为「产品 / 分组 / 标签 / 设备」四选一 `el-radio-group`（`targetMode`），产品维度自动取所选固件 `productId`；分组 / 标签复用 `useDeviceGroupTreeQuery` / `useTagListQuery`，设备复用 `deviceApi.getList({ pageNum:1, pageSize:100 })`
- [x] `src/views/workbench/ota/UpgradeTaskDetail.vue`：顶部计数卡片 + 目标快照 + 记录表（按 `status` 过滤，逐台进度条与 `message`）+「重投未成功」按钮（二次确认）
- [x] `src/views/workbench/ota/__tests__/FirmwareList.spec.js` / `UpgradeTasks.spec.js` / `UpgradeTaskDetail.spec.js`：上传校验与列表渲染 / 建任务目标选择与提交载荷 / 进度渲染与重投二次确认
  - 实施校正：三份 spec 合计 33 用例（10 / 13 / 10）
- [x] `src/router/index.js`：新增三条路由（插入 `boards/:id` 之后；设计文档 §8.1）
  - `ota/firmwares` → `FirmwareList.vue`，`meta: { title:'固件管理', icon:'package', group:'fleet', roles:['ADMIN','OPERATOR'] }`
  - `ota/tasks` → `UpgradeTasks.vue`，`meta: { title:'升级任务', icon:'operation', group:'fleet', roles:['ADMIN','OPERATOR'] }`
  - `ota/tasks/:id` → `UpgradeTaskDetail.vue`，`meta: { title:'任务详情', hidden:true, group:'fleet', roles:['ADMIN','OPERATOR'] }`
- [x] `src/components/layout/NavMenu.vue`：`navGroups` 追加分组「设备运维」（`key:'fleet'`，位于 `access` 之前），含「固件管理」/「升级任务」两项（**图标沿用既有 `package` / `operation`，不新增图标资源**）
- [x] 路由快照测试：新增三条路由的 `group` / `roles` 断言
  - 实施校正：同步更新 `routes.spec.js` 中 `toEqual([...])` 子路由名单数组（补 `FirmwareList` / `UpgradeTasks` / `UpgradeTaskDetail`）、`groups` 集合（补 `fleet`）、hidden 名单（补 `UpgradeTaskDetail`），并新增 OTA 路由专项断言（路径 / 分区 / 角色）
- [x] 错误处理沿用 axios 拦截器统一提示（`6231` / `6234` / `6236` / `6237` / `6238`），页面不重复弹错

## P6 测试与验收

- [x] 后端单测（设计文档 §10.1）
  - `OtaFirmwareServiceImplTest`：上传落盘与 MD5 一致；版本重复 → `6233`；版本非法 / 文件超限 / 缺失 → `6232`；产品不存在 → `6002`；删除被引用 → `6234`；删除不存在 → `6231`；落盘异常 → `6235`；`enabled=false` 时上传 / 建任务被拒
  - `OtaUpgradeServiceImplTest`：目标解析（含 `productIds` 维度）条数正确；产品不匹配设备被剔除、剔除后为空 → `6237`；超 `task-max-devices` → `6237`；逐台下发单台失败不阻断整批且保持 `PENDING`；`retry` 对 `SUCCESS` 任务 → `6238`；`refreshCounters` 计数与状态判定正确
  - `OtaProgressServiceImplTest`：状态机前向迁移；进度不回退；版本不一致忽略记 WARN；非法报文丢弃；终态不覆盖；无活跃记录忽略
  - `OtaDispatchSweeperTest`：`enabled=false` / `interval<=0` 不注册；`PENDING` 在线补投；超时置 `TIMEOUT`；单次异常不冒泡（照 `PropertyHistorySweeperTest` 范式）
  - `OtaFirmwareControllerTest` / `OtaUpgradeControllerTest`：端点契约、参数校验、错误码映射、鉴权（ADMIN/OPERATOR）
  - `IngestDispatcherTest`（扩展）：第 6 路 fan-out 隔离失败、`device.ota` 事件类型映射
  - `DeviceCommandServiceImplTest`（扩展）：保留服务 `ota_upgrade` 免物模型声明、其余服务仍严格校验
  - `OtaUpgradeRecordMapperSqlTest`（`@MybatisTest` 或既有集成范式）：`selectDispatchCandidates` 只取在线设备 `PENDING`、受 `LIMIT` 约束；`selectLatestActiveByDevice` 取最新非终态；`refreshCounters` 计数 / 状态与手算一致
  - ArchUnit（`mvn verify`）：`OtaFirmwareService` / `OtaUpgradeService` / `OtaProgressService` 接口**不得**依赖 `..mapper..`；新增类不越层（Controller→Service→Mapper）
- [x] 前端 `npm run lint`（0 error）/ `npm run test` / `npm run build` 全绿（设计文档 §10.2）
  - `ota.spec.js` / `FirmwareList.spec.js` / `UpgradeTasks.spec.js` / `UpgradeTaskDetail.spec.js` + 路由快照断言
- [x] 端到端实测（设计文档 §10.3 的 A1~A5）并逐条勾选 —— **已执行并通过**：起 `docker/docker-compose.yml` 编排栈（backend / frontend / emqx / mysql / redis 全 `healthy`），以真实 HTTP 请求 + `mosquitto_pub`（经 `docker_mqtt-network` 接 EMQX）模拟设备上行，逐条实测 **PASS=34 / FAIL=0**
  - [x] A1：固件包管理可用（`POST /api/ota/firmwares` 上传后列表出现，磁盘文件与 `ota_firmware` 记录一致 —— `md5` / `file_size` / 落盘路径三者比对一致）
  - [x] A2：创建升级任务后在线设备订阅 `/cmd/down` 收到 `thing.service.ota_upgrade` 及 `url`（订阅端实收 `thing.service.ota_upgrade`，`params` 含 `url` / `version` / `md5` / `size`）
  - [x] A3：按产品 / 分组 / 标签三种目标分别建任务，`ota_upgrade_record` 条数与预期一致（三种目标各建 1 任务，逐任务记录数 = 目标设备数）
  - [x] A4：设备向 `device/{key}/ota` 发报文，记录 `progress` 更新（`downloading@30` → `success@100`，`status` / `message` 落库）
  - [x] A5：详情端点记录表展示 `status` / `progress` / `message`，计数与任务状态同步（`totalCount` / `successCount` / `dispatchedCount` = 1，记录明细暴露三字段；前端页面渲染沿用既有契约、未做浏览器实测）
- [x] 联动复核：以 `mosquitto_pub` 经 `docker_mqtt-network` 接 EMQX 模拟设备上行 / 回执，配合真实 HTTP 请求跑通 A1~A5；`scripts/`（`r5-trigger-check.*` 形态）未改动

## P7 文档回填

- [x] `docs/平台功能链路路线图.md`：L9 / T-22 标记已交付并补交付记录；§2 能力域 11「OTA 固件升级」现状由「缺失」改为「已具备固件包管理与批次升级、进度回传（T-22）」；§5 进度指向下一任务；§7 变更记录加 **v1.9** 并更新头部版本与状态
- [x] `docs/总督促文档.md`：T-22 交付记录（**分段读，文件超 64KB**）——已升 **v1.26**（§1 交付记录表 + §3.15 交付范围与未做项（A1~A5 实测已收口）+ §变更记录）；先以 v1.25 记载「现场未实测」，补测通过后同版补记 v1.26 实测结论
- [x] `docs/ARCHITECTURE.md`：OTA 模块与数据流（三张表、固件卷与 nginx 静态托管）、新增上行主题 `device/{key}/ota`、`IngestDispatcher` 第 6 路 fan-out、命令通道复用与保留服务白名单（并修正头部版本与 §changelog 一致性）——已升 v1.16（§4.1 控制器 22→24、§4.2 两控制器、§4.3 组件、§4.4 模块归属、§5/§6/§8、changelog）
- [x] `docs/DEPLOYMENT.md`：`app.ota.*` 配置说明（`OTA_*` 环境变量）、`V11` 迁移说明、固件共享卷 `ota_firmware` 挂载、nginx `location ^~ /firmware/` 与 `client_max_body_size` 说明、OTA 排查手册（固件 404 / 设备未收到地址 / 进度不更新）——已新增 §9.17 OTA 固件升级运维章节

## P8 门禁

- [x] 后端 `mvn -B verify` 全绿（含 ArchUnit 分层门禁）—— 记录 `Tests run` 总数与新增数
  - 结果：`Tests run` 合计 **697**、`Failures: 0`、`Errors: 0`、`Skipped: 0`（73 个测试类）；产出 `mqtt-cloud-backend-1.0.0.jar`（59.0 MB）；含 OTA 新增单测（Firmware/Upgrade/Progress 三个 ServiceImplTest、两 Controller、DispatchSweeper、Mapper SQL）及既有单测扩展（IngestDispatcher 第 6 路、DeviceCommandServiceImpl 保留服务白名单）
- [x] 前端 `npm run lint` 0 error / `npm run test` / `npm run build` 全绿 —— 记录 `Test Files` / `Tests` / `built in`
  - 结果：`lint` 0 error；`Test Files 51 passed (51)` / `Tests 360 passed (360)`（含 `src/api/__tests__/ota.spec.js` 13 例）；`✓ built in 29.84s`（产物含 `UpgradeTasks-*.js`）
- [x] `V1`~`V10` 迁移脚本**零改动**（仅新增 `V11__ota_firmware.sql`）；`git status --porcelain` 中 `db/migration` 只出现 `V11` 一个 `??` 条目
  - 结果：`db/migration` 目录仅 `V11__ota_firmware.sql` 一条 `??`；`V1`~`V10` 无任何 `M`/`D` 条目
- [x] ACL 零改动复核：`AclEvaluator.java` 无 diff；`device/{key}/ota` 上行在 `allowDevice` PUBLISH 判定下天然放行（补一条说明性断言或测试）
  - 结果：`AclEvaluator.java` 无 diff（`git diff --stat` 空）；`device/{key}/ota` 命中设备自有命名空间 `device/{key}/...` PUBLISH 前缀规则，天然放行，无需改 ACL

---

## 完成口径

P1~P8 全部勾选后，T-22 视为交付；任何未完成项（尤其需真实设备配合的端到端实测）需在 `总督促文档.md` 显式记录原因与后续安排。
提交按主题拆分（迁移与实体；DTO / 常量与错误码；Mapper 与聚合 SQL；服务层与配置（含摄取第 6 路、命令白名单、目标维度扩展、巡检）；接口层与安全配置；部署与静态托管（compose / nginx / env）；前端；文档），不合并提交。

---

## 交付记录

**状态**：P1~P8 全部勾选，T-22 视为交付（端到端实测已补测通过，无遗留例外项）。

- **日期**：2026-10-03 ~ 2026-10-04
- **后端门禁**：`mvn -B verify` 通过 —— `Tests run` 697 / `Failures: 0` / `Errors: 0` / `Skipped: 0`（73 类），产出 `mqtt-cloud-backend-1.0.0.jar`（59.0 MB）；含 ArchUnit 分层门禁
- **前端门禁**：`npm run lint` 0 error；`npm run test` `Test Files 51 passed (51)` / `Tests 360 passed (360)`；`npm run build` `✓ built in 29.84s`
- **迁移复核**：`V1`~`V10` 零改动（`db/migration` 仅 `V11__ota_firmware.sql` 一条 `??`）；`AclEvaluator.java` 无 diff，`device/{key}/ota` 上行天然放行
- **端到端实测**：**已执行并通过** —— 起 `docker/docker-compose.yml` 编排栈（backend / frontend / emqx / mysql / redis 全 `healthy`），A1~A5 逐条实测 **PASS=34 / FAIL=0**（真实 HTTP 请求 + `mosquitto_pub` 经 `docker_mqtt-network` 接 EMQX 模拟设备上行）
- **例外项**：无 —— 先前「A1~A5 端到端实测顺延」已补测通过，`总督促文档.md` §3.15 同步更新
- **提交口径**：按主题拆分（迁移与实体；DTO / 常量与错误码；Mapper 与聚合 SQL；服务层与配置；接口层与安全配置；部署与静态托管；前端；文档），不合并提交
