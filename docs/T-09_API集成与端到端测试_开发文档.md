# T-09：API集成与端到端测试 - 开发文档
> 版本：v2.1（接口清单对齐实际代码）  修订日期：2026-08-06

---

## 1. 子任务概述

**目标：** 完成前后端API联调、端到端功能测试、Swagger文档完善、Bug修复与优化。确保所有接口正确对接、前端页面功能完整、关键用户流程顺畅。本文档覆盖后端实际实现的全部38个接口（9个控制器），包括统计分析、API密钥、Webhook、外部API四个扩展模块。

**依赖：** T-03, T-04, T-05（后端接口已完成）、T-06, T-07, T-08（前端页面已完成）

**产出物：**
- API联调完成确认
- 端到端测试报告（含测试结果）
- Bug修复记录
- Swagger文档完善
- 本开发文档

**下游影响：** T-10（部署：基于本子任务的集成测试结果）

---

## 2. 开发内容

### 2.1 API接口清单（共38个接口，9个控制器）

**认证方式说明：**

| 认证方式 | 请求头 | 说明 |
|----------|--------|------|
| 公开 | 无 | SecurityConfig中permitAll，无需认证 |
| JWT | `Authorization: Bearer {token}` | 登录后获取，除公开路径外所有接口需要 |
| API Key | `X-API-Key: {key}` | 外部API专用，密钥通过/api/api-keys创建 |

**路径前缀说明：** 所有路径均带 context-path 前缀 `/api`（如 `/api/auth/login`）。

#### 2.1.1 认证管理 AuthController（5个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 1 | 用户注册 | POST | /api/auth/register | 公开 | {username, password, email?, phone?} | UserResponseDTO |
| 2 | 用户登录 | POST | /api/auth/login | 公开 | {username, password} | User(含token) |
| 3 | 用户登出 | POST | /api/auth/logout | JWT | - | void |
| 4 | 获取当前用户 | GET | /api/auth/current | JWT | - | User |
| 5 | 修改密码 | POST | /api/auth/change-password | JWT | {oldPassword, newPassword} | void |

#### 2.1.2 设备管理 DeviceController（7个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 6 | 创建设备 | POST | /api/devices | JWT | CreateDeviceDTO | Device |
| 7 | 获取设备列表 | GET | /api/devices | JWT | pageNum, pageSize, deviceName?, deviceType?, status? | IPage\<Device\> |
| 8 | 获取设备详情 | GET | /api/devices/{deviceId} | JWT | - | Device |
| 9 | 更新设备 | PUT | /api/devices/{deviceId} | JWT | UpdateDeviceDTO | Device |
| 10 | 删除设备 | DELETE | /api/devices/{deviceId} | JWT | - | void |
| 11 | 获取在线设备 | GET | /api/devices/online | JWT | - | List\<Device\> |
| 12 | 获取设备状态历史 | GET | /api/devices/{deviceId}/status | JWT | - | List\<DeviceStatus\> |

#### 2.1.3 消息管理 MessageController（4个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 13 | 发布消息 | POST | /api/messages/publish | JWT | {topic, payload, qos, deviceId?} | Message |
| 14 | 查询消息列表 | GET | /api/messages | JWT | pageNum, pageSize, topic?, deviceId?, direction?, startTime?, endTime? | IPage\<Message\> |
| 15 | 获取最近消息 | GET | /api/messages/recent | JWT | limit（默认50，最大100） | List\<Message\> |
| 16 | 消息趋势统计 | GET | /api/messages/trend | JWT | days（默认7） | List\<Map\> |

#### 2.1.4 历史查询 HistoryController（2个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 17 | 查询历史记录 | GET | /api/history | JWT | pageNum, pageSize, deviceId?, topic?, startTime?, endTime? | IPage\<HistoryRecord\> |
| 18 | 查询设备历史 | GET | /api/history/device/{deviceId} | JWT | pageNum, pageSize, startTime?, endTime? | IPage\<HistoryRecord\> |

#### 2.1.5 健康检查 HealthController（1个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 19 | 健康检查 | GET | /api/health | 公开 | - | Map（status/database/redis/timestamp） |

