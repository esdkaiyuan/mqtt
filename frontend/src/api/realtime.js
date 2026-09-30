import { getToken } from './axios'

/** 重连退避基数（毫秒），序列 1s → 2s → 4s → 8s → … */
const RECONNECT_BASE_DELAY = 1000
/** 退避上限（毫秒） */
const RECONNECT_MAX_DELAY = 30000
/** 心跳超时（毫秒）：后端心跳间隔 15s，客户端连续 3 倍间隔未收到任何字节即判定链路已死 */
const HEARTBEAT_TIMEOUT = 45000

/**
 * 订阅后端实时数据流。
 * 使用 fetch + ReadableStream 而非 EventSource，以便携带 Authorization 头，
 * 避免 token 出现在 URL 与访问日志中。
 *
 * 具备以下健壮性能力：
 * - 指数退避重连（1s → 2s → 4s → … 上限 30s，含 ±20% 抖动避免惊群）
 * - 心跳超时判定（45s 未收到任何字节则主动断开并重连，穿透「TCP 存活但链路已死」）
 * - 半帧保留（按 `\n\n` 切帧，跨 read() 的不完整帧留在 buffer 中）
 *
 * @param {object} [options]
 * @param {(event: object) => void} [options.onEvent] 收到业务事件时回调
 * @param {(error: Error) => void} [options.onError] 单次连接失败时回调（重连仍在继续）
 * @param {() => void} [options.onOpen] 每次连接建立时回调
 * @param {(status: 'connecting'|'open'|'reconnecting'|'closed') => void} [options.onStatus] 连接状态变化回调
 * @returns {{ close: () => void }}
 */
export function subscribeRealtime({ onEvent, onError, onOpen, onStatus } = {}) {
  let closed = false
  let hasConnectedOnce = false
  let reconnectAttempts = 0
  let reconnectTimer = null
  let heartbeatTimer = null
  let controller = null

  const emitStatus = (status) => {
    if (onStatus) onStatus(status)
  }

  const clearHeartbeat = () => {
    if (heartbeatTimer !== null) {
      clearTimeout(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  // 每收到一点字节就重置计时；超时则主动 abort，交由统一的重连逻辑处理。
  const resetHeartbeat = () => {
    clearHeartbeat()
    heartbeatTimer = setTimeout(() => {
      if (controller) controller.abort()
    }, HEARTBEAT_TIMEOUT)
  }

  const scheduleReconnect = () => {
    if (closed) return
    emitStatus('reconnecting')

    const exp = Math.min(RECONNECT_BASE_DELAY * 2 ** reconnectAttempts, RECONNECT_MAX_DELAY)
    // ±20% 抖动，避免多客户端在同一时刻集中重连
    const jitter = exp * 0.2 * (Math.random() * 2 - 1)
    const delay = Math.max(0, Math.round(exp + jitter))
    reconnectAttempts += 1

    reconnectTimer = setTimeout(run, delay)
  }

  const run = async () => {
    if (closed) return
    clearHeartbeat()
    controller = new AbortController()

    emitStatus(reconnectAttempts === 0 && !hasConnectedOnce ? 'connecting' : 'reconnecting')

    try {
      const response = await fetch('/api/realtime/stream', {
        method: 'GET',
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${getToken()}`
        },
        signal: controller.signal
      })
      if (!response.ok || !response.body) {
        throw new Error(`实时通道连接失败: HTTP ${response.status}`)
      }

      // 连接建立：重置退避计数并开始心跳计时
      hasConnectedOnce = true
      reconnectAttempts = 0
      emitStatus('open')
      if (onOpen) onOpen()
      resetHeartbeat()

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        resetHeartbeat()
        buffer += decoder.decode(value, { stream: true })

        // 仅在完整帧（以空行结尾）到来时解析，半帧保留在 buffer 中等待后续字节
        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const chunk = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          handleChunk(chunk, onEvent)
          boundary = buffer.indexOf('\n\n')
        }
      }

      // 服务端主动结束流（非 close() 触发）→ 视为断线，进入重连
      if (!closed) scheduleReconnect()
    } catch (error) {
      if (closed) return
      // AbortError 可能来自心跳超时主动断开，此时同样需要重连，故不跳过
      if (error.name !== 'AbortError' && onError) onError(error)
      scheduleReconnect()
    }
  }

  run()

  return {
    close() {
      if (closed) return
      closed = true
      clearHeartbeat()
      if (reconnectTimer !== null) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      if (controller) controller.abort()
      emitStatus('closed')
    }
  }
}

function handleChunk(chunk, onEvent) {
  const dataLines = chunk
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  // 心跳注释帧（以 `:` 开头）不含 data: 行，此处直接忽略
  if (dataLines.length === 0 || !onEvent) return
  try {
    onEvent(JSON.parse(dataLines.join('\n')))
  } catch (e) {
    // 忽略无法解析的心跳或注释帧
  }
}
