# T-40 Modbus 字序与 32 位整型支持 实施计划

> 版本：v1.1　创建日期：2026-10-21　状态：**已交付**
> 目标：补齐平台侧 Modbus 在真实现场最常见的两项兼容缺口——**字序（word order）**与 **32 位整型**。

---

## 1. 为什么做

T-31 P4 的 Modbus 采集默认按大端解析 float/double、int 只有 16 位。真实 PLC 常见两类问题：

1. 32 位浮点采用 word-swap（CDAB）或字节交换（BADC），默认大端会读出完全错误的值；
2. 累计量 / 计数器常为 32 位整数，只有 16 位 `int` 无法表达。

这两项不解决，P4 在现场只能采到“看起来有值但不对”的数据。

---

## 2. 设计决策（后续勿推翻）

1. **字序是产品级 `driver_config` 配置，不是点表字段**
   - 同一台设备的寄存器字序通常一致；放在 `driver_config.wordOrder` 避免每个点位重复配置。
   - 支持 `ABCD`（默认，大端）、`CDAB`（字交换）、`BADC`（字节交换）、`DCBA`（小端）。
2. **只影响 32 / 64 位数值**
   - `int32` / `uint32` / `float`（2 寄存器）与 `double`（4 寄存器）；
   - 16 位 `int`、`bool`、`string` 不参与重排，避免把单寄存器值改坏。
3. **字节级重排，而不是只交换寄存器数组**
   - 先把寄存器按大端展开成字节，再按字序重排；
   - `CDAB` = 每个 32 位字内交换两个 16 位字；`BADC` = 每个 16 位字内交换字节；
     `DCBA` = 完整反转（小端）。64 位 `double` 的 `CDAB`/`BADC` 按每 4 字节块处理，`DCBA` 全反转。
4. **`int32` 有符号、`uint32` 无符号**
   - `int32` 输出 `-2147483648~2147483647`；`uint32` 输出 `0~4294967295`；
   - 工程值仍按 `原始值 × scale + offset` 换算。
5. **零迁移**
   - `dataType` 是字符串列，V18 无枚举约束；只扩服务层白名单与前端选项；
   - `wordOrder` 存在 `driver_config` JSON 中，不需要新列或新表。
6. **非法字序显式拒绝**
   - `ModbusConnectionConfig` 校验四个白名单值，非法值让整轮采集以可读错误失败，不静默回落 ABCD —— 静默回落会让现场以为已配置成功。

---

## 3. 变更文件

| 文件 | 作用 |
|---|---|
| `collect/modbus/ModbusConnectionConfig.java` | 解析 / 校验 `wordOrder`，默认 `ABCD` |
| `collect/modbus/ModbusValueConverter.java` | `int32` / `uint32`、字节级字序重排 |
| `collect/modbus/ModbusPointCollector.java` | 把 `config.wordOrder()` 传入换算 |
| `service/impl/DevicePointTableServiceImpl.java` | dataType 白名单扩 `int32/uint32` |
| `frontend/src/api/devicePointTable.js` | 点表数据类型选项扩两项 |
| `docs/D2_设备接入协议规范.md` | 字序、int32/uint32、寄存器宽度 |
| `docs/DEPLOYMENT.md` | `driver_config.wordOrder` 与现场排查 |

---

## 4. 测试

- `ModbusValueConverterTest`：int32 / uint32 / float CDAB / int32 CDAB / uint32 DCBA / double DCBA；
- `ModbusConnectionConfigTest`：默认 ABCD、`cdab` 归一化、非法 `wordOrder` 拒绝；
- `devicePointTable.spec.js`：数据类型选项与后端白名单同步。

---

## 5. 验收标准

1. `driver_config.wordOrder` 配 `CDAB` 时，float 能读出正确值；
2. `int32` / `uint32` 能按 2 个寄存器解析，`uint32` 不出现负数；
3. 非法 `wordOrder` 在采集轮次中给出明确失败，不静默按 ABCD；
4. 既有 int/float/double/bool/string 行为零回归；
5. 后端 `mvn verify`、前端 `eslint/test/build` 全绿。

---

## 6. 交付结果（2026-10-21）

- `ModbusConnectionConfig` 新增 `wordOrder`（默认 ABCD），非法值显式拒绝；
- `ModbusValueConverter` 支持 int32/uint32 与四种字序的字节级重排；
- `DevicePointTableServiceImpl` 与前端点表选项同步扩 `int32/uint32`；
- 新增/调整单测：转换器 +6、配置 +1、前端选项同步；
- 后端 `mvn -B verify` **1409 项 0 失败**（1329 基线 + 80 新增；含 ArchUnit 5 项）；
- 前端 `eslint` 0 error、`vitest` **600 项全绿（76 files）**、`vite build` 通过；
- 真实现场 word-swap / 32 位累计量未实测。

---

## 7. 明确不做

- 64 位整型（`int64/uint64`）；
- 每点位独立字序（设备级足够；现场混用需拆设备）；
- 寄存器地址偏移自动换算（40001 → 0 仍由用户填写零基地址）；
- Modbus RTU / 多从站（仍为后续批次）。