#### 2.1.6 统计分析 AnalyticsController（4个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 20 | 总览统计 | GET | /api/analytics/overview | JWT | - | Map（设备总数/在线数/消息总数等） |
| 21 | 设备状态分布 | GET | /api/analytics/devices/status | JWT | - | List\<Map\>（按ONLINE/OFFLINE/INACTIVE分组计数） |
| 22 | 设备类型分布 | GET | /api/analytics/devices/type | JWT | - | List\<Map\>（按deviceType分组计数） |
| 23 | 消息趋势统计 | GET | /api/analytics/messages/trend | JWT | days（默认7） | List\<Map\>（按天分组的消息量） |

#### 2.1.7 API密钥管理 ApiKeyController（4个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 24 | 获取密钥列表 | GET | /api/api-keys | JWT | - | List\<ApiKey\>（仅当前用户的密钥） |
| 25 | 创建密钥 | POST | /api/api-keys | JWT | {name, description?, permissions?, expiresAt?} | ApiKey（含完整密钥明文，**仅创建时返回一次**） |
| 26 | 获取密钥详情 | GET | /api/api-keys/{id} | JWT | - | ApiKey（仅所有者，越权返回403） |
| 27 | 吊销密钥 | DELETE /api/api-keys/{id} | JWT | - | void（仅所有者，越权返回403） |

#### 2.1.8 Webhook管理 WebhookController（6个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 28 | 获取Webhook列表 | GET | /api/webhooks | JWT | - | List\<WebhookConfig\>（仅当前用户） |
| 29 | 创建Webhook | POST | /api/webhooks | JWT | {name, url, events, secret?, deviceKey?, headers?, retryCount?, timeoutSeconds?} | WebhookConfig |
| 30 | 获取Webhook详情 | GET | /api/webhooks/{id} | JWT | - | WebhookConfig（仅所有者） |
| 31 | 更新Webhook | PUT | /api/webhooks/{id} | JWT | WebhookConfigRequest | WebhookConfig（仅所有者） |
| 32 | 删除Webhook | DELETE | /api/webhooks/{id} | JWT | - | void（仅所有者） |
| 33 | 测试Webhook | POST | /api/webhooks/{id}/test | JWT | - | String（仅所有者，提交测试任务） |

#### 2.1.9 外部API ExternalApiController（5个接口）

| 序号 | 接口 | 方法 | 路径 | 认证方式 | 请求体/参数 | 响应 |
|------|------|------|------|----------|-------------|------|
| 34 | 外部-查询设备列表 | GET | /api/external/v1/devices | API Key | deviceKey?（可选，精确查询单设备） | List\<Map\>（仅密钥所属用户的设备） |
| 35 | 外部-查询设备详情 | GET | /api/external/v1/devices/{deviceKey} | API Key | - | Map（设备完整信息） |
| 36 | 外部-发送设备指令 | POST | /api/external/v1/devices/{deviceKey}/command | API Key | {payload, qos?}（自动将topic的/data或/heartbeat替换为/command下发） | String |
| 37 | 外部-查询设备状态 | GET | /api/external/v1/devices/{deviceKey}/status | API Key | - | Map（status/lastSeen/online） |
| 38 | 外部-获取统计信息 | GET | /api/external/v1/stats | API Key | - | Map（totalDevices/onlineDevices/apiKeyName/permissions） |

**接口总数核对：** 5（认证）+ 7（设备）+ 4（消息）+ 2（历史）+ 1（健康）+ 4（统计）+ 4（密钥）+ 6（Webhook）+ 5（外部）= **38个接口**

### 2.2 Swagger/OpenAPI完善

每个Controller接口需添加完整的Swagger注解：

```java
@Operation(summary = "用户注册", description = "注册新用户，默认角色为VIEWER",
    requestBody = @RequestBody(description = "注册信息", required = true,
        content = @Content(schema = @Schema(implementation = RegisterDTO.class))))
@ApiResponse(responseCode = "200", description = "注册成功",
    content = @Content(schema = @Schema(implementation = UserResponseDTO.class)))
@ApiResponse(responseCode = "400", description = "参数错误（用户名已存在等）")
@PostMapping("/register")
public Result<UserResponseDTO> register(@Valid @RequestBody RegisterDTO dto) { ... }
```

