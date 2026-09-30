import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { subscribeRealtime } from '../realtime'

vi.mock('../axios', () => ({ getToken: () => 'test-token' }))

const encoder = new TextEncoder()
const originalSetTimeout = globalThis.setTimeout
const originalClearTimeout = globalThis.clearTimeout
const originalRandom = Math.random

/** 让出一次宏任务，冲干净当前所有微任务。 */
const flush = () => new Promise((resolve) => originalSetTimeout(resolve, 0))

/**
 * 手写调度器替换全局定时器：
 * 测试里可以精确读取「下一次重连的延迟」，并手动推进，避免真实等待。
 */
function installScheduler() {
  const tasks = []
  let nextId = 1

  globalThis.setTimeout = (fn, delay) => {
    const id = nextId++
    tasks.push({ id, fn, delay })
    return id
  }
  globalThis.clearTimeout = (id) => {
    const index = tasks.findIndex((task) => task.id === id)
    if (index >= 0) tasks.splice(index, 1)
  }

  return {
    tasks,
    pendingDelays: () => tasks.map((task) => task.delay).sort((a, b) => a - b),
    /** 触发延迟最小的那个定时器，并等待其异步链跑完 */
    async runNext() {
      tasks.sort((a, b) => a.delay - b.delay)
      const task = tasks.shift()
      if (!task) return undefined
      task.fn()
      await flush()
      return task
    },
    restore() {
      globalThis.setTimeout = originalSetTimeout
      globalThis.clearTimeout = originalClearTimeout
    }
  }
}

/** 用预置分片构造一个 fetch 响应，分片耗尽后即读到流结束。 */
function streamResponse(chunks) {
  let index = 0
  return {
    ok: true,
    status: 200,
    body: {
      getReader: () => ({
        read: async () =>
          index < chunks.length
            ? { value: encoder.encode(chunks[index++]), done: false }
            : { value: undefined, done: true }
      })
    }
  }
}

describe('api/realtime SSE 客户端', () => {
  let scheduler

  beforeEach(() => {
    scheduler = installScheduler()
    // 固定随机数使退避抖动为 0，便于断言精确延迟
    Math.random = () => 0.5
  })

  afterEach(() => {
    scheduler.restore()
    Math.random = originalRandom
    vi.unstubAllGlobals()
  })

  it('解析完整数据帧并回调业务事件', async () => {
    const onEvent = vi.fn()
    const onStatus = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(streamResponse(['data: {"deviceId":"d1","value":21}\n\n']))
    )

    const client = subscribeRealtime({ onEvent, onStatus })
    await flush()

    expect(onEvent).toHaveBeenCalledTimes(1)
    expect(onEvent).toHaveBeenCalledWith({ deviceId: 'd1', value: 21 })
    expect(onStatus).toHaveBeenCalledWith('connecting')
    expect(onStatus).toHaveBeenCalledWith('open')

    client.close()
  })

  it('携带 Authorization 头请求实时通道', async () => {
    const fetchMock = vi.fn().mockResolvedValue(streamResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const client = subscribeRealtime({})
    await flush()

    expect(fetchMock.mock.calls[0][0]).toBe('/api/realtime/stream')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token')

    client.close()
  })

  it('半帧保留：跨 read() 的不完整帧待补齐后再解析', async () => {
    const onEvent = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(streamResponse(['data: {"device', 'Id":"d1"}\n\n', 'data: {"n":2}\n\n']))
    )

    const client = subscribeRealtime({ onEvent })
    await flush()

    expect(onEvent).toHaveBeenCalledTimes(2)
    expect(onEvent.mock.calls[0][0]).toEqual({ deviceId: 'd1' })
    expect(onEvent.mock.calls[1][0]).toEqual({ n: 2 })

    client.close()
  })

  it('一次到达多帧时按顺序全部解析', async () => {
    const onEvent = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(streamResponse(['data: {"n":1}\n\ndata: {"n":2}\n\ndata: {"n":3}\n\n']))
    )

    const client = subscribeRealtime({ onEvent })
    await flush()

    expect(onEvent.mock.calls.map((call) => call[0].n)).toEqual([1, 2, 3])

    client.close()
  })

  it('忽略心跳注释帧与不可解析帧', async () => {
    const onEvent = vi.fn()
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(streamResponse([':\n\n', ': keep-alive\n\n', 'data: not-json\n\n', 'data: {"ok":1}\n\n']))
    )

    const client = subscribeRealtime({ onEvent })
    await flush()

    expect(onEvent).toHaveBeenCalledTimes(1)
    expect(onEvent).toHaveBeenCalledWith({ ok: 1 })

    client.close()
  })

  it('连接失败时按指数退避重连并有上限', async () => {
    const onError = vi.fn()
    const onStatus = vi.fn()
    const fetchMock = vi.fn().mockRejectedValue(new Error('boom'))
    vi.stubGlobal('fetch', fetchMock)

    const client = subscribeRealtime({ onError, onStatus })
    await flush()

    const delays = []
    for (let i = 0; i < 6; i += 1) {
      delays.push(scheduler.pendingDelays()[0])
      await scheduler.runNext()
    }

    expect(delays).toEqual([1000, 2000, 4000, 8000, 16000, 30000])
    expect(onError).toHaveBeenCalledTimes(7)
    expect(onStatus).toHaveBeenLastCalledWith('reconnecting')

    client.close()
  })

  it('建立连接后重置退避计数', async () => {
    const fetchMock = vi.fn()
    fetchMock.mockRejectedValueOnce(new Error('boom'))
    fetchMock.mockResolvedValue(streamResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const client = subscribeRealtime({})
    await flush()

    // 首次连接失败 → 1s
    expect(scheduler.pendingDelays()[0]).toBe(1000)
    await scheduler.runNext()

    // 第二次成功建立连接后服务端随即结束流：退避已重置，故仍是 1s 而非 2s
    expect(scheduler.pendingDelays()[0]).toBe(1000)

    client.close()
  })

  it('心跳超时主动断开重连，且不上报为错误', async () => {
    let rejectRead
    let chunkSent = false
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        body: {
          getReader: () => ({
            read: () => {
              if (!chunkSent) {
                chunkSent = true
                return Promise.resolve({ value: encoder.encode('data: {"n":1}\n\n'), done: false })
              }
              return new Promise((_resolve, reject) => {
                rejectRead = reject
              })
            }
          })
        }
      })
    )

    const onError = vi.fn()
    const onEvent = vi.fn()
    const client = subscribeRealtime({ onEvent, onError })
    await flush()

    expect(onEvent).toHaveBeenCalledTimes(1)
    // 心跳计时器已就位
    expect(scheduler.pendingDelays()).toContain(45000)

    await scheduler.runNext()

    // 模拟 fetch 在 abort 后让挂起的 read() 抛出 AbortError
    rejectRead(Object.assign(new Error('aborted'), { name: 'AbortError' }))
    await flush()

    expect(onError).not.toHaveBeenCalled()
    expect(scheduler.pendingDelays()).toContain(1000)

    client.close()
  })

  it('close() 后清理定时器且不再重连', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('boom'))
    vi.stubGlobal('fetch', fetchMock)

    const client = subscribeRealtime({})
    await flush()
    expect(scheduler.tasks).toHaveLength(1)

    client.close()

    expect(scheduler.tasks).toHaveLength(0)
    await flush()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
