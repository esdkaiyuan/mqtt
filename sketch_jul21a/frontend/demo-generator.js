// Demo data generator for testing the frontend
// Run this in browser console to simulate real-time data

class DemoDataGenerator {
  constructor() {
    this.isRunning = false
    this.interval = null
    this.ws = null
    this.time = 0
    this.fallProbability = 0.001 // 0.1% chance of fall per update
  }

  connect(url = 'ws://localhost:8080/ws') {
    try {
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        console.log('Demo generator connected to WebSocket')
        this.sendDeviceInfo()
      }

      this.ws.onerror = (error) => {
        console.log('WebSocket not available, using direct store update')
        this.startDirectMode()
      }
    } catch (error) {
      console.log('WebSocket not available, using direct store update')
      this.startDirectMode()
    }
  }

  startDirectMode() {
    // Directly update the store if WebSocket is not available
    this.isRunning = true
    console.log('Starting demo data generator in direct mode...')
    console.log('Use generator.start() to begin, generator.stop() to stop')
  }

  sendDeviceInfo() {
    const info = {
      type: 'device_info',
      device_id: 'ESP32_DEMO_001',
      firmware_version: '1.0.0',
      battery_level: 85
    }
    this.ws.send(JSON.stringify(info))
  }

  generateSensorData() {
    this.time += 0.02 // 20ms interval (50Hz)

    // Generate realistic sensor data with some noise and patterns
    const baseAccel = {
      ax: Math.sin(this.time * 0.5) * 0.1 + (Math.random() - 0.5) * 0.05,
      ay: Math.cos(this.time * 0.3) * 0.08 + (Math.random() - 0.5) * 0.04,
      az: 1.0 + Math.sin(this.time * 0.2) * 0.02 + (Math.random() - 0.5) * 0.02
    }

    const baseGyro = {
      gx: Math.sin(this.time * 0.4) * 2 + (Math.random() - 0.5) * 1,
      gy: Math.cos(this.time * 0.6) * 1.5 + (Math.random() - 0.5) * 0.8,
      gz: Math.sin(this.time * 0.8) * 1 + (Math.random() - 0.5) * 0.5
    }

    // Occasionally simulate a fall event
    if (Math.random() < this.fallProbability) {
      return this.generateFallData()
    }

    return {
      type: 'sensor_data',
      timestamp: Date.now(),
      ...baseAccel,
      ...baseGyro
    }
  }

  generateFallData() {
    console.log('Simulating fall event...')

    // Simulate fall characteristics
    const fallTypes = ['forward_fall', 'backward_fall', 'side_fall']
    const fallType = fallTypes[Math.floor(Math.random() * fallTypes.length)]

    const fallData = {
      type: 'sensor_data',
      timestamp: Date.now(),
      ax: (Math.random() - 0.5) * 4 + 2, // High acceleration
      ay: (Math.random() - 0.5) * 3,
      az: Math.random() * 2 + 0.5,
      gx: (Math.random() - 0.5) * 100 + 50, // High angular velocity
      gy: (Math.random() - 0.5) * 80,
      gz: (Math.random() - 0.5) * 60
    }

    // Send fall event notification
    setTimeout(() => {
      const fallEvent = {
        type: 'fall_detected',
        fall_type: fallType,
        confidence: 0.85 + Math.random() * 0.15,
        ...fallData
      }

      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(fallEvent))
      }
    }, 100)

    return fallData
  }

  start() {
    if (this.isRunning) {
      console.log('Generator is already running')
      return
    }

    this.isRunning = true
    console.log('Starting demo data generator...')

    this.interval = setInterval(() => {
      const data = this.generateSensorData()

      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(data))
      } else {
        // Update store directly
        if (window.__pinia) {
          const motionStore = window.__pinia._s.get('motion')
          if (motionStore) {
            motionStore.addDataPoint(data)
          }
        }
      }
    }, 20) // 50Hz update rate

    console.log('Demo data generator started')
    console.log('Use generator.stop() to stop')
  }

  stop() {
    if (!this.isRunning) {
      console.log('Generator is not running')
      return
    }

    this.isRunning = false
    if (this.interval) {
      clearInterval(this.interval)
      this.interval = null
    }
    console.log('Demo data generator stopped')
  }

  disconnect() {
    this.stop()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }
}

// Create global instance
window.generator = new DemoDataGenerator()

console.log('Demo data generator loaded!')
console.log('Usage:')
console.log('  generator.connect()  - Connect to WebSocket')
console.log('  generator.start()    - Start generating data')
console.log('  generator.stop()     - Stop generating data')
console.log('  generator.disconnect() - Disconnect and cleanup')
