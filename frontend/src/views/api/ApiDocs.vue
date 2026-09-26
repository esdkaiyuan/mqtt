<template>
  <div class="api-docs-page page-container">
    <div class="page-header">
      <h2>API文档</h2>
      <div class="header-actions">
        <router-link to="/settings/api-keys" class="btn-primary">
          <svg-icon name="settings" :size="14" />
          API密钥管理
        </router-link>
      </div>
    </div>

    <div class="docs-overview card">
      <h3>外部API接入指南</h3>
      <p class="docs-intro">
        通过API密钥认证，您的软件可以远程控制设备、获取设备状态、接收数据同步。
        所有外部API请求需要在请求头中包含 <code>X-API-Key</code> 字段。
      </p>
    </div>

    <div class="api-endpoints">
      <div class="endpoint-group" v-for="group in endpointGroups" :key="group.title">
        <h3 class="group-title">{{ group.title }}</h3>
        <div class="endpoint-list">
          <div class="endpoint-card card" v-for="ep in group.endpoints" :key="ep.path">
            <div class="endpoint-header">
              <span :class="['method-badge', ep.method.toLowerCase()]">{{ ep.method }}</span>
              <code class="endpoint-path">{{ ep.path }}</code>
            </div>
            <p class="endpoint-desc">{{ ep.desc }}</p>
            <div v-if="ep.example" class="endpoint-example">
              <div class="example-header">请求示例</div>
              <pre class="example-code">{{ ep.example }}</pre>
            </div>
            <div v-if="ep.response" class="endpoint-example">
              <div class="example-header">响应示例</div>
              <pre class="example-code">{{ ep.response }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'

const endpointGroups = [
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
    "metadata": {"unit":"°C","min":-20,"max":80}
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
        desc: '向设备发送控制指令，通过MQTT发布到设备的command Topic',
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
</script>

<style scoped>
.api-docs-page {
  max-width: 900px;
}

.docs-overview {
  margin-bottom: var(--spacing-2xl);
}

.docs-overview h3 {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
}

.docs-intro {
  font-size: var(--font-size-md);
  color: var(--color-text-regular);
  line-height: 1.8;
}

.docs-intro code {
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: #E8594F;
}

.endpoint-group {
  margin-bottom: var(--spacing-3xl);
}

.group-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-sm);
  border-bottom: 2px solid var(--border-color);
}

.endpoint-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.endpoint-card {
  padding: var(--spacing-lg);
}

.endpoint-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.method-badge {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  font-family: var(--font-family-mono);
}

.method-badge.get {
  background: #E8F3FF;
  color: #165DFF;
}

.method-badge.post {
  background: #E8FFEA;
  color: #00B42A;
}

.method-badge.put {
  background: #FFF3E8;
  color: #FF7D00;
}

.method-badge.delete {
  background: #FFECEC;
  color: #F53F3F;
}

.endpoint-path {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-md);
  color: var(--color-text-primary);
}

.endpoint-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
  margin-bottom: var(--spacing-md);
}

.endpoint-example {
  margin-bottom: var(--spacing-md);
}

.example-header {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-xs);
  font-weight: 500;
}

.example-code {
  background: #1D2129;
  color: #A9E1FF;
  padding: var(--spacing-md);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  overflow-x: auto;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: var(--spacing-md);
}
</style>
