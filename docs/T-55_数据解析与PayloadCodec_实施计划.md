# T-55 · 数据解析与 Payload Codec（非 Alink 报文映射）

> 版本：v1.0　创建日期：2026-10-26　状态：**已交付**
> 定位：补齐 `头部平台对标_尚未补齐功能项清单.md` G-2-2（非 Alink 载荷无派生）与 G-3-1（无自定义数据映射），让现场非 Alink JSON 报文也能进入物模型解析、属性最新值、告警、规则、场景与属性历史。
> 关联：[T-14 物模型与数据解析](T-14_物模型与数据解析_实施计划.md)、[T-53 平台治理与规模化补强](T-53_平台治理与规模化补强_实施计划.md)、[D2 设备接入协议规范](D2_设备接入协议规范.md)。

---

## 1. 背景与目标

T-14 之后，平台的上行解析只接受 Alink 信封：

```json
{"method":"thing.event.property.post","params":{"temperature":26.5}}
```

现场大量设备/第三方系统直接上报平铺或厂商私有 JSON（如 `{"sensors":{"temp":26.5}}`）。
此前这类报文只会原样落 `message`，不产生属性最新值、事件、告警、规则、场景与历史数据。

本任务目标：

1. 产品级配置**声明式 Payload Codec**，把非 Alink JSON 映射为 Alink；
2. 映射发生在物模型解析之前，下游链路零改动；
3. 不引入脚本引擎，避免设备上报成为任意代码执行入口；
4. 保存期严格校验，运行期缓存模板，失败可回退原始载荷。

---

## 2. 设计决策

### 2.1 声明式模板，不执行脚本

Codec 是一段 JSON，不是 JS/Python 脚本：

```json
{
  "version": 1,
  "fallbackToRaw": true,
  "template": {
    "method": "thing.event.property.post",
    "params": {
      "temperature": "$.sensors.temp",
      "humidity": "$.sensors.hum"
    }
  }
}
```

- 模板中的字符串以 `$` 开头视为**只读路径**：`$`、`$.a.b`、`$[0]`、`$['a.b']`、`$.a[0].b`；
- 其它字符串/数字/布尔/null 为常量；
- 对象 / 数组递归渲染；
- 路径不存在时字段省略，不注入 `null`；
- **不支持**函数、表达式运算、通配符、脚本。

选型理由：脚本沙箱的依赖、资源限制与安全评审成本远高于当前收益；声明式映射已覆盖「字段换名 / 嵌套取值 / 数组下标 / 常量补全 / 事件体组装」等高频场景。复杂变换留待后续引入受限脚本引擎时另立任务。

### 2.2 映射位置

在 `ThingModelInterpretServiceImpl.interpretOne` 中：

```
解析原始 JSON → Payload Codec 映射 → method 分派 → 属性 / 事件解析 → 五条旁路
```

- Codec 未配置：完全走原路径；
- Codec 配置且映射成功：`thing_model_interpret_total{result="codec"}` +1，后续行为与 Alink 完全一致；
- 映射失败且 `fallbackToRaw=true`：回退原始载荷（兼容设备同时支持两种格式）；
- 映射失败且 `fallbackToRaw=false`：按 `unparsed` 丢弃。

### 2.3 缓存与失效

- `PayloadCodecCache`：进程内缓存，TTL 复用 `app.thing-model.cache-ttl-seconds`（默认 60s，0 关闭）；
- 产品更新 / 删除后 `AfterCommit` 主动失效本副本；多副本收敛窗口 = TTL；
- 缓存「未配置」的空定义，避免每条上行都回源查库。

### 2.4 保存期校验

`PayloadCodecValidator` 校验：

- 必须是 JSON 对象；字节数 ≤ 16KB；
- `version` 缺省或 1；`mode` 缺省或 `TEMPLATE`；`fallbackToRaw` 必须为布尔；
- `template` 必须为对象，且 `method` 非空；
- 所有 `$` 字符串必须是合法路径；模板深度 ≤10、节点数 ≤500；
- 非法返回 `6317`（`PAYLOAD_CODEC_INVALID`）。

规范化后只保留 `version` / `mode` / `fallbackToRaw` / `template`，未知字段丢弃。

