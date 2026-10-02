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
        desc: '向设备发送控制指令，通过 MQTT 发布到设备的 command Topic',
        example: `curl -X POST \\
  https://your-server/api/external/v1/devices/switch-001/command \\
  -H "X-API-Key: your-api-key" \\
  -H "Content-Type: application/json" \\
  -d '{"payload": "{\\"action\\": \\"turn_on\\"}", "qos": 1}'`,
        response: `{
  "code": 200,
  "data": "指令发送成功"
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
  }
]

export const errorCodes = [
  { code: 200, message: '成功', desc: '请求已正确处理' },
  { code: 400, message: '参数错误', desc: '请求参数缺失或格式不合法' },
  { code: 401, message: '未认证', desc: '缺少或无效的 X-API-Key / Bearer Token' },
  { code: 403, message: '无权限', desc: '密钥权限范围不包含该操作' },
  { code: 404, message: '资源不存在', desc: '设备标识或路径不存在' },
  { code: 429, message: '请求过于频繁', desc: '超出密钥的调用频率限制' },
  { code: 500, message: '服务端错误', desc: '服务内部异常，请稍后重试或联系管理员' }
]

export const webhookEvents = [
  { event: 'device.online', desc: '设备上线', payload: '{ deviceKey, status, lastSeen }' },
  { event: 'device.offline', desc: '设备离线', payload: '{ deviceKey, status, lastSeen }' },
  { event: 'device.created', desc: '设备创建', payload: '{ deviceKey, deviceName, deviceType }' },
  { event: 'message.received', desc: '收到设备上报消息', payload: '{ deviceKey, topic, payload, timestamp }' }
]