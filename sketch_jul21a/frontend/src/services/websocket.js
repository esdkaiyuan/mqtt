import { useMotionStore } from '../stores/motion'

export const DEFAULT_DEVICE_ID = 'ESP32_001'

function resolveBaseUrl() {
  const configured = import.meta.env.VITE_WS_BASE
  if (configured) {
    return configured.replace(/\/$/, '')
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}`
}

class WebSocketService {
  constructor() {
    this.ws = null
    this.deviceId = DEFAULT_DEVICE_ID
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.store = null
    this.manualClose = false
  }

  buildUrl(deviceId) {
    return `${resolveBaseUrl()}/ws/view/${encodeURIComponent(deviceId)}`
  }

  connect(deviceId = DEFAULT_DEVICE_ID) {
    if (!this.store) {
      this.store = useMotionStore()
    }

    this.disconnect()
    this.manualClose = false
    this.deviceId = deviceId

    const url = this.buildUrl(deviceId)

    try {
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        this.reconnectAttempts = 0
        this.store.setConnectionStatus(true)
      }

      this.ws.onmessage = (event) => {
        try {
          this.handleMessage(JSON.parse(event.data))
        } catch (error) {
          console.error('Error parsing WebSocket message:', error)
        }
      }

      this.ws.onerror = () => {
        this.store.setConnectionStatus(false)
      }

      this.ws.onclose = () => {
        this.store.setConnectionStatus(false)
        if (!this.manualClose) {
          this.attemptReconnect()
        }
      }
    } catch (error) {
      console.error('Error creating WebSocket:', error)
      this.store.setConnectionStatus(false)
      this.attemptReconnect()
    }
  }

  handleMessage(data) {
    if (!this.store || !data || !data.type) return

    switch (data.type) {
      case 'sensor_data':
        // Backend pushes whole batches; each sample carries an ISO timestamp.
        for (const sample of data.data || []) {
          this.store.addDataPoint({
            timestamp: Date.parse(sample.timestamp),
            ax: sample.ax ?? 0,
            ay: sample.ay ?? 0,
            az: sample.az ?? 0,
            gx: sample.gx ?? 0,
            gy: sample.gy ?? 0,
            gz: sample.gz ?? 0
          })
        }
        break

      case 'fall_detected':
        this.store.addFallEvent({
          type: data.fall_type || 'unknown',
          confidence: data.confidence ?? 0,
          timestamp: data.timestamp
        })
        break

      case 'device_info':
        this.store.setDeviceInfo({
          deviceId: data.device_id,
          online: Boolean(data.online)
        })
        break

      default:
        break
    }
  }

  attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      return
    }
    this.reconnectAttempts++
    setTimeout(() => {
      if (!this.manualClose) {
        this.connect(this.deviceId)
      }
    }, this.reconnectDelay)
  }

  disconnect() {
    if (this.ws) {
      this.manualClose = true
      this.ws.onclose = null
      this.ws.close()
      this.ws = null
      if (this.store) {
        this.store.setConnectionStatus(false)
      }
    }
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }
}

export const wsService = new WebSocketService()