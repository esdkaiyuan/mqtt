# T-38 快速上位机（Web 组态） 实施计划

> 版本：v1.1　创建日期：2026-10-21　最后更新：2026-10-21　状态：**已交付**
> 目标：对标 ThingsCloud「应用工作台」/ 阿里云 IoT「可视化应用」，按物模型**一键生成**可交互的 Web 上位机，并保存复用。

---

## 1. 为什么做

现有「数据看板」只支持历史趋势图（`DashboardConfig.Panel` 绑定「设备组 × 属性 × 桶」），无法承载上位机最核心的三件事：

1. **实时看值**：属性最新值随 SSE 推送刷新，而不是等历史查询；
2. **实时控值**：开关、滑块、枚举、文本直接写设备影子期望值；
3. **一键生成**：用户不想手工拖控件，希望按物模型自动生成一套可用界面。

本任务新增独立的 **HMI 域**，不动看板模型，避免把「历史分析」与「实时控制」两种语义揉进同一份配置。

---

## 2. 设计决策（后续勿推翻）

1. **HMI 与 Dashboard 分离**
   - 看板是「历史趋势分析」，HMI 是「实时监控 + 控制」；
   - 两者共用 `PropertyTrendChart`、属性历史接口与 SSE，但配置模型、校验规则、页面入口独立。
   - 不扩展 `DashboardConfig`：它的面板必填 `deviceIds / identifier / bucket / chartType / aggregation`，与实时控制无交集，硬塞会污染既有 12 项校验测试。
2. **一个 HMI = 一台设备**
   - 上位机是「对一台设备的操作面板」，不做多设备拼盘；需要多设备时建多个 HMI 或用看板。
   - 生成器与校验都可直接以单设备物模型为准，避免跨产品类型不兼容。
3. **控件类型白名单固定**
   - `value`（只读值）、`switch`（bool rw）、`slider`（数值 rw）、`input`（数值 / 文本 rw）、`select`（enum rw）、`button`（服务调用）、`chart`（数值属性趋势）、`events`（最近事件）。
   - 每个控件只绑定一个 `identifier`；校验时按物模型核对类型与可写性，**不允许「绑定了不存在属性」的配置落库**。
4. **布局用 12 列栅格 + 顺序流**
   - 每个控件带 `span`（1~12），按数组顺序自动排布；不做绝对定位 / 自由拖拽。
   - 理由：自动生成需要确定性布局；自由拖拽的碰撞检测与移动端适配成本远高于收益，后续可单独立项。
5. **写值统一走设备影子期望值**
   - `switch/slider/input/select` 调用 `PUT /devices/{deviceKey}/shadow/desired`，等价于 `property_set` 命令；
   - 离线设备自动 `QUEUED` 并在上线后补发，与设备详情页控制面板语义一致；
   - 前端做乐观更新，失败回滚并提示。
6. **实时值来自全局 SSE**
   - 复用 `stores/ui.js` 的单一 SSE 连接，按 `event.deviceKey` 过滤；
   - 不额外为 HMI 建 SSE 连接，避免多页面叠加连接数。
7. **生成器只生成「能用的」控件**
   - 服务有入参也生成按钮，点击时按 `command-capability` 动态渲染参数表单；
   - 数值只读属性生成值卡，首个数值属性额外生成趋势图；
   - 无物模型时只生成「最近事件」控件，并在页面提示先建模。

---

## 3. 数据模型

### 3.1 表 `hmi_view`（V21）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 主键 |
| `user_id` | BIGINT | 创建者，`ON DELETE CASCADE` |
| `device_id` | BIGINT | 绑定设备，`ON DELETE CASCADE` |
| `name` | VARCHAR(64) | HMI 名称 |
| `config` | JSON | 控件配置 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

索引：`idx_user_updated(user_id, updated_at)`、`idx_device(device_id)`。硬删除，无逻辑删除列。

### 3.2 `HmiConfig`

```json
{
  "title": "温湿度传感器上位机",
  "widgets": [
    { "id": "w1", "type": "value",  "title": "温度", "identifier": "temperature", "span": 3, "unit": "°C" },
    { "id": "w2", "type": "switch", "title": "电源", "identifier": "power",       "span": 3 },
    { "id": "w3", "type": "slider", "title": "目标温度", "identifier": "target", "span": 4,
      "min": 16, "max": 30, "step": 1, "unit": "°C" },
    { "id": "w4", "type": "button", "title": "重启", "identifier": "reboot", "span": 3 },
    { "id": "w5", "type": "chart",  "title": "温度趋势", "identifier": "temperature", "span": 12 },
    { "id": "w6", "type": "events", "title": "最近事件", "span": 12 }
  ]
}
```