访问地址：`http://localhost:8080/api/swagger-ui.html`

**分组配置要求（当前OpenApiConfig仅配置4个分组，需补充4个）：**

| 分组名 | 路径匹配 | 状态 |
|--------|----------|------|
| 认证管理 | /api/auth/** | 已配置 |
| 设备管理 | /api/devices/** | 已配置 |
| 消息管理 | /api/messages/** | 已配置 |
| 历史查询 | /api/history/** | 已配置 |
| 统计分析 | /api/analytics/** | **待补充** |
| API密钥 | /api/api-keys/** | **待补充** |
| Webhook | /api/webhooks/** | **待补充** |
| 外部API | /api/external/v1/** | **待补充** |

### 2.3 端到端测试用例

#### 测试场景1：完整用户流程

**测试步骤：**
1. 打开浏览器访问 http://localhost:3000/login
2. 输入错误凭证（用户名不存在） → 预期：显示"用户名或密码错误"
3. 输入错误密码 → 预期：显示"用户名或密码错误"
4. 输入正确凭证（admin/admin123） → 预期：跳转到/dashboard、顶部显示用户名"admin"
5. 导航到"设备管理" → 预期：显示设备列表页面、无设备时显示空状态
6. 点击"创建设备" → 预期：弹出创建对话框
7. 填写设备信息并提交 → 预期：设备出现在列表中、状态为"未激活"
8. 导航到"实时消息" → 预期：显示消息监控页面、MQTT连接状态"已连接"
9. 使用MQTT客户端发送消息：`mosquitto_pub -h localhost -t "device/sensor-001/data" -m '{"temp":26.5}'`
10. 预期：设备状态变为"在线"、消息流中出现新消息
11. 在消息监控页面填写发布表单并发布消息 → 预期：消息发送成功、消息流中出现
12. 导航到"历史查询" → 预期：显示历史查询页面
13. 选择设备和时间范围，点击查询 → 预期：显示历史数据表格
14. 导航回设备列表，点击"查看" → 预期：跳转到设备详情页、信息完整
15. 点击"编辑"修改设备名称 → 预期：修改成功、列表更新
16. 导航到"实时消息"，点击用户下拉菜单"退出登录" → 预期：跳转到/login
17. 尝试直接访问 http://localhost:3000/devices → 预期：重定向到/login

**预期结果：** 所有步骤均按预期执行，无异常。

#### 测试场景2：多用户数据隔离

**前置条件：** 创建两个测试用户（userA和userB）

**测试步骤：**
1. 以userA身份登录
2. 创建设备A（deviceKey: device-a）
3. 登出
4. 以userB身份登录
5. 创建设备B（deviceKey: device-b）
6. 访问设备列表 → 预期：只看到设备B，看不到设备A
7. 尝试通过API直接访问设备A：`curl http://localhost:8080/api/devices/{deviceA_id} -H "Authorization: Bearer {userB_token}"`
8. 预期：返回403 "无权限操作此设备"

**预期结果：** 数据隔离正确。

#### 测试场景3：设备生命周期

**测试步骤：**
1. 创建设备 → 预期：状态为"未激活"（INACTIVE）
2. 发送MQTT消息到设备Topic → 预期：状态变为"在线"（ONLINE）、lastSeen更新
3. 等待5分钟（或修改超时时间为1分钟） → 预期：状态变为"离线"（OFFLINE）
4. 再次发送MQTT消息 → 预期：状态恢复为"在线"（ONLINE）
5. 点击"删除"确认删除 → 预期：设备从列表消失

**预期结果：** 设备状态流转正确。

#### 测试场景4：API密钥与外部API联调

**前置条件：** 已登录（admin），已创建设备 test-dev-001

**测试步骤：**
1. 导航到「API密钥」页面（/settings/api-keys）→ 预期：显示密钥列表（初始为空）
2. 点击「创建密钥」，填写名称"test-key"、权限 `["device:read","device:write"]` → 预期：创建成功，**完整密钥明文仅显示一次**，立即复制保存
3. 使用 API Key 调用外部接口查询设备列表：
   ```bash
   curl http://localhost:8080/api/external/v1/devices -H "X-API-Key: {刚创建的密钥}"
   ```
   → 预期：返回200、包含当前用户的设备列表
4. 不带密钥调用 → 预期：返回401、"无效的API Key"
5. 使用 API Key 发送设备指令：
   ```bash
   curl -X POST http://localhost:8080/api/external/v1/devices/test-dev-001/command \
     -H "X-API-Key: {密钥}" -H "Content-Type: application/json" \
     -d '{"payload":"{\"action\":\"restart\"}","qos":1}'
   ```
   → 预期：返回200、"指令发送成功"；MQTT客户端订阅 `device/test-dev-001/command` 能收到消息
6. 使用 API Key 查询设备状态 → 预期：返回 status/lastSeen/online 字段
7. 使用 API Key 查询统计信息 → 预期：返回 totalDevices/onlineDevices/apiKeyName/permissions
8. 吊销密钥（点击「删除」）→ 预期：密钥从列表移除
9. 再次使用已吊销的密钥调用 → 预期：返回401、"无效的API Key"

**预期结果：** API Key 全生命周期（创建→使用→吊销）正确，外部API五种调用全部正常。

#### 测试场景5：Webhook配置联调

**前置条件：** 已登录；准备一个可接收POST请求的测试URL（如 http://localhost:5000/webhook-test 或 webhook.site）

**测试步骤：**
1. 导航到「Webhook」页面（/settings/webhooks）→ 预期：显示Webhook列表（初始为空）
2. 点击「创建Webhook」，填写名称、URL、事件类型 → 预期：创建成功、出现在列表
3. 点击「测试」按钮 → 预期：返回"Webhook测试任务已提交"
4. 查看详情 → 预期：显示完整配置（含secret、headers、重试次数、超时时间）
5. 修改Webhook（更新URL或事件）→ 预期：修改成功
6. 使用用户B的Token访问用户A的Webhook（API方式）→ 预期：返回403
7. 删除Webhook → 预期：从列表移除、再次查询返回404

**预期结果：** Webhook CRUD + 所有权隔离正确。

#### 测试场景6：仪表盘统计联调

**前置条件：** 已登录；已有若干设备和消息数据

**测试步骤：**
1. 导航到「仪表盘」（/dashboard）→ 预期：显示统计卡片和图表（ECharts渲染）
2. 核对「设备总数」与设备列表实际数量 → 预期：一致
3. 核对「在线设备数」→ 预期：与 /api/devices/online 返回数量一致
4. 核对「设备状态分布」饼图 → 预期：与 /api/analytics/devices/status 数据一致
5. 核对「设备类型分布」饼图 → 预期：与 /api/analytics/devices/type 数据一致
6. 核对「消息趋势」折线图 → 预期：与 /api/analytics/messages/trend 数据一致
7. 「最近消息」表格 → 预期：与 /api/messages/recent 数据一致

**预期结果：** 仪表盘所有图表与后端统计接口数据一致。

### 2.4 Bug修复流程

**Bug记录格式：**
```markdown
## Bug-XXX：[简短描述]

**发现时间：** 2026-08-06
**发现人：** [测试人员/开发者]
**严重程度：** P0（阻塞）/ P1（重要）/ P2（一般）
**重现步骤：**
1. ...
2. ...
**预期结果：** ...
**实际结果：** ...
**截图/日志：** ...
**修复方案：** ...
**修复人：** ...
**修复时间：** ...
```

**常见Bug类型：**
- 接口返回格式不符（未使用Result包装）
- 前端表单校验缺失
- Token过期后未正确跳转登录页
- 设备列表分页参数不生效
- 消息流未自动滚动
- 历史查询时间范围过滤错误

---

## 3. 开发内容检测环节

### DET-09-01：API接口联调检测

**检测人：** 开发者 + 测试人员

**测试工具：** Postman / curl / Apifox

**认证准备：** 先调用登录接口获取JWT Token（保存为$TOKEN），再调用/api/api-keys创建API Key（保存为$API_KEY）

| 序号 | 接口 | 认证 | 预期HTTP状态 | 预期code | 预期message | 结果 |
|------|------|------|-------------|----------|-------------|------|
| 1 | POST /api/auth/register | 公开 | 200 | 200 | success | ___ |
| 2 | POST /api/auth/login | 公开 | 200 | 200 | success | ___ |
| 3 | POST /api/auth/logout | JWT | 200 | 200 | success | ___ |
| 4 | GET /api/auth/current | JWT | 200 | 200 | success | ___ |
| 5 | POST /api/auth/change-password | JWT | 200 | 200 | success | ___ |
| 6 | POST /api/devices | JWT | 200 | 200 | success | ___ |
| 7 | GET /api/devices | JWT | 200 | 200 | success | ___ |
| 8 | GET /api/devices/{deviceId} | JWT | 200 | 200 | success | ___ |
| 9 | PUT /api/devices/{deviceId} | JWT | 200 | 200 | success | ___ |
| 10 | DELETE /api/devices/{deviceId} | JWT | 200 | 200 | success | ___ |
| 11 | GET /api/devices/online | JWT | 200 | 200 | success | ___ |
| 12 | GET /api/devices/{deviceId}/status | JWT | 200 | 200 | success | ___ |
| 13 | POST /api/messages/publish | JWT | 200 | 200 | success | ___ |
| 14 | GET /api/messages | JWT | 200 | 200 | success | ___ |
| 15 | GET /api/messages/recent | JWT | 200 | 200 | success | ___ |
| 16 | GET /api/messages/trend | JWT | 200 | 200 | success | ___ |
| 17 | GET /api/history | JWT | 200 | 200 | success | ___ |
| 18 | GET /api/history/device/{deviceId} | JWT | 200 | 200 | success | ___ |
| 19 | GET /api/health | 公开 | 200 | - | -（返回Map，非Result包装） | ___ |
| 20 | GET /api/analytics/overview | JWT | 200 | 200 | success | ___ |
| 21 | GET /api/analytics/devices/status | JWT | 200 | 200 | success | ___ |
| 22 | GET /api/analytics/devices/type | JWT | 200 | 200 | success | ___ |
| 23 | GET /api/analytics/messages/trend | JWT | 200 | 200 | success | ___ |
| 24 | GET /api/api-keys | JWT | 200 | 200 | success | ___ |
| 25 | POST /api/api-keys | JWT | 200 | 200 | success | ___ |
| 26 | GET /api/api-keys/{id} | JWT | 200 | 200 | success（越权时403） | ___ |
| 27 | DELETE /api/api-keys/{id} | JWT | 200 | 200 | success（越权时403） | ___ |
| 28 | GET /api/webhooks | JWT | 200 | 200 | success | ___ |
| 29 | POST /api/webhooks | JWT | 200 | 200 | success | ___ |
| 30 | GET /api/webhooks/{id} | JWT | 200 | 200 | success（越权时403） | ___ |
| 31 | PUT /api/webhooks/{id} | JWT | 200 | 200 | success（越权时403） | ___ |
| 32 | DELETE /api/webhooks/{id} | JWT | 200 | 200 | success（越权时403） | ___ |
| 33 | POST /api/webhooks/{id}/test | JWT | 200 | 200 | success（越权时403） | ___ |
| 34 | GET /api/external/v1/devices | API Key | 200 | 200 | success（无效Key时401） | ___ |
| 35 | GET /api/external/v1/devices/{deviceKey} | API Key | 200 | 200 | success | ___ |
| 36 | POST /api/external/v1/devices/{deviceKey}/command | API Key | 200 | 200 | success | ___ |
| 37 | GET /api/external/v1/devices/{deviceKey}/status | API Key | 200 | 200 | success | ___ |
| 38 | GET /api/external/v1/stats | API Key | 200 | 200 | success | ___ |

**验证命令示例：**
```bash
# 1. 登录获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# 2. 创建设备（JWT认证）
curl -s -X POST http://localhost:8080/api/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceName":"测试设备","deviceKey":"test-dev-001","deviceType":"sensor","topic":"device/test-dev-001/data"}' | jq '.code'

# 3. 查询统计接口（JWT认证）
curl -s http://localhost:8080/api/analytics/overview \
  -H "Authorization: Bearer $TOKEN" | jq '.data'

# 4. 创建API Key（JWT认证），保存返回的完整密钥
API_KEY=$(curl -s -X POST http://localhost:8080/api/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"test-key","permissions":"[\"device:read\"]"}' | jq -r '.data.keyValue')

# 5. 外部API调用（X-API-Key认证）
curl -s http://localhost:8080/api/external/v1/devices \
  -H "X-API-Key: $API_KEY" | jq '.code'

# 6. 外部API发送指令
curl -s -X POST http://localhost:8080/api/external/v1/devices/test-dev-001/command \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"payload":"{\"action\":\"restart\"}","qos":1}' | jq '.message'

# 7. Webhook CRUD（JWT认证）
curl -s -X POST http://localhost:8080/api/webhooks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"测试Webhook","url":"http://localhost:5000/hook","events":"device.online"}' | jq '.code'
```

**异常路径抽检（每个模块至少验证1个失败场景）：**

| 序号 | 异常场景 | 预期HTTP状态 | 预期code | 结果 |
|------|----------|-------------|----------|------|
| E1 | 无Token访问 /api/devices | 401 | 401 | ___ |
| E2 | 无效Token访问 /api/devices | 401 | 3003（TOKEN_INVALID） | ___ |
| E3 | 重复用户名注册 | 200 | 1001（USERNAME_EXISTS） | ___ |
| E4 | 错误密码登录 | 200 | 3001（INVALID_CREDENTIALS） | ___ |
| E5 | 用户B访问用户A的设备 | 200 | 2003（DEVICE_NOT_OWNED） | ___ |
| E6 | 重复deviceKey创建设备 | 200 | 2001（DEVICE_KEY_EXISTS） | ___ |
| E7 | 无效API Key调外部接口 | 200 | 401（Result.error手工返回） | ___ |
| E8 | 用户B删除用户A的API Key | 200 | 403（FORBIDDEN） | ___ |
| E9 | 用户B访问用户A的Webhook | 200 | 403（FORBIDDEN） | ___ |

### DET-09-02：端到端测试检测

**检测人：** 测试人员

**测试环境：** Chrome浏览器、后端服务运行、EMQX运行

| 测试场景 | 预期结果 | 实际结果 | 通过 |
|---------|---------|---------|------|
| S1: 完整用户流程 | 登录→设备CRUD→消息监控→历史查询→登出，全部按预期 | ___ | ___ |
| S2: 多用户数据隔离 | 用户A看不到用户B的设备 | ___ | ___ |
| S3: 设备生命周期 | INACTIVE→ONLINE→OFFLINE→ONLINE→删除，流转正确 | ___ | ___ |
| S4: API密钥与外部API | 创建→使用→吊销全流程正确，5个外部接口调用正常 | ___ | ___ |
| S5: Webhook配置 | CRUD+测试+所有权隔离正确 | ___ | ___ |
| S6: 仪表盘统计 | 所有图表与统计接口数据一致 | ___ | ___ |

**测试记录模板：**
```markdown
### S1: 完整用户流程
- [x] 1. 登录页正常显示
- [x] 2. 错误凭证显示错误
- [x] 3. 正确凭证登录成功
- ...
**结果：通过** / **结果：未通过（问题描述）**
```

### DET-09-03：前端页面检测

**检测人：** 开发者 + UI/UX测试

| 检查项 | 检测内容 | 验证方法 | 标准 | 结果 |
|--------|----------|----------|------|------|
| 页面加载 | 所有页面无404 | 逐页访问 | /login, /register, /landing, /dashboard, /devices, /messages, /history, /api-docs, /settings/api-keys, /settings/webhooks 全部加载 | ___ |
| 交互正常 | 按钮/表单/导航可用 | 手动测试 | 点击、输入、提交全部正常 | ___ |
| 数据展示 | 列表/表格/详情正确 | 对比后端API响应 | 前端展示与API返回一致 | ___ |
| 响应式 | 桌面1920x1080正常 | 浏览器调整窗口 | 布局正常、无溢出 | ___ |
| 极简风格 | 黑白灰色系 | 视觉检查 | 无彩色装饰、SVG图标、无Emoji | ___ |
| 浏览器兼容 | Chrome/Firefox/Edge | 三浏览器测试 | 主要功能在各浏览器可用 | ___ |
| 控制台无错误 | Console无红色错误 | 开发者工具Console | 无ERROR级别日志 | ___ |
| 网络无失败 | Network无失败请求 | 开发者工具Network | 所有请求状态码200/401/403（无5xx） | ___ |

### DET-09-04：接口文档检测

**检测人：** 开发者自检

| 检查项 | 检测内容 | 验证方法 | 标准 | 结果 |
|--------|----------|----------|------|------|
| Swagger UI | 可访问 | 浏览器访问 http://localhost:8080/api/swagger-ui.html | 页面正常加载 | ___ |
| 接口说明 | 每个接口有描述 | Swagger UI中逐接口查看 | 每个接口有summary和description | ___ |
| 参数说明 | 参数类型和必填标注 | Swagger UI中查看参数 | @Parameter注解完整 | ___ |
| 响应示例 | 有响应schema | Swagger UI中查看响应 | @Schema注解定义响应结构 | ___ |
| 分组正确 | 4个已配置分组显示 | Swagger UI左侧分组 | 认证管理、设备管理、消息管理、历史查询 | ___ |
| 新增模块分组 | Analytics/ApiKey/Webhook/External接口的文档归属 | Swagger UI中查找新模块接口 | **当前OpenApiConfig仅配置4个分组，新模块接口归入默认分组或无分组，需在OpenApiConfig中补充4个GroupedOpenApi（统计分析、API密钥、Webhook、外部API）** | ___ |

---

## 4. 验收标准

| 序号 | 验收项 | 通过标准 | 结果 |
|------|--------|----------|------|
| ACC-01 | API联调 | 38个接口全部联调通过（含JWT/API Key两种认证路径）、响应格式统一（Result）、状态码正确 | ___ |
| ACC-02 | 端到端测试 | 6个测试场景全部通过、无阻塞性问题 | ___ |
| ACC-03 | 前端页面 | 所有页面（10个路由）加载正常、交互正常、数据展示正确、无控制台错误 | ___ |
| ACC-04 | 接口文档 | Swagger UI可访问、接口文档完整、分组正确（新模块分组需补充配置） | ___ |
| ACC-05 | Bug修复 | 联调中发现的所有P0/P1级Bug已修复 | ___ |

---

## 5. 验收后更新总督促文档

| 更新项 | 内容 |
|--------|------|
| 总督促进度表 | 更新T-09状态为"完成"、填入实际完成日期和工时 |
| 总督促验收表 | 填入ACC-01到ACC-05结果 |
| 总督促更新日志 | 追加T-09完成记录 |

---

## 6. 文件清单

| 文件路径 | 说明 | 状态 |
|----------|------|------|
| `backend/src/main/java/.../config/OpenApiConfig.java` | Swagger配置（已创建，需补充4个新模块分组） | 待更新 |
| 各Controller（更新） | 添加/完善@Operation等Swagger注解（重点补充Analytics/ApiKey/Webhook/ExternalApi四个控制器） | 待更新 |
| `docs/T-09_API集成与端到端测试_开发文档.md` | 本文档 | 已创建 |
| 测试Bug记录 | Bug修复记录（Markdown格式） | 测试过程中维护 |

---

## 7. 依赖说明

**上游依赖：** T-03, T-04, T-05, T-06, T-07, T-08

**下游依赖本子任务的文件：**
- T-10（部署：基于本子任务的集成测试结果）

---

## 8. 常见问题

**Q: 端到端测试中如何验证MQTT消息？**
A: 使用独立的MQTT客户端（如mosquitto_pub/mosquitto_sub或MQTTX）发送和订阅消息，同时观察后端日志和前端页面，确认消息流完整。

**Q: Swagger文档不显示RequestBody的示例怎么办？**
A: 使用@Schema(implementation = DTO.class)注解，确保DTO类有正确的@Schema注解或Lombok的@Data注解生成getter/setter。

**Q: 前端测试中如何模拟不同用户登录？**
A: 使用浏览器的无痕模式（Incognito），或使用不同的浏览器分别登录不同用户。

**Q: 端到端测试中发现Bug如何记录？**
A: 使用本文档2.4节的Bug记录格式，记录发现时间、重现步骤、预期/实际结果，分配给对应开发者修复。
