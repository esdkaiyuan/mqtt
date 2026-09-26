import { useMotionStore } from '../stores/motion'

class WebSocketService {
  constructor() {
    this.ws = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectDelay = 3000
    this.store = null
  }

  connect(url = 'ws://localhost:8080/ws') {
    if (!this.store) {
      this.store = useMotionStore()
    }

    try {
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        console.log('WebSocket connected')
        this.reconnectAttempts = 0
        this.store.setConnectionStatus(true)
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (error) {
          console.error('Error parsing WebSocket message:', error)
        }
      }

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error)
        this.store.setConnectionStatus(false)
      }

      this.ws.onclose = () => {
        console.log('WebSocket disconnected')
        this.store.setConnectionStatus(false)
        this.attemptReconnect(url)
      }
    } catch (error) {
      console.error('Error creating WebSocket:', error)
      this.attemptReconnect(url)
    }
  }

  handleMessage(data) {
    if (!this.store) return

    switch (data.type) {
      case 'sensor_data':
        this.store.addDataPoint({
          timestamp: data.timestamp || Date.now(),
          ax: data.ax || 0,
          ay: data.ay || 0,
          az: data.az || 0,
          gx: data.gx || 0,
          gy: data.gy || 0,
          gz: data.gz || 0
        })
        break

      case 'fall_detected':
        this.store.addFallEvent({
          type: data.fall_type || 'unknown',
          confidence: data.confidence || 0,
          ax: data.ax,
          ay: data.ay,
          az: data.az,
          gx: data.gx,
          gy: data.gy,
          gz: data.gz
        })
        break

      case 'device_info':
        this.store.setDeviceInfo({
          deviceId: data.device_id,
          firmwareVersion: data.firmware_version,
          batteryLevel: data.battery_level
        })
        break

      default:
        console.log('Unknown message type:', data.type)
    }
  }

  attemptReconnect(url) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

      setTimeout(() => {
        this.connect(url)
      }, this.reconnectDelay)
    } else {
      console.log('Max reconnection attempts reached')
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }
}

export const wsService = new WebSocketService()
