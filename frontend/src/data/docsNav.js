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
    group: '设备接入',
    items: [
      { id: 'identity', title: '设备身份', path: '/docs/device/identity' },
      { id: 'mqtt', title: 'MQTT 连接参数', path: '/docs/device/mqtt' },
      { id: 'topic-acl', title: 'Topic 与 ACL', path: '/docs/device/topic-acl' },
      { id: 'esp32', title: 'ESP32 接入示例', path: '/docs/device/esp32' }
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
  return docsNav.find((chapter) =>
    chapter.items.some((item) => path === item.path)
  ) || null
}