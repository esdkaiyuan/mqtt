import { getToken } from './axios'

/**
 * 订阅后端实时数据流。
 * 使用 fetch + ReadableStream 而非 EventSource，以便携带 Authorization 头，
 * 避免 token 出现在 URL 与访问日志中。
 */
export function subscribeRealtime({ onEvent, onError, onOpen } = {}) {
  const controller = new AbortController()

  const run = async () => {
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
      if (onOpen) onOpen()

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let boundary = buffer.indexOf('\n\n')
        while (boundary !== -1) {
          const chunk = buffer.slice(0, boundary)
          buffer = buffer.slice(boundary + 2)
          handleChunk(chunk, onEvent)
          boundary = buffer.indexOf('\n\n')
        }
      }
    } catch (error) {
      if (error.name !== 'AbortError' && onError) onError(error)
    }
  }

  run()

  return {
    close() {
      controller.abort()
    }
  }
}

function handleChunk(chunk, onEvent) {
  const dataLines = chunk
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  if (dataLines.length === 0 || !onEvent) return
  try {
    onEvent(JSON.parse(dataLines.join('\n')))
  } catch (e) {
    // 忽略无法解析的心跳或注释帧
  }
}