---

## 3. 数据模型

`V33__product_payload_codec.sql`：

```sql
ALTER TABLE product
    ADD COLUMN payload_codec TEXT NULL
        COMMENT 'Payload Codec JSON（T-55）；NULL=按原始 Alink 解析' AFTER payload_format;
```

- 只加列，不改既有列；
- 存量产品 `payload_codec` 为 NULL，行为与 T-14 完全一致。

---

## 4. 后端变更

| 组件 | 变更 |
|---|---|
| `Product` / `ProductRequest` | 新增 `payloadCodec` |
| `ProductServiceImpl` | 创建 / 更新时校验并规范化；更新 / 删除后失效缓存 |
| `PayloadCodecValidator` | 新增：保存期校验 + 规范化 + 运行时解析 |
| `PayloadCodecTransformer` | 新增：模板渲染 + 路径解析 |
| `PayloadCodecDefinition` | 新增：运行时定义（模板 + fallback） |
| `PayloadCodecService` / `Impl` | 新增：产品级读取、缓存、失效 |
| `PayloadCodecCache` | 新增：TTL 进程内缓存 |
| `ThingModelInterpretServiceImpl` | 解析前应用 Codec；新增 `codec` 指标 |
| `ResultCode` | 新增 `6317` |

---

## 5. 前端变更

- 产品表单新增「Payload Codec（JSON）」编辑区，带示例占位与保存前 JSON / template 校验；
- 产品列表新增「数据解析」列：`Codec` / `Alink` 标签；
- 文档站「设备与物模型」增加 Payload Codec 说明；D2 协议页增加映射示例与限制。

---

## 6. API / 错误码

- `POST /products`、`PUT /products/{id}` 请求体新增可选 `payloadCodec`；
- 更新时 `null` 保持原值，空串清空；
- 非法配置返回 `400 + 6317`；
- 开放 API 的产品投影未包含 codec（避免第三方读取内部映射）；管理端产品接口返回。

---

## 7. 配置

**无新增环境变量**：

- 缓存 TTL 复用 `THING_MODEL_CACHE_TTL_SECONDS`；
- 解析总开关复用 `THING_MODEL_INTERPRET_ENABLED`（关闭时 Codec 同样不执行）；
- Codec 大小上限固定 16KB（`PayloadCodecValidator.MAX_SIZE_BYTES`）。

---

## 8. 测试

- `PayloadCodecValidatorTest`：默认值 / 未知字段、空值、非法 JSON、版本 / mode、缺 template / method、非法路径、深度超限、大小超限；
- `PayloadCodecTransformerTest`：路径 / 常量 / 嵌套 / 数组下标 / 带点键名 / 缺失字段、禁用 / 原始 null、fallback 与 strict、路径语法；
- `ThingModelInterpretServiceImplTest`：非 Alink 报文经 Codec 映射后命中属性，`codec` 与 `property` 指标各 +1。

门禁结果（2026-10-26）：

- T-55 定向测试 **59 项全绿**；
- 后端全量 `mvn -B verify`：**1626 项测试 0 失败 0 错误，BUILD SUCCESS**（含 ArchUnit）；
- 前端 `eslint` **0 error / 0 warning**、`vitest` **683 项 / 87 个文件全绿**、`vite build` 通过；
- 待现场实测：真实非 Alink 报文映射、多副本缓存收敛。

---

## 9. 未做 / 后续

| 项 | 说明 |
|---|---|
| 脚本化解析 | 需要脚本引擎与资源沙箱，另立任务 |
| 通配符 / 数组投影 | 当前仅支持单值路径与下标，不支持 `[*]` 聚合 |
| 事件 / 服务专用模板 | 当前统一 `template.method`，可映射属性或事件，但无独立多模板 |
| 设备级 Codec | 当前为产品级；单设备差异化需后续评估 |
| 数据桥接（Kafka / DB） | 规则 / 场景转发仍为 MQTT / HTTP，非本任务范围 |
| 现场实测 | 真实设备非 Alink 报文映射、多副本缓存收敛待现场验收 |

---

## 10. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：产品级 Payload Codec、校验 / 渲染 / 缓存 / 解析集成与前端编辑完成。 |