/**
 * 开放 API 端点单一数据源。
 * 供文档站「开放 API」章节与工作台内引用复用，避免多处维护。
 * 以实际后端 `ExternalApiController` 为准，路径统一以 /api 为前缀。
 */

const ep = (method, path, desc, example = '', response = '') => ({
  method,
  path,
  desc,
  example,
  response
})

const KEY_HEADER = '-H "X-API-Key: your-api-key"'
const BASE = 'https://your-server/api/external/v1'

const pageResponse = `{
  "code": 200,
  "data": {
    "records": [],
    "total": 0,
    "current": 1,
    "size": 20
  }
}`

export const endpointGroups = [
  {
    key: 'devices',
    title: '设备与状态',
    endpoints: [
      ep(
        'GET',
        '/external/v1/devices',
        '返回密钥所属用户名下设备列表。可选 query 参数 deviceKey 精确查询单台设备。',
        `curl -X GET "${BASE}/devices" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 1,
      "deviceKey": "sensor-temp-001",
      "deviceName": "温度传感器-01",
      "deviceType": "sensor",
      "topic": "device/sensor-temp-001/data",
      "status": "ONLINE",
      "lastSeen": "2026-10-22T10:00:00",
      "firmwareVersion": "1.2.3"
    }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}',
        '按 deviceKey 返回设备完整信息，额外包含 description 与 metadata；非本人设备返回 2003。',
        `curl -X GET "${BASE}/devices/sensor-temp-001" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "id": 1,
    "deviceKey": "sensor-temp-001",
    "deviceName": "温度传感器-01",
    "deviceType": "sensor",
    "status": "ONLINE",
    "firmwareVersion": "1.2.3",
    "description": "客厅温度传感器",
    "metadata": {"unit": "°C", "min": -20, "max": 80}
  }
}`
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/status',
        '返回设备在线状态：status / lastSeen / online。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/status" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "deviceKey": "sensor-temp-001",
    "status": "ONLINE",
    "lastSeen": "2026-10-22T10:00:00",
    "online": true
  }
}`
      )
    ]
  },
  {
    key: 'control',
    title: '设备控制与影子',
    endpoints: [
      ep(
        'POST',
        '/external/v1/devices/{deviceKey}/command',
        '按物模型校验后下发命令（source=OPEN_API）。请求体：type=property_set|service，identifier（service 必填），params（JSON），callType=async|sync。',
        `curl -X POST "${BASE}/devices/switch-001/command" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d '{"type":"property_set","params":{"power":true},"callType":"async"}'`,
        `{
  "code": 200,
  "data": {
    "commandId": "8f1c0b6e-...-a1",
    "commandType": "property_set",
    "status": "SENT",
    "callType": "async"
  }
}`
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/commands',
        '命令记录分页，按创建时间倒序。query：page（从 1 起）、size（上限 100）。',
        `curl -X GET "${BASE}/devices/switch-001/commands?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/shadow',
        '返回 desired / reported / delta 三份状态与影子版本号；影子不存在时返回空映射与 version=0，modeled=false 表示产品未定义物模型。',
        `curl -X GET "${BASE}/devices/switch-001/shadow" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "deviceId": 1,
    "version": 12,
    "modeled": true,
    "desired": {"power": true},
    "reported": {"power": true},
    "delta": {}
  }
}`
      ),
      ep(
        'PUT',
        '/external/v1/devices/{deviceKey}/shadow/desired',
        '写入影子期望值，等价于 property_set 命令（source=OPEN_API）。在线返回 SENT，离线返回 QUEUED 并在上线后补发；params 必须为非空 JSON 对象，否则返回 6206。',
        `curl -X PUT "${BASE}/devices/switch-001/shadow/desired" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d '{"params":{"power":true}}'`,
        `{
  "code": 200,
  "data": {
    "commandId": "2b9f...",
    "commandType": "property_set",
    "status": "QUEUED"
  }
}`
      )
    ]
  },
  {
    key: 'data',
    title: '属性、事件与时序',
    endpoints: [
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/properties',
        '返回设备全部属性的最新上报值（identifier / dataType / value / reportedAt）；无上报时返回空数组。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/properties" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "identifier": "temperature",
      "dataType": "float",
      "value": "26.5",
      "reportedAt": "2026-10-22T10:00:00"
    }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/events',
        '事件记录分页，按时间倒序。query：page、size（上限 100）。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/events?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/properties/history',
        '属性时序分桶聚合。query：identifiers（逗号分隔，空=全部已建模属性）、startTime、endTime、bucket=1m|5m|15m|30m|1h|6h|1d。非法范围 6225、未建模 6226、桶非法 6227。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/properties/history?identifiers=temperature&bucket=5m" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "deviceId": 1,
      "identifier": "temperature",
      "dataType": "float",
      "numeric": true,
      "points": [{"time": "2026-10-22 10:00:00", "count": 5, "min": 26.1, "max": 27.0, "avg": 26.5}]
    }
  ]
}`
      )
    ]
  },
  {
    key: 'messages',
    title: '消息与设备日志',
    endpoints: [
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/messages',
        '消息历史分页，按上报时间倒序。query：topic、startTime、endTime、page、size（上限 100）。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/messages?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/logs',
        '设备统一日志时间线（消息 / 事件 / 命令 / 连接状态）。query：types=message,event,command,status、startTime、endTime、keyword、page、size。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/logs?types=message,command&page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      )
    ]
  },
  {
    key: 'rules',
    title: '规则与告警（只读）',
    endpoints: [
      ep(
        'GET',
        '/external/v1/rules',
        '只读返回本人消息规则。query：sourceType=PROPERTY|EVENT、actionType、enabled、keyword、page、size。',
        `curl -X GET "${BASE}/rules?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/alerts',
        '告警记录分页，按最近触发时间倒序。query：status、sourceType、severity、deviceId、openOnly、page、size；旧版 pageNum/pageSize 仍兼容。',
        `curl -X GET "${BASE}/alerts?status=TRIGGERED&page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/alerts/{id}',
        '按 ID 返回本账号告警详情；不存在 6209，越权 403。',
        `curl -X GET "${BASE}/alerts/1001" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "id": 1001,
    "ruleName": "高温告警",
    "severity": "CRITICAL",
    "status": "TRIGGERED",
    "triggerValue": "86.5"
  }
}`
      ),
      ep(
        'GET',
        '/external/v1/alerts/unread-count',
        '返回本账号活动告警数量。',
        `curl -X GET "${BASE}/alerts/unread-count" \\\n  ${KEY_HEADER}`,
        `{"code":200,"data":3}`
      ),
      ep(
        'GET',
        '/external/v1/alerts/rules',
        '只读返回本人告警规则。query：sourceType=THRESHOLD|OFFLINE|EVENT、enabled。',
        `curl -X GET "${BASE}/alerts/rules?sourceType=THRESHOLD" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 10,
      "name": "高温告警",
      "sourceType": "THRESHOLD",
      "identifier": "temperature",
      "operator": "GT",
      "thresholdValue": "80",
      "windowSeconds": 300,
      "aggregateType": "AVG",
      "minSamples": 3,
      "enabled": 1
    }
  ]
}`
      )
    ]
  },
  {
    key: 'ota',
    title: 'OTA（只读）',
    endpoints: [
      ep(
        'GET',
        '/external/v1/ota/firmwares',
        '只读返回本人固件包，可按 productId 过滤；不提供上传 / 删除。',
        `curl -X GET "${BASE}/ota/firmwares?productId=1" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 3,
      "productId": 1,
      "version": "1.2.0",
      "fileName": "firmware-1.2.0.bin",
      "fileSize": 524288,
      "md5": "d41d8cd98f00b204e9800998ecf8427e"
    }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/ota/tasks',
        '只读返回本人升级任务聚合视图。query：page、size（上限 100）。',
        `curl -X GET "${BASE}/ota/tasks?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/ota/tasks/{id}',
        '返回任务聚合、灰度批次时间线与目标快照；逐台记录走独立端点。',
        `curl -X GET "${BASE}/ota/tasks/12" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "id": 12,
    "name": "固件 1.2.0 灰度",
    "version": "1.2.0",
    "status": "RUNNING",
    "batchMode": "BATCHED",
    "batchTotal": 3,
    "currentBatchNo": 1
  }
}`
      ),
      ep(
        'GET',
        '/external/v1/ota/tasks/{id}/records',
        '按任务 ID 分页返回逐台升级记录；query：status、page、size（上限 100）。',
        `curl -X GET "${BASE}/ota/tasks/12/records?status=FAILED&page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      )
    ]
  },
  {
    key: 'scenes',
    title: '场景（只读）',
    endpoints: [
      ep(
        'GET',
        '/external/v1/scenes',
        '只读返回本人场景定义。query：triggerType、enabled、keyword、page、size（上限 100）。',
        `curl -X GET "${BASE}/scenes?page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      )
    ]
  },
  {
    key: 'organization',
    title: '设备组织（只读）',
    endpoints: [
      ep(
        'GET',
        '/external/v1/device-groups',
        '只读返回密钥所属用户的扁平分组列表；不包含共享分组，避免越出账号数据边界。',
        `curl -X GET "${BASE}/device-groups" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 1,
      "parentId": null,
      "name": "一号车间",
      "sortOrder": 0,
      "description": "产线设备",
      "deviceCount": 12,
      "createdAt": "2026-10-20T09:00:00"
    }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/device-groups/tree',
        '只读返回分组树，children 递归嵌套。',
        `curl -X GET "${BASE}/device-groups/tree" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "一号车间",
      "deviceCount": 12,
      "children": [
        { "id": 2, "parentId": 1, "name": "A 线", "deviceCount": 5 }
      ]
    }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/device-tags',
        '只读返回密钥所属用户的设备标签。',
        `curl -X GET "${BASE}/device-tags" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    { "id": 1, "name": "重点设备", "color": "#E6A23C", "createdAt": "2026-10-20T09:00:00" }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/devices/{deviceKey}/points',
        '只读返回设备点位配置（不触发采集）；query：executor=EDGE|PLATFORM。',
        `curl -X GET "${BASE}/devices/sensor-temp-001/points?executor=PLATFORM" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    {
      "id": 1,
      "pointKey": "temperature",
      "address": "40001",
      "dataType": "int16",
      "scale": 0.1,
      "unit": "℃",
      "executor": "PLATFORM",
      "lastRunStatus": "OK"
    }
  ]
}`
      )
    ]
  },
  {
    key: 'executions',
    title: '执行记录与看板（只读）',
    endpoints: [
      ep(
        'GET',
        '/external/v1/rules/executions',
        '按规则 / 设备 / 状态分页查询消息规则执行记录。query：ruleId、deviceId、status、page、size。',
        `curl -X GET "${BASE}/rules/executions?status=FAILED&page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/scenes/executions',
        '按场景 / 设备 / 状态分页查询场景执行记录。query：sceneId、deviceId、status、page、size。',
        `curl -X GET "${BASE}/scenes/executions?status=FAILED&page=1&size=20" \\\n  ${KEY_HEADER}`,
        pageResponse
      ),
      ep(
        'GET',
        '/external/v1/scenes/executions/{id}',
        '返回单次场景执行详情与逐步步骤明细；不存在 6243 / 越权 403。',
        `curl -X GET "${BASE}/scenes/executions/88" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'GET',
        '/external/v1/dashboards',
        '只读返回本人看板摘要（不含 config 明细）。',
        `curl -X GET "${BASE}/dashboards" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": [
    { "id": 3, "name": "产线总览", "panelCount": 4, "updatedAt": "2026-10-22T10:00:00" }
  ]
}`
      ),
      ep(
        'GET',
        '/external/v1/dashboards/{id}',
        '返回看板名称与面板配置；不存在或非本人 6228。',
        `curl -X GET "${BASE}/dashboards/3" \\\n  ${KEY_HEADER}`
      )
    ]
  },
  {
    key: 'writes',
    title: '写操作（设备 / 规则 / 场景）',
    endpoints: [
      ep(
        'POST',
        '/external/v1/devices',
        '为密钥所属用户创建设备；deviceKey 同产品内唯一。响应含一次性明文密钥，仅本次返回。',
        `curl -X POST "${BASE}/devices" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d '{"productId":3,"deviceKey":"sensor-002","deviceName":"传感器-02","deviceType":"sensor","topic":"device/sensor-002/data"}'`,
        `{
  "code": 200,
  "data": {
    "id": 12,
    "deviceKey": "sensor-002",
    "productKey": "env-sensor",
    "username": "env-sensor.sensor-002",
    "deviceSecret": "one-time-secret"
  }
}`
      ),
      ep(
        'PUT',
        '/external/v1/devices/{deviceKey}',
        '更新设备名称 / 类型 / Topic / 描述；非归属设备返回 2003。',
        `curl -X PUT "${BASE}/devices/sensor-002" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d '{"deviceName":"传感器-02-A"}'`
      ),
      ep(
        'DELETE',
        '/external/v1/devices/{deviceKey}',
        '逻辑删除设备；非归属设备返回 2003。',
        `curl -X DELETE "${BASE}/devices/sensor-002" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'POST',
        '/external/v1/rules',
        '创建消息规则；校验口径与控制台一致（非法 6219 / 动作 6220 / 越权设备 2003 / 超限 6222）。',
        `curl -X POST "${BASE}/rules" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d @rule.json`
      ),
      ep(
        'PUT',
        '/external/v1/rules/{id}',
        '整体更新消息规则；不存在 6218 / 越权 403。',
        `curl -X PUT "${BASE}/rules/9" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d @rule.json`
      ),
      ep(
        'DELETE',
        '/external/v1/rules/{id}',
        '逻辑删除消息规则；执行记录保留。',
        `curl -X DELETE "${BASE}/rules/9" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'PUT',
        '/external/v1/rules/{id}/enabled',
        '启用 / 停用消息规则。query：enabled=true|false。',
        `curl -X PUT "${BASE}/rules/9/enabled?enabled=false" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'POST',
        '/external/v1/scenes',
        '创建场景联动；校验口径与控制台一致（非法 6241 / 动作 6242 / 触发源 6245 / 超限 6244 / 越权设备 2003）。',
        `curl -X POST "${BASE}/scenes" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d @scene.json`
      ),
      ep(
        'PUT',
        '/external/v1/scenes/{id}',
        '整体更新场景（步骤流整体替换）；不存在 6240 / 越权 403。',
        `curl -X PUT "${BASE}/scenes/12" \\\n  ${KEY_HEADER} \\\n  -H "Content-Type: application/json" \\\n  -d @scene.json`
      ),
      ep(
        'DELETE',
        '/external/v1/scenes/{id}',
        '逻辑删除场景；执行记录保留。',
        `curl -X DELETE "${BASE}/scenes/12" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'PUT',
        '/external/v1/scenes/{id}/enabled',
        '启用 / 停用场景。query：enabled=true|false。',
        `curl -X PUT "${BASE}/scenes/12/enabled?enabled=true" \\\n  ${KEY_HEADER}`
      ),
      ep(
        'POST',
        '/external/v1/scenes/{id}/run',
        '手动执行一次场景；返回执行记录 ID。',
        `curl -X POST "${BASE}/scenes/12/run" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": 88
}`
      )
    ]
  },
  {
    key: 'stats',
    title: '统计与 HTTP 上报',
    endpoints: [
      ep(
        'GET',
        '/external/v1/stats',
        '返回 totalDevices / onlineDevices / apiKeyName / permissions（JSON 数组字符串）。',
        `curl -X GET "${BASE}/stats" \\\n  ${KEY_HEADER}`,
        `{
  "code": 200,
  "data": {
    "totalDevices": 3,
    "onlineDevices": 1,
    "apiKeyName": "MyApp Key",
    "permissions": "[\\"device:read\\",\\"device:write\\",\\"stats:read\\"]"
  }
}`
      ),
      ep(
        'POST',
        '/ingest/{deviceKey}/{messageType}',
        '设备 HTTP 上报。HTTP Basic 鉴权，用户名 {productKey}.{deviceKey}、密码 device_secret；messageType=data|heartbeat|lwt。与 MQTT 上报同分片、同顺序，属性最新值等价。可选请求头 Idempotency-Key 用于弱网重试去重；超出设备频率返回 429 + 6314。',
        `curl -X POST "https://your-server/api/ingest/sensor-temp-001/data" \\\n  -u "esp32-fall-detect.sensor-temp-001:<device_secret>" \\\n  -H "Content-Type: application/json" \\\n  -d '{"method":"thing.event.property.post","params":{"temperature":26.5}}'`,
        `{
  "code": 200,
  "message": "成功",
  "data": {
    "deviceKey": "sensor-temp-001",
    "messageType": "data",
    "topic": "device/sensor-temp-001/data",
    "receivedAt": "2026-10-22T12:00:00",
    "accepted": true
  }
}`
      )
    ]
  }
]

const GROUP_SUMMARIES = {
  '设备与状态': '检索设备、读取详情与在线状态，是所有设备级接口的入口。',
  '设备控制与影子': '命令下发、命令记录、影子读取与 desired 写入；写操作需要 device:write。',
  '属性、事件与时序': '读取最新属性、事件记录与属性时序分桶聚合，适合做业务侧数据展示。',
  '消息与设备日志': '原始消息历史与统一设备时间线，用于对账、审计与排障。',
  '规则与告警（只读）': '读取规则与告警记录；规则与告警的写操作仍由控制台完成。',
  'OTA（只读）': '读取固件包、升级任务、批次与逐台记录；上传 / 下发 / 推进仍在控制台。',
  '场景（只读）': '读取场景定义与配置，用于外部展示或同步。',
  '设备组织（只读）': '读取分组、标签与点位表，用于外部系统对齐设备组织与采集配置。',
  '执行记录与看板（只读）': '读取规则 / 场景执行记录与看板配置，用于外部监控与报表。',
  '写操作（设备 / 规则 / 场景）': '开放 API 的写入口，全部需要 device:write scope；设备写按 deviceKey 归属校验，规则 / 场景由 userId 隔离。',
  '统计与 HTTP 上报': '账号级统计与设备 HTTP 上报入口；上报使用设备凭据而非 API Key。'
}

const p = (name, where, type, required, desc) => ({ name, in: where, type, required, desc })

const ENDPOINT_META = {
  'GET /external/v1/devices': {
    params: [p('deviceKey', 'query', 'string', false, '精确查询单台设备；留空返回列表')]
  },
  'GET /external/v1/devices/{deviceKey}': {
    params: [p('deviceKey', 'path', 'string', true, '设备唯一标识')]
  },
  'GET /external/v1/devices/{deviceKey}/status': {
    params: [p('deviceKey', 'path', 'string', true, '设备唯一标识')]
  },
  'POST /external/v1/devices/{deviceKey}/command': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('type', 'body', 'string', true, 'property_set / service'),
      p('identifier', 'body', 'string', false, '服务标识符；property_set 必须为空'),
      p('params', 'body', 'object', true, '命令参数 JSON 对象'),
      p('callType', 'body', 'string', false, 'async（默认）/ sync')
    ],
    note: 'sync 会等待回执到终态再返回；来源记为 OPEN_API。'
  },
  'GET /external/v1/devices/{deviceKey}/commands': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/devices/{deviceKey}/shadow': {
    params: [p('deviceKey', 'path', 'string', true, '设备唯一标识')]
  },
  'PUT /external/v1/devices/{deviceKey}/shadow/desired': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('params', 'body', 'object', true, '非空 JSON 对象，写入 desired')
    ],
    note: '等价于 property_set 命令；离线设备返回 QUEUED 并在上线后补发。'
  },
  'GET /external/v1/devices/{deviceKey}/properties': {
    params: [p('deviceKey', 'path', 'string', true, '设备唯一标识')]
  },
  'GET /external/v1/devices/{deviceKey}/events': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/devices/{deviceKey}/properties/history': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('identifiers', 'query', 'string', false, '逗号分隔；留空取全部已建模属性'),
      p('startTime', 'query', 'string', false, '起始时间'),
      p('endTime', 'query', 'string', false, '结束时间'),
      p('bucket', 'query', 'string', false, '1m / 5m / 15m / 30m / 1h / 6h / 1d')
    ]
  },
  'GET /external/v1/devices/{deviceKey}/messages': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('topic', 'query', 'string', false, 'Topic 关键词过滤'),
      p('startTime', 'query', 'string', false, '起始时间'),
      p('endTime', 'query', 'string', false, '结束时间'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/devices/{deviceKey}/logs': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('types', 'query', 'string', false, 'message / event / command / status，可重复'),
      p('startTime', 'query', 'string', false, '起始时间'),
      p('endTime', 'query', 'string', false, '结束时间'),
      p('keyword', 'query', 'string', false, '关键字'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/rules': {
    params: [
      p('sourceType', 'query', 'string', false, 'PROPERTY / EVENT'),
      p('actionType', 'query', 'string', false, '规则动作类型'),
      p('enabled', 'query', 'boolean', false, '启用状态'),
      p('keyword', 'query', 'string', false, '名称 / 标识符关键字'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/alerts': {
    params: [
      p('status', 'query', 'string', false, 'TRIGGERED / ACKNOWLEDGED / RECOVERED'),
      p('sourceType', 'query', 'string', false, 'THRESHOLD / OFFLINE / EVENT'),
      p('severity', 'query', 'string', false, 'INFO / WARNING / CRITICAL'),
      p('deviceId', 'query', 'number', false, '设备 ID'),
      p('openOnly', 'query', 'boolean', false, '仅看未恢复告警；与 status 同传时以 status 为准'),
      p('page', 'query', 'number', false, '页码，从 1 起，默认 1'),
      p('size', 'query', 'number', false, '每页条数，默认 20，上限 100')
    ],
    note: '旧版 pageNum / pageSize 仍兼容；新接入请使用 page / size。'
  },
  'GET /external/v1/alerts/{id}': {
    params: [p('id', 'path', 'number', true, '告警 ID')]
  },
  'GET /external/v1/alerts/unread-count': { params: [] },
  'GET /external/v1/alerts/rules': {
    params: [
      p('sourceType', 'query', 'string', false, 'THRESHOLD / OFFLINE / EVENT'),
      p('enabled', 'query', 'boolean', false, '启用状态')
    ]
  },
  'GET /external/v1/ota/firmwares': {
    params: [p('productId', 'query', 'number', false, '按产品过滤')]
  },
  'GET /external/v1/ota/tasks': {
    params: [
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/ota/tasks/{id}': {
    params: [p('id', 'path', 'number', true, '任务 ID')]
  },
  'GET /external/v1/ota/tasks/{id}/records': {
    params: [
      p('id', 'path', 'number', true, '任务 ID'),
      p('status', 'query', 'string', false, '记录状态过滤'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/scenes': {
    params: [
      p('triggerType', 'query', 'string', false, 'PROPERTY / EVENT / TIMER / ONLINE / OFFLINE / SHADOW'),
      p('enabled', 'query', 'boolean', false, '启用状态'),
      p('keyword', 'query', 'string', false, '名称关键字'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/device-groups': { params: [] },
  'GET /external/v1/device-groups/tree': { params: [] },
  'GET /external/v1/device-tags': { params: [] },
  'GET /external/v1/devices/{deviceKey}/points': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('executor', 'query', 'string', false, 'EDGE / PLATFORM')
    ]
  },
  'GET /external/v1/rules/executions': {
    params: [
      p('ruleId', 'query', 'number', false, '规则 ID'),
      p('deviceId', 'query', 'number', false, '设备 ID'),
      p('status', 'query', 'string', false, 'PENDING / SUCCESS / FAILED'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/scenes/executions': {
    params: [
      p('sceneId', 'query', 'number', false, '场景 ID'),
      p('deviceId', 'query', 'number', false, '触发设备 ID'),
      p('status', 'query', 'string', false, 'PENDING / RUNNING / SUCCESS / FAILED / PARTIAL'),
      p('page', 'query', 'number', false, '页码，从 1 起'),
      p('size', 'query', 'number', false, '每页条数，上限 100')
    ]
  },
  'GET /external/v1/scenes/executions/{id}': {
    params: [p('id', 'path', 'number', true, '执行记录 ID')]
  },
  'GET /external/v1/dashboards': { params: [] },
  'GET /external/v1/dashboards/{id}': {
    params: [p('id', 'path', 'number', true, '看板 ID')]
  },
    'GET /external/v1/stats': { params: [] },
  'POST /external/v1/devices': {
    params: [
      p('productId', 'body', 'number', true, '产品 ID'),
      p('deviceKey', 'body', 'string', true, '设备唯一标识（同产品内唯一）'),
      p('deviceName', 'body', 'string', true, '设备名称'),
      p('deviceType', 'body', 'string', true, '设备类型'),
      p('topic', 'body', 'string', true, 'MQTT Topic'),
      p('description', 'body', 'string', false, '设备描述')
    ]
  },
  'PUT /external/v1/devices/{deviceKey}': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('deviceName', 'body', 'string', false, '设备名称'),
      p('deviceType', 'body', 'string', false, '设备类型'),
      p('topic', 'body', 'string', false, 'MQTT Topic'),
      p('description', 'body', 'string', false, '设备描述')
    ]
  },
  'DELETE /external/v1/devices/{deviceKey}': {
    params: [p('deviceKey', 'path', 'string', true, '设备唯一标识')]
  },
  'POST /external/v1/rules': {
    params: [p('body', 'body', 'object', true, 'RuleRequest，与控制台创建接口同结构')]
  },
  'PUT /external/v1/rules/{id}': {
    params: [
      p('id', 'path', 'number', true, '规则 ID'),
      p('body', 'body', 'object', true, 'RuleRequest 全量覆盖')
    ]
  },
  'DELETE /external/v1/rules/{id}': {
    params: [p('id', 'path', 'number', true, '规则 ID')]
  },
  'PUT /external/v1/rules/{id}/enabled': {
    params: [
      p('id', 'path', 'number', true, '规则 ID'),
      p('enabled', 'query', 'boolean', true, '启用状态')
    ]
  },
  'POST /external/v1/scenes': {
    params: [p('body', 'body', 'object', true, 'SceneRequest，与控制台创建接口同结构')]
  },
  'PUT /external/v1/scenes/{id}': {
    params: [
      p('id', 'path', 'number', true, '场景 ID'),
      p('body', 'body', 'object', true, 'SceneRequest 全量覆盖')
    ]
  },
  'DELETE /external/v1/scenes/{id}': {
    params: [p('id', 'path', 'number', true, '场景 ID')]
  },
  'PUT /external/v1/scenes/{id}/enabled': {
    params: [
      p('id', 'path', 'number', true, '场景 ID'),
      p('enabled', 'query', 'boolean', true, '启用状态')
    ]
  },
  'POST /external/v1/scenes/{id}/run': {
    params: [p('id', 'path', 'number', true, '场景 ID')]
  },
  'POST /ingest/{deviceKey}/{messageType}': {
    params: [
      p('deviceKey', 'path', 'string', true, '设备唯一标识'),
      p('messageType', 'path', 'string', true, 'data / heartbeat / lwt')
    ],
    auth: 'HTTP Basic',
    note: '用户名 {productKey}.{deviceKey}，密码 device_secret；请求体为设备原始载荷。可选 Idempotency-Key；重复请求返回 accepted=false 且响应头 X-Ingest-Duplicate=true。'
  }
}

// 把摘要 / 参数 / 认证方式合并进端点数据，避免在每个端点对象里重复字段
endpointGroups.forEach((group) => {
  group.summary = GROUP_SUMMARIES[group.title] || ''
  group.endpoints.forEach((endpoint) => {
    const meta = ENDPOINT_META[`${endpoint.method} ${endpoint.path}`] || {}
    endpoint.params = meta.params || []
    endpoint.auth = meta.auth || 'X-API-Key'
    endpoint.note = meta.note || ''
  })
})

export const errorCodes = [
  { code: 200, message: '成功', desc: '请求已正确处理' },
  { code: 400, message: '参数错误', desc: '请求参数缺失或格式不合法' },
  { code: 401, message: '未认证', desc: '缺少或无效的 X-API-Key / Bearer Token' },
  { code: 403, message: '无权限', desc: '访问他人资源、角色不允许，或 API Key 缺少所需 scope' },
  { code: 404, message: '资源不存在', desc: '设备标识或路径不存在' },
  { code: 409, message: '状态冲突', desc: '资源当前状态不允许该操作（如重试非 FAILED 执行记录）' },
  { code: 413, message: '载荷过大', desc: 'HTTP 上报或上传内容超过限制' },
  { code: 500, message: '服务端错误', desc: '服务内部异常，请稍后重试或联系管理员' },
  { code: 2002, message: '设备不存在', desc: 'deviceKey 不存在或已删除' },
  { code: 2003, message: '无权限操作此设备', desc: '开放 API 只能访问密钥所属用户的设备' },
  { code: 3501, message: '无效的 API Key', desc: '密钥不存在、已撤销或请求头缺失' },
  { code: 4001, message: '消息发送失败', desc: '命令或下行消息未能发布到 Broker' },
  { code: 4002, message: '超出调用频率限制', desc: '开放接口按 API Key 维度限流，响应带 Retry-After' },
  { code: 6005, message: '设备已被禁用', desc: '设备处于停用状态，恢复后才能下发命令' },
  { code: 4291, message: '命令调用配额超限', desc: '按操作者固定窗口计数，可调整 COMMAND_QUOTA_* 或延后重试' },
  { code: 5002, message: '死信记录不存在', desc: '重放 / 丢弃死信时 ID 不存在' },
  { code: 5003, message: '死信已被处置', desc: '终态不可重复处置' },
  { code: 5004, message: '死信重放入队失败', desc: '队列不可用，死信回退 PENDING 可稍后重试' },
  { code: 6201, message: '产品未定义物模型', desc: '命令校验依赖物模型，请先补齐属性 / 服务定义' },
  { code: 6202, message: '属性不可写', desc: 'property_set 的目标属性未标记为可写' },
  { code: 6203, message: '标识符未定义', desc: '命令的 identifier 不在物模型中' },
  { code: 6204, message: '参数不符合物模型', desc: 'params 的类型、范围或必填项校验失败' },
  { code: 6205, message: '命令记录不存在', desc: '命令 ID 不存在或不属于当前账号' },
  { code: 6206, message: '影子期望值非法', desc: 'PUT shadow/desired 的 params 必须为非空 JSON 对象' },
  { code: 6208, message: '告警规则配置非法', desc: '窗口、聚合方式、最少样本、来源字段等校验失败' },
  { code: 6209, message: '告警记录不存在', desc: '告警 ID 不存在或不属于当前账号' },
  { code: 6210, message: '告警状态不允许该操作', desc: '仅活动告警可确认 / 恢复 / 指派' },
  { code: 6212, message: '分组不存在', desc: '分组 ID 不存在或已删除' },
  { code: 6216, message: '分组非空', desc: '删除分组前需先移出设备与子分组' },
  { code: 6225, message: '属性时序范围非法', desc: 'startTime / endTime 缺失或跨度超限' },
  { code: 6226, message: '属性未建模', desc: 'identifiers 中包含物模型未定义的属性' },
  { code: 6227, message: '时序桶非法', desc: 'bucket 不在 1m/5m/15m/30m/1h/6h/1d' },
  { code: 6241, message: '场景参数非法', desc: '场景结构、并行块、快照损坏或名称冲突' },
  { code: 6243, message: '场景执行记录不存在', desc: '执行记录 ID 不存在或不属于当前账号' },
  { code: 6246, message: '设备凭据无效', desc: 'HTTP 上报 Basic 凭据缺失 / 错误 / 设备停用' },
  { code: 6247, message: 'messageType 不支持', desc: 'HTTP 上报仅支持 data / heartbeat / lwt' },
  { code: 6248, message: '上报载荷过大', desc: 'HTTP 上报请求体超过 max-payload-bytes' },
  { code: 6268, message: '作用域配置非法', desc: '规则 / 告警的 ALL / DEVICE / GROUP / TAG 字段不一致或越权' },
  { code: 6270, message: '注册批次不存在', desc: '批量注册批次号不存在或不属于当前用户' },
  { code: 6271, message: '批量注册数量非法', desc: '单批注册数量需在 1~1000' },
  { code: 6272, message: 'deviceKey 模板非法', desc: '前缀仅允许字母、数字、中划线、下划线' },
  { code: 6273, message: '激活码非法或已被使用', desc: '激活码不匹配或已消费' },
  { code: 6290, message: '点表不可下发', desc: '仅 EDGE 点表可下发，PLATFORM 由平台自行采集' },
  { code: 6291, message: '无可下发点位', desc: '设备没有启用中的边缘点位' },
  { code: 6292, message: '设备不可下发点表', desc: '设备不存在 / 已禁用 / 协议不支持边缘驱动' },
  { code: 6293, message: 'OTA 目标设备冲突', desc: '目标设备已在其他升级任务中且策略为 REJECT' },
  { code: 6294, message: 'OTA 灰度参数非法', desc: 'batchMode / batchSize / 间隔等不合法' },
  { code: 6295, message: '命令标识符被拒绝', desc: '命中危险命令黑名单或角色不允许' },
  { code: 6296, message: '命令类型被拒绝', desc: '当前角色不允许该命令类型' },
  { code: 6305, message: '看板分享不存在或已失效', desc: '公开大屏链接已停用 / 轮换 / 删除' },
  { code: 6308, message: '分组成员或权限非法', desc: '权限只能是 VIEW / OPERATE，目标用户不可共享' },
  { code: 6309, message: '分组成员不存在', desc: '成员 ID 不属于该分组' },
  { code: 6310, message: '无权访问分组或设备', desc: '共享成员无权访问该分组 / 设备' },
  { code: 6311, message: 'Modbus 从站地址非法', desc: '点位 unitId 需在 0~247，留空继承驱动配置' },
  { code: 6312, message: '场景版本不存在', desc: '版本 ID 不存在或不属于该场景' },
  { code: 6313, message: '审计日志查询条件非法', desc: '时间格式错误或起始时间晚于结束时间' },
  { code: 6314, message: 'HTTP 上报超出频率限制', desc: '设备级固定窗口限流，响应带 429' },
  { code: 6315, message: '幂等键非法', desc: 'Idempotency-Key 长度需 1~128，仅允许可见 ASCII' },
  { code: 6316, message: '设备导入文件非法', desc: 'CSV 格式 / 表头 / 行数 / 大小不合法' },
  { code: 6317, message: 'Payload Codec 配置非法', desc: 'JSON / template / 路径表达式 / 大小不合法，或 version / mode 不支持' }
]

export const webhookEvents = [
  { event: 'device.data', desc: '收到设备属性 / 服务上报', payload: '{ event, deviceKey, deviceName, deviceType, topic, status, payload, timestamp }' },
  { event: 'device.heartbeat', desc: '设备心跳', payload: '{ event, deviceKey, deviceName, deviceType, topic, status, payload, timestamp }' },
  { event: 'device.lwt', desc: '设备遗愿消息', payload: '{ event, deviceKey, deviceName, deviceType, topic, status, payload, timestamp }' },
  { event: 'device.ota', desc: '设备 OTA 进度回传', payload: '{ event, deviceKey, deviceName, deviceType, topic, status, payload, timestamp }' },
  {
    event: 'alert.triggered',
    desc: '告警触发（含抑制窗口内去重后的再次提醒）',
    payload: '{ alertId, ruleId, ruleName, sourceType, severity, identifier, title, triggerValue, triggerCount, firstTriggeredAt, lastTriggeredAt }'
  },
  {
    event: 'alert.recovered',
    desc: '告警恢复',
    payload: '{ alertId, ruleId, ruleName, sourceType, severity, title, triggerCount, recoveredAt }'
  },
  {
    event: 'alert.escalated',
    desc: '告警超时未确认触发升级（含手动指派时勾选「同时通知」）',
    payload: '{ alertId, ruleId, ruleName, sourceType, severity, escalationLevel, assigneeId, title, escalatedAt }'
  }
]
