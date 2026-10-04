/**
 * 开放 API 端点单一数据源。
 * 供文档站「开放 API」章节与工作台内引用复用，避免多处维护。
 * 以实际后端接口为准，路径统一以 /api 为前缀。
 */
export const endpointGroups = [
  {
    title: '设备管理',
    endpoints: [
      {
        method: 'GET',
        path: '/external/v1/devices',
        desc: '获取所有设备列表，或通过设备标识查询单个设备',
        example: `curl -X GET \\
  https://your-server/api/external/v1/devices \\
  -H "X-API-Key: your-api-key"`,
        response: `{
  "code": 200,
  "data": [
    {
      "id": 1,
      "deviceKey": "sensor-temp-001",
      "deviceName": "温度传感器-01",
      "deviceType": "sensor",
      "topic": "device/sensor-temp-001/data",
      "status": "ONLINE",
      "lastSeen": "2026-08-11T10:00:00"
    }
  ]
}`
      },
      {
        method: 'GET',
        path: '/external/v1/devices/{deviceKey}',
        desc: '获取指定设备的详细信息',
        example: `curl -X GET \\
  https://your-server/api/external/v1/devices/sensor-temp-001 \\
  -H "X-API-Key: your-api-key"`,
        response: `{
  "code": 200,
  "data": {
    "id": 1,
    "deviceKey": "sensor-temp-001",
    "deviceName": "温度传感器-01",
    "deviceType": "sensor",
    "status": "ONLINE",
    "lastSeen": "2026-08-11T10:00:00",
    "description": "客厅温度传感器",
    "metadata": {"unit": "°C", "min": -20, "max": 80}
  }
}`
      },
      {
        method: 'GET',
        path: '/external/v1/devices/{deviceKey}/status',
        desc: '获取设备的在线状态',
        example: `curl -X GET \\
  https://your-server/api/external/v1/devices/sensor-temp-001/status \\
  -H "X-API-Key: your-api-key"`,
        response: `{
  "code": 200,
  "data": {
    "deviceKey": "sensor-temp-001",
    "status": "ONLINE",
    "lastSeen": "2026-08-11T10:00:00",
    "online": true
  }
}`
      }
    ]
  },
  {
    title: '设备控制',
    endpoints: [
      {
        method: 'POST',
        path: '/external/v1/devices/{deviceKey}/command',
        desc: '按物模型校验后向设备下发命令，MQTT 发布到 device/{deviceKey}/cmd/down',
        example: `curl -X POST \\
  https://your-server/api/external/v1/devices/switch-001/command \\
  -H "X-API-Key: your-api-key" \\
  -H "Content-Type: application/json" \\
  -d '{"type": "property_set", "params": {"power": true}, "callType": "async"}'`,
        response: `{
  "code": 200,
  "data": {
    "commandId": "8f1c0b6e-...-a1",
    "commandType": "property_set",
    "status": "SENT",
    "callType": "async"
  }
}`
      },
      {
        method: 'GET',
        path: '/external/v1/devices/{deviceKey}/commands',
        desc: '命令记录分页，按创建时间倒序',
        example: `curl -X GET \\
  https://your-server/api/external/v1/devices/switch-001/commands?page=1&size=10 \\
  -H "X-API-Key: your-api-key"`,
        response: `{
  "code": 200,
  "data": {
    "records": [
      { "commandId": "8f1c0b6e-...-a1", "commandType": "property_set", "status": "ACKED" }
    ],
    "total": 1
  }
}`
      }
    ]
  },
  {
    title: '统计信息',
    endpoints: [
      {
        method: 'GET',
        path: '/external/v1/stats',
        desc: '获取平台统计信息',
        example: `curl -X GET \\
  https://your-server/api/external/v1/stats \\
  -H "X-API-Key: your-api-key"`,
        response: `{
  "code": 200,
  "data": {
    "totalDevices": 3,
    "onlineDevices": 1,
    "apiKeyName": "MyApp Key",
    "permissions": ["device:read", "device:write"]
  }
}`
      }
    ]
  },
  {
    title: '设备接入（HTTP 上报）',
    endpoints: [
      {
        method: 'POST',
        path: '/ingest/{deviceKey}/{messageType}',
        desc: '设备 HTTP 上报入口。使用 HTTP Basic 鉴权，用户名为 {productKey}.{deviceKey}、密码为 device_secret；messageType 取值 data / heartbeat / lwt，其它值返回 400（6247）。请求体为设备原始载荷，后端不做预解析，与 MQTT 上报同分片、同顺序，属性最新值等价。',
        example: `curl -X POST \\
  https://your-server/api/ingest/sensor-temp-001/data \\
  -u "esp32-fall-detect.sensor-temp-001:<device_secret>" \\
  -H "Content-Type: application/json" \\
  -d '{"method":"thing.event.property.post","params":{"temperature":26.5}}'`,
        response: `{
  "code": 200,
  "message": "成功",
  "data": {
    "deviceKey": "sensor-temp-001",
    "messageType": "data",
    "topic": "device/sensor-temp-001/data",
    "receivedAt": "2026-10-04T12:00:00",
    "accepted": true
  }
}`
      }
    ]
  }
]

export const errorCodes = [
  { code: 200, message: '成功', desc: '请求已正确处理' },
  { code: 400, message: '参数错误', desc: '请求参数缺失或格式不合法' },
  { code: 401, message: '未认证', desc: '缺少或无效的 X-API-Key / Bearer Token' },
  { code: 403, message: '无权限', desc: '密钥权限范围不包含该操作' },
  { code: 404, message: '资源不存在', desc: '设备标识或路径不存在' },
  { code: 429, message: '请求过于频繁', desc: '超出密钥的调用频率限制' },
  { code: 500, message: '服务端错误', desc: '服务内部异常，请稍后重试或联系管理员' },
  { code: 6246, message: '设备凭据无效', desc: 'HTTP 上报 Basic 凭据缺失、格式错误、设备不存在/停用/禁用或密钥错误（HTTP 401）' },
  { code: 6247, message: 'messageType 不支持', desc: 'HTTP 上报 messageType 不在 data / heartbeat / lwt 白名单（HTTP 400）' },
  { code: 6248, message: '上报载荷过大', desc: 'HTTP 上报请求体超过 max-payload-bytes 限制（HTTP 413）' }
]

export const webhookEvents = [
  { event: 'device.online', desc: '设备上线', payload: '{ deviceKey, status, lastSeen }' },
  { event: 'device.offline', desc: '设备离线', payload: '{ deviceKey, status, lastSeen }' },
  { event: 'device.created', desc: '设备创建', payload: '{ deviceKey, deviceName, deviceType }' },
  { event: 'message.received', desc: '收到设备上报消息', payload: '{ deviceKey, topic, payload, timestamp }' },
  {
    event: 'alert.triggered',
    desc: '告警触发（含抑制窗口内去重后的再次提醒）',
    payload: '{ alertId, ruleId, ruleName, sourceType, severity, identifier, title, triggerValue, triggerCount, firstTriggeredAt, lastTriggeredAt }'
  },
  {
    event: 'alert.recovered',
    desc: '告警恢复',
    payload: '{ alertId, ruleId, ruleName, sourceType, severity, title, triggerCount, recoveredAt }'
  }
]