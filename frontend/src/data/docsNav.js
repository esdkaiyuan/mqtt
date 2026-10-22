/**
 * 文档站目录树单一数据源。
 * 每个 group 对应一个章节页面，group.items 对应该页面的锚点小节。
 */
export const docsNav = [
  {
    group: '快速开始',
    items: [
      { id: 'overview', title: '平台简介', path: '/docs/getting-started/overview' },
      { id: 'workbench', title: '登录与工作台', path: '/docs/getting-started/workbench' },
      { id: 'first-device', title: '接入第一个设备', path: '/docs/getting-started/first-device' }
    ]
  },
  {
    group: '功能指南',
    items: [
      { id: 'devices', title: '设备与物模型', path: '/docs/features/devices' },
      { id: 'automation', title: '规则、告警与场景', path: '/docs/features/automation' },
      { id: 'operations', title: '运维与可视化', path: '/docs/features/operations' },
      { id: 'access', title: '权限与开放接入', path: '/docs/features/access' }
    ]
  },
  {
    group: '设备接入',
    items: [
      { id: 'identity', title: '设备身份', path: '/docs/device/identity' },
      { id: 'mqtt', title: 'MQTT 连接参数', path: '/docs/device/mqtt' },
      { id: 'http', title: 'HTTP 上报', path: '/docs/device/http' },
      { id: 'topic-acl', title: 'Topic 与 ACL', path: '/docs/device/topic-acl' }
    ]
  },
  {
    group: '接入示例',
    items: [
      { id: 'general', title: '通用接入流程', path: '/docs/examples/general' },
      { id: 'esp32', title: 'ESP32 / ESP8266', path: '/docs/examples/esp32' },
      { id: 'stm32', title: 'STM32 / 裸机 MCU', path: '/docs/examples/stm32' },
      { id: 'linux', title: 'Linux / 树莓派', path: '/docs/examples/linux' },
      { id: 'cellular', title: '4G Cat.1 / NB-IoT', path: '/docs/examples/cellular' }
    ]
  },
  {
    group: '上位机开发',
    items: [
      { id: 'qt', title: 'Qt 快速上位机', path: '/docs/hmi/qt' },
      { id: 'api', title: '通讯与代码生成', path: '/docs/hmi/api' },
      { id: 'deploy', title: '打包与部署', path: '/docs/hmi/deploy' }
    ]
  },
  {
    group: '协议规范',
    items: [
      { id: 'd1', title: 'MQTT 主题与 ACL', path: '/docs/protocol/d1' },
      { id: 'd2', title: '设备接入协议', path: '/docs/protocol/d2' }
    ]
  },
  {
    group: '消息与数据',
    items: [
      { id: 'realtime', title: '实时消息（SSE）', path: '/docs/data/realtime' },
      { id: 'history', title: '历史数据查询', path: '/docs/data/history' },
      { id: 'format', title: '数据格式约定', path: '/docs/data/format' }
    ]
  },
  {
    group: '开放 API',
    items: [
      { id: 'api-keys', title: 'API 密钥', path: '/docs/api/api-keys' },
      { id: 'rest', title: 'REST API 参考', path: '/docs/api/rest' },
      { id: 'webhook', title: 'Webhook 回调', path: '/docs/api/webhook' },
      { id: 'errors', title: '错误码', path: '/docs/api/errors' }
    ]
  },
  {
    group: '平台运维',
    items: [
      { id: 'deployment', title: '部署', path: '/docs/ops/deployment' },
      { id: 'monitoring', title: '监控与巡检', path: '/docs/ops/monitoring' },
      { id: 'faq', title: '常见问题', path: '/docs/ops/faq' }
    ]
  }
]

/** 依据当前路径解析所属章节（group + items），供正文页与右侧目录复用 */
export function resolveChapter(path) {
  const normalized = path.replace(/\/+$/, '')
  return docsNav.find((chapter) =>
    chapter.items.some((item) => {
      if (item.path === normalized) return true
      // /docs/device、/docs/features 等省略 section 的入口仍归属对应章节
      return item.path.replace(/\/[^/]+$/, '') === normalized
    })
  ) || null
}