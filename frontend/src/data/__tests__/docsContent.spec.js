import { describe, it, expect } from 'vitest'
import { endpointGroups, errorCodes, webhookEvents } from '@/data/apiEndpoints'
import { docsNav, resolveChapter } from '@/data/docsNav'

describe('apiEndpoints 文档数据', () => {
  it('每个分组都有摘要且端点路径唯一', () => {
    const seen = new Set()
    const keys = endpointGroups.map((group) => group.key)
    expect(new Set(keys).size).toBe(keys.length)
    expect(endpointGroups.length).toBeGreaterThan(0)
    for (const group of endpointGroups) {
      expect(group.key).toBeTruthy()
      expect(group.title).toBeTruthy()
      expect(group.summary).toBeTruthy()
      expect(group.endpoints.length).toBeGreaterThan(0)
      for (const endpoint of group.endpoints) {
        const key = `${endpoint.method} ${endpoint.path}`
        expect(seen.has(key), `重复端点 ${key}`).toBe(false)
        seen.add(key)
        expect(endpoint.desc).toBeTruthy()
        expect(endpoint.auth).toBeTruthy()
        expect(Array.isArray(endpoint.params)).toBe(true)
      }
    }
  })

  it('开放 API 统一位于 /external/v1，HTTP 上报单独走 /ingest', () => {
    for (const endpoint of endpointGroups.flatMap((group) => group.endpoints)) {
      const isIngest = endpoint.path.startsWith('/ingest/')
      if (!isIngest) {
        expect(endpoint.path.startsWith('/external/v1/'), endpoint.path).toBe(true)
      }
    }
  })

  it('错误码与 Webhook 事件不重复且字段完整', () => {
    const codes = errorCodes.map((item) => item.code)
    expect(new Set(codes).size).toBe(codes.length)
    for (const item of errorCodes) {
      expect(item.message).toBeTruthy()
      expect(item.desc).toBeTruthy()
    }

    const events = webhookEvents.map((item) => item.event)
    expect(new Set(events).size).toBe(events.length)
    for (const item of webhookEvents) {
      expect(item.desc).toBeTruthy()
      expect(item.payload).toBeTruthy()
    }
  })
})

describe('docsNav 文档导航', () => {
  it('章节与条目路径唯一并可按路径反查章节', () => {
    const paths = []
    for (const chapter of docsNav) {
      expect(chapter.group).toBeTruthy()
      for (const item of chapter.items) {
        expect(item.path.startsWith('/docs/'), item.path).toBe(true)
        paths.push(item.path)
        expect(resolveChapter(item.path)?.group).toBe(chapter.group)
      }
    }
    expect(new Set(paths).size).toBe(paths.length)
  })

  it('省略 section 的章节入口仍能解析到对应章节', () => {
    expect(resolveChapter('/docs/device')?.group).toBe('设备接入')
    expect(resolveChapter('/docs/features')?.group).toBe('功能指南')
    expect(resolveChapter('/docs/examples')?.group).toBe('接入示例')
    expect(resolveChapter('/docs/hmi')?.group).toBe('上位机开发')
  })

  it('功能指南四个主题齐全', () => {
    const features = docsNav.find((chapter) => chapter.group === '功能指南')
    expect(features).toBeTruthy()
    expect(features.items.map((item) => item.id)).toEqual([
      'devices',
      'automation',
      'operations',
      'access'
    ])
  })

  it('设备接入章节包含 HTTP 上报入口，接入示例与上位机独立成章', () => {
    const devices = docsNav.find((chapter) => chapter.group === '设备接入')
    const examples = docsNav.find((chapter) => chapter.group === '接入示例')
    const hmi = docsNav.find((chapter) => chapter.group === '上位机开发')
    expect(devices).toBeTruthy()
    expect(examples).toBeTruthy()
    expect(hmi).toBeTruthy()
    expect(devices.items.map((item) => item.id)).toEqual([
      'identity',
      'mqtt',
      'http',
      'topic-acl'
    ])
    expect(examples.items.map((item) => item.id)).toEqual([
      'general',
      'esp32',
      'stm32',
      'linux',
      'cellular'
    ])
    expect(hmi.items.map((item) => item.id)).toEqual(['qt', 'api', 'deploy'])
  })
})