校验：

- 控件数 ≤ `app.hmi.max-widgets`（默认 30），HMI 数 ≤ `app.hmi.max-count`（默认 20）；
- `type` 必须命中白名单；`span` 1~12；`id` 缺省自动生成并去重；
- `value` / `chart` 绑属性（`chart` 仅数值）；`switch` 绑 bool 且 `rw`；
  `slider` / `input` 绑数值 / 文本且 `rw`；`select` 绑 enum 且 `rw`；`button` 绑服务；`events` 不绑定；
- `title` 缺省用属性 / 服务标识符；`min/max/step` 从物模型回填。

---

## 4. 交付内容

### 4.1 后端端点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/hmi-views` | 本人 HMI 列表 |
| POST | `/hmi-views` | 创建（名称 + deviceId + config） |
| GET | `/hmi-views/{id}` | 详情（含 deviceKey / deviceName / 解析后的 config） |
| PUT | `/hmi-views/{id}` | 更新 |
| DELETE | `/hmi-views/{id}` | 删除 |
| GET | `/hmi-views/draft?deviceId=` | 按物模型生成草稿，不落库 |

- 归属：`user_id` 服务端注入；非本人 / 不存在统一 6300，避免探测；
- 设备归属：创建 / 草稿时校验设备属于当前用户（ADMIN 可跨账号）；
- 配置非法 6302 / 绑定非法 6303 / 控件类型非法 6304 / 数量超限 6301。

### 4.2 前端

- `/workbench/hmi`：HMI 卡片列表（名称、设备、控件数、更新时间），新建入口；
- `/workbench/hmi/new`：选择设备 → 生成预览 → 命名 → 保存；
- `/workbench/hmi/:id`：
  - 实时运行：值卡 / 开关 / 滑块 / 输入 / 下拉 / 按钮 / 趋势 / 事件；
  - SSE 实时刷新，写值乐观更新；
  - 「编辑」抽屉：改名称、标题、单位、跨度、删除控件、从物模型添加控件；
- 设备详情新增「上位机」入口，带 `deviceId` 预选；
- 导航「开发接入」组新增「上位机」。

---

## 5. 测试与验收

- 后端：
  - 生成器映射（bool/enum/数值/文本/服务/事件）逐类型断言；
  - 校验拒绝非法类型、非法绑定、非 rw 控件、超限；
  - 归属隔离：非本人 HMI 6300、非归属设备 2003；
  - 迁移 V21 与 `HmiViewMapper` SQL 守卫（如既有项目有 SQL 测试约定）。
- 前端：
  - 生成向导提交与保存；
  - 控件写值调用影子接口、失败回滚；
  - SSE 事件按 deviceKey 过滤并刷新值；
  - 编辑抽屉保存载荷。
- 门禁：后端 `mvn -B verify` / 前端 `eslint` + `vitest` + `vite build` 全绿。

---

## 6. 交付结果（2026-10-21）

- 迁移 `V21__hmi_view.sql`（只加表）；
- 后端新增 `HmiView` / `HmiViewMapper` / `HmiProperties` / `HmiViewService` / `HmiViewServiceImpl` / `HmiViewController` 与 5 个 DTO；
- 新增单测：`HmiViewServiceImplTest`（12 项）、`HmiViewControllerTest`（5 项）、`ResultCodeEnumTest` 补 5 个错误码断言；
- 后端全量 `mvn -B verify`：**1329 项 0 失败**（1294 基线 + 35 新增，含 ArchUnit 5 项）；
- 前端新增 `HmiList.vue` / `HmiCreate.vue` / `HmiDetail.vue`、`HmiGrid` / `HmiWidget` / `HmiServiceDialog` / `HmiEditorDrawer`、`api/hmi.js` 与 API / 控件单测 9 项；
- 文档回填：路线图 L23、总督促 §3.29、对标清单域 16、ARCHITECTURE §4.2/§5/§6/§8、DEPLOYMENT §9.24。

---

## 7. 明确不做

- 自由拖拽 / 绝对定位 / 多页面组态；
- 多设备拼盘与跨设备联动控制；
- 自定义脚本 / 公式计算控件；
- HMI 分享与外链访问（仍限登录用户本人）；
- 桌面端工程代码导出（本轮仅 Web 组态）。
