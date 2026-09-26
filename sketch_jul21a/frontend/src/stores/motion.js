import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useMotionStore = defineStore('motion', () => {
  // Connection state
  const isConnected = ref(false)
  const deviceInfo = ref({
    deviceId: '',
    firmwareVersion: '',
    batteryLevel: 100
  })

  // Real-time data (latest 10 seconds at 50Hz = 500 points)
  const realtimeData = ref({
    timestamps: [],
    ax: [], ay: [], az: [],
    gx: [], gy: [], gz: []
  })

  // History data for annotation
  const historyData = ref([])

  // Fall events
  const fallEvents = ref([])

  // Statistics
  const stats = ref({
    totalSamples: 0,
    totalFalls: 0,
    todaySamples: 0,
    todayFalls: 0,
    lastFallTime: null
  })

  // Annotations
  const annotations = ref([])

  // Max data points for realtime (10 seconds at 50Hz)
  const MAX_POINTS = 500

  // Computed
  const latestData = computed(() => {
    if (realtimeData.value.timestamps.length === 0) {
      return { ax: 0, ay: 0, az: 0, gx: 0, gy: 0, gz: 0, timestamp: Date.now() }
    }
    const len = realtimeData.value.timestamps.length
    return {
      ax: realtimeData.value.ax[len - 1],
      ay: realtimeData.value.ay[len - 1],
      az: realtimeData.value.az[len - 1],
      gx: realtimeData.value.gx[len - 1],
      gy: realtimeData.value.gy[len - 1],
      gz: realtimeData.value.gz[len - 1],
      timestamp: realtimeData.value.timestamps[len - 1]
    }
  })

  // Actions
  function addDataPoint(data) {
    const { timestamp, ax, ay, az, gx, gy, gz } = data

    realtimeData.value.timestamps.push(timestamp)
    realtimeData.value.ax.push(ax)
    realtimeData.value.ay.push(ay)
    realtimeData.value.az.push(az)
    realtimeData.value.gx.push(gx)
    realtimeData.value.gy.push(gy)
    realtimeData.value.gz.push(gz)

    // Keep only MAX_POINTS
    if (realtimeData.value.timestamps.length > MAX_POINTS) {
      realtimeData.value.timestamps.shift()
      realtimeData.value.ax.shift()
      realtimeData.value.ay.shift()
      realtimeData.value.az.shift()
      realtimeData.value.gx.shift()
      realtimeData.value.gy.shift()
      realtimeData.value.gz.shift()
    }

    // Add to history
    historyData.value.push(data)
    if (historyData.value.length > 10000) {
      historyData.value = historyData.value.slice(-10000)
    }

    // Update stats
    stats.value.totalSamples++
    stats.value.todaySamples++
  }

  function addFallEvent(event) {
    fallEvents.value.unshift({
      id: Date.now(),
      ...event,
      timestamp: new Date().toISOString()
    })

    // Keep only last 100 events
    if (fallEvents.value.length > 100) {
      fallEvents.value = fallEvents.value.slice(0, 100)
    }

    stats.value.totalFalls++
    stats.value.todayFalls++
    stats.value.lastFallTime = new Date().toISOString()
  }

  function setConnectionStatus(status) {
    isConnected.value = status
  }

  function setDeviceInfo(info) {
    deviceInfo.value = { ...deviceInfo.value, ...info }
  }

  function addAnnotation(annotation) {
    annotations.value.push({
      id: Date.now(),
      ...annotation,
      createdAt: new Date().toISOString()
    })
  }

  function updateAnnotation(id, updates) {
    const index = annotations.value.findIndex(a => a.id === id)
    if (index !== -1) {
      annotations.value[index] = { ...annotations.value[index], ...updates }
    }
  }

  function deleteAnnotation(id) {
    annotations.value = annotations.value.filter(a => a.id !== id)
  }

  function clearRealtimeData() {
    realtimeData.value = {
      timestamps: [],
      ax: [], ay: [], az: [],
      gx: [], gy: [], gz: []
    }
  }

  function resetStats() {
    stats.value = {
      totalSamples: 0,
      totalFalls: 0,
      todaySamples: 0,
      todayFalls: 0,
      lastFallTime: null
    }
  }

  return {
    // State
    isConnected,
    deviceInfo,
    realtimeData,
    historyData,
    fallEvents,
    stats,
    annotations,

    // Computed
    latestData,

    // Actions
    addDataPoint,
    addFallEvent,
    setConnectionStatus,
    setDeviceInfo,
    addAnnotation,
    updateAnnotation,
    deleteAnnotation,
    clearRealtimeData,
    resetStats
  }
})
