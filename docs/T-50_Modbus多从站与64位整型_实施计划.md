# T-50 Modbus 多从站与 64 位整型 实施计划

> 版本：v1.0　创建日期：2026-10-22　状态：已交付
> 目标：补齐 G-13-2 的多从站残留与 T-40 的 `int64` / `uint64` 残留，让一个 Modbus TCP 网关可同时轮询多个 unitId，并把 64 位整数按既有字序规则还原。

---

## 1. 背景与范围

T-31 P4 的平台侧 Modbus TCP 主站目前只支持「设备级单从站」：`driver_config.unitId` 是整台设备唯一的从站地址，`ModbusPointDispatch` 只按功能码与地址区间分组。现场常见的「一个 TCP 网关后面挂多个 RTU 从站」无法配置；同时 T-40 补了 32 位整型，`int64` / `uint64` 仍缺失。

本任务：

- 点表新增可选 `unit_id`（0~247）：为空继承 `driver_config.unitId`；
- 同一设备的多条点位可按不同 unitId 分组，采集器在同一 TCP 连接上分别读取；
- 批量读合并键增加 unitId，**不同从站即使地址相同也绝不合并**；
- 数据类型新增 `int64` / `uint64`（4 个寄存器），沿用 `wordOrder` 字序与 scale/offset 换算；
- 前端点表页支持从站地址与 64 位类型，导入解析兼容 `unitId` / `slaveId` / `stationId` 别名；
- D2 协议规范与 DEPLOYMENT 同步点表载荷 `unitId`、64 位宽度与字序说明。

不在本任务范围：Modbus RTU 串口、OPC-UA、写寄存器、多 TCP 端点（一台设备仍只连一个 host:port）、point 级超时覆盖。

---

## 2. 数据模型

### 2.1 迁移 `V28__point_unit_and_int64.sql`

- `device_point_table` 新增 `unit_id INT NULL`：`NULL` = 继承设备级 `driver_config.unitId`；
- 同步 `data_type` 列注释，加入 `int64` / `uint64`；
- 只加列、不改既有列，存量点位行为不变。

### 2.2 数据类型

| `data_type` | 寄存器宽度 | 说明 |
|---|---|---|
| `int64` | 4 | 有符号 64 位 |
| `uint64` | 4 | 无符号 64 位，按 `Long.toUnsignedString` 输出 |

`wordOrder` 规则不变：ABCD / CDAB / BADC / DCBA 作用于所有 32 / 64 位数值。

---

## 3. 后端实现

### 3.1 点表保存

- `PointTableUpsertRequest` / `DevicePointTable` / `PointTableDTO` 增加 `unitId`；
- `@Min(0)` / `@Max(247)` + 服务层二次校验；非法返回 `6311`；
- `DevicePointTableServiceImpl.normalize` 写入 `unitId`，`toDTO` 回传；
- 数据类型白名单扩为 `int|int32|uint32|int64|uint64|float|double|bool|string`；
- 路线 A 的点表下发载荷（`PointTablePayloadBuilder`）同步携带可选 `unitId`，边缘网关可据此区分从站。

### 3.2 采集分组

- `ModbusPointDispatch.plan(points, defaultUnitId)`：候选点位先解析有效 unitId（点位值优先，空则默认），再按 `(pointType, unitId)` 分组；
- `ReadGroup` 增加 `unitId`；不同 unitId 的点位不共享合并读；
- 合并读失败降级逐点时，仍使用该分组的 unitId；
- 采集器在过滤阶段拒绝 `unitId` 越界点位（标记失败，不拖垮同批其它点）；
- `driver_config` 仍承载 host / port / 默认 unitId / 超时 / 字序。

### 3.3 64 位整型

- `ModbusValueConverter.registerWidth`：`int64` / `uint64` → 4；
- `rawValue`：`int64` 用 `toInt64`；`uint64` 用 `Long.toUnsignedString(toInt64(...))`；
- 工程值换算继续走 `BigDecimal(raw) * scale + offset`，无符号 64 位大数不会溢出。

---

## 4. 前端

- `api/devicePointTable.js`：数据类型选项新增「整数（64 位有符号 / 无符号）」；创建点位增加 `unitId: null`；导入兼容 `unitId` / `unit_id` / `slaveId` / `stationId`；
- `DevicePointTable.vue`：地址列后新增「从站」列（可选，留空继承驱动配置，范围 0~247）；
- 保存前校验 unitId 范围；导出 JSON 原样包含 `unitId`。

---

## 5. 测试

后端：

- `ModbusValueConverterTest`：int64 / uint64 的宽度、边界值、字序；
- `ModbusPointDispatchTest`：不同 unitId 不合并、相同 unitId 合并、默认 unitId 回落；
- `ModbusPointCollectorTest`：多 unitId 分别调用主站读；越界 unitId 点位失败；
- `ModbusLoopbackIntegrationTest`：真实 TCP 从站注册两个 process image，验证同一连接读取两个 unitId；
- `DevicePointTableServiceImplTest`：unitId 保存 / 回传 / 越界拒绝。

前端：

- `devicePointTable.spec.js`：数据类型选项、创建默认值、导入 unitId 别名。

门禁：

- 后端 `sh scripts/mvnw.sh -B verify`：**1536 项 0 失败**（1526 基线 + 10 新增；含 ArchUnit 5 项）；
- 前端 `eslint src` 0 error、`vitest` **664 项全绿（82 files）**、`vite build` 通过。

---

## 6. 已实测与未实测

已实测：

- `int64` / `uint64` 的寄存器宽度、边界值、`DCBA` 字序；
- 不同 unitId 的分组隔离、空 unitId 继承默认值；
- 采集器对多 unitId 分别发起主站读、越界 unitId 点位失败且不建立连接；
- **真实 TCP 回环**：j2mod 从站注册两个 process image，同一连接读取 unit 1 与 unit 2；
- 点表服务 unitId 落库 / 回传与 0~247 范围校验；
- 路线 A 点表下发载荷的 `unitId` 映射与空值省略；
- 前端从站地址别名解析、64 位类型选项。

未实测：

- 真实现场 Modbus TCP 网关后挂多个 RTU 从站的时延、串扰与并发；
- 真实 PLC 的 int64/uint64 字序差异（仅按既有 ABCD/CDAB/BADC/DCBA 口径测试）；
- 浏览器中点表页从站列与 64 位类型下拉的实际交互；
- 真实 MySQL 上 `V28` 迁移回放；
- 多从站大批量点位下的合并读效率与故障降级表现。

---

## 7. 关键决策与已知限制

1. **点位 unitId 优先于驱动默认值**：一个网关下不同从站可以共用同一连接，只有需要覆盖时才在点位行填写。
2. **合并键必须包含 unitId**：不同从站相同寄存器地址绝不可合并，否则会把 A 从站的值写到 B 从站点位上。
3. **不引入写寄存器**：本批只读；写入涉及功能码 5/6/15/16 与权限审计，另立任务。
4. **RTU 与 OPC-UA 不做**：串口需要额外原生依赖与现场设备，OPC-UA 是独立协议栈；本批保持 TCP-only。
5. **uint64 文本化**：Java `long` 无符号输出为十进制字符串，参与 BigDecimal 换算，不截断为负数。
