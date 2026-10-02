import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { deviceApi } from '@/api/device'
import { subscribeRealtime } from '@/api/realtime'

const STORAGE_NAV_COLLAPSED = 'ui.nav.collapsed'
const STORAGE_RAIL_OPEN = 'ui.rail.open'

/** 全局健康度轮询间隔（毫秒） */
const HEALTH_POLL_INTERVAL = 30000
/** 实时事件环形缓冲上限，供页面消费最近事件 */
const EVENT_BUFFER_LIMIT = 200

function readBool(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : raw === 'true'
  } catch {
    return fallback
  }
}

function writeBool(key, value) {
  try {
    localStorage.setItem(key, value ? 'true' : 'false')
  } catch {
    // localStorage 不可用时静默降级，仅内存生效
  }
}

export const useUiStore = defineStore('ui', () => {
  /* ---------- 布局折叠状态 ---------- */
  const navCollapsed = ref(readBool(STORAGE_NAV_COLLAPSED, false))
  const railOpen = ref(readBool(STORAGE_RAIL_OPEN, true))

  /* ---------- 右上下文栏当前面板与上下文数据 ---------- */
  const railName = ref('')
  const railContext = ref({})

  /* ---------- 页面与右栏共享的筛选条件 ---------- */
  const messageFilter = ref('')

  /* ---------- 响应式视口与抽屉 ---------- */
  // 四档：wide ≥1440 / compact 1024–1439 / narrow 768–1023 / phone <768
  const viewport = ref('wide')
  const isCompact = computed(() => viewport.value === 'compact')
  const isNarrow = computed(() => viewport.value === 'narrow')
  const isPhone = computed(() => viewport.value === 'phone')
  // 左导航转为抽屉：仅手机档
  const navAsDrawer = computed(() => isPhone.value)
  // 右上下文栏转为抽屉：平板及以下
  const railAsDrawer = computed(() => isNarrow.value || isPhone.value)
  const navDrawerOpen = ref(false)
  const railDrawerOpen = ref(false)

  /* ---------- 全局健康度 ---------- */
  const realtimeStatus = ref('closed')
  const brokerStatus = ref('unknown')
  const onlineDevices = ref(0)
  const totalDevices = ref(0)
  const lastRefreshAt = ref(0)
  const version = ref(import.meta.env.VITE_APP_VERSION || 'v1.0.0')

  /* ---------- 实时事件缓冲 ---------- */
  const events = ref([])

  const onlineRate = computed(() => {
    if (!totalDevices.value) return 0
    return Math.round((onlineDevices.value / totalDevices.value) * 100)
  })

  let healthTimer = null
  let realtimeHandle = null
  let eventSeq = 0

  function toggleNav() {
    navCollapsed.value = !navCollapsed.value
    writeBool(STORAGE_NAV_COLLAPSED, navCollapsed.value)
  }

  function setNavCollapsed(value) {
    navCollapsed.value = value
    writeBool(STORAGE_NAV_COLLAPSED, value)
  }

  function toggleRail() {
    railOpen.value = !railOpen.value
    writeBool(STORAGE_RAIL_OPEN, railOpen.value)
  }

  function setRailOpen(value) {
    railOpen.value = value
    writeBool(STORAGE_RAIL_OPEN, value)
  }

  function setRail(name) {
    railName.value = name || ''
  }

  /** 页面把右栏需要的数据写入上下文，右栏面板只读消费，避免跨页耦合。 */
  function setRailContext(context) {
    railContext.value = context || {}
  }

  function setMessageFilter(value) {
    messageFilter.value = value || ''
  }

  /**
   * 切换视口档位。紧凑档（1024–1439）右栏默认收起、按钮唤出；
   * 宽屏档默认展开。档位驱动只改内存值，不覆盖用户持久化偏好。
   */
  function setViewport(tier) {
    const next = tier || 'wide'
    const prev = viewport.value
    viewport.value = next

    if (next !== prev) {
      if (next === 'compact') railOpen.value = false
      else if (next === 'wide') railOpen.value = true
    }

    // 退出对应档位时收起抽屉，避免残留遮挡
    if (!navAsDrawer.value) navDrawerOpen.value = false
    if (!railAsDrawer.value) railDrawerOpen.value = false
  }

  function toggleNavDrawer() {
    navDrawerOpen.value = !navDrawerOpen.value
  }

  function toggleRailDrawer() {
    railDrawerOpen.value = !railDrawerOpen.value
  }

  function setRealtimeStatus(status) {
    realtimeStatus.value = status
  }

  function pushEvent(event) {
    eventSeq += 1
    // 附带单调递增 id，供列表 key 使用；缓冲区前移时 key 仍保持稳定
    events.value.push({ ...event, id: eventSeq })
    if (events.value.length > EVENT_BUFFER_LIMIT) {
      events.value.splice(0, events.value.length - EVENT_BUFFER_LIMIT)
    }
  }

  function clearEvents() {
    events.value = []
  }

  /**
   * 拉取全局健康度：在线设备数与设备总数。
   * 任一请求失败只影响对应字段，不阻塞其余数据更新。
   */
  async function refreshHealth() {
    const [onlineResult, listResult] = await Promise.allSettled([
      deviceApi.getOnline(),
      deviceApi.getList({ page: 1, size: 1 })
    ])

    if (onlineResult.status === 'fulfilled') {
      const list = onlineResult.value?.data || onlineResult.value || []
      onlineDevices.value = Array.isArray(list) ? list.length : 0
      brokerStatus.value = 'ok'
    } else {
      brokerStatus.value = 'down'
    }

    if (listResult.status === 'fulfilled') {
      const page = listResult.value?.data || listResult.value || {}
      totalDevices.value = page.total || 0
    }

    lastRefreshAt.value = Date.now()
  }

  function startHealthPolling() {
    if (healthTimer !== null) return
    refreshHealth()
    healthTimer = setInterval(refreshHealth, HEALTH_POLL_INTERVAL)
  }

  function stopHealthPolling() {
    if (healthTimer === null) return
    clearInterval(healthTimer)
    healthTimer = null
  }

  /**
   * 建立应用级实时通道，状态与事件统一汇聚到本 store。
   * 全站仅此一条 SSE 连接，页面消费 `events`，不再各自开连接。
   */
  function connectRealtime() {
    if (realtimeHandle) return
    realtimeHandle = subscribeRealtime({
      onStatus: setRealtimeStatus,
      onEvent: pushEvent
    })
  }

  function disconnectRealtime() {
    if (!realtimeHandle) return
    realtimeHandle.close()
    realtimeHandle = null
  }

  return {
    navCollapsed,
    railOpen,
    railName,
    railContext,
    messageFilter,
    isNarrow,
    isCompact,
    isPhone,
    navAsDrawer,
    railAsDrawer,
    navDrawerOpen,
    railDrawerOpen,
    realtimeStatus,
    brokerStatus,
    onlineDevices,
    totalDevices,
    lastRefreshAt,
    version,
    events,
    onlineRate,
    toggleNav,
    setNavCollapsed,
    toggleRail,
    setRailOpen,
    setRail,
    setRailContext,
    setMessageFilter,
    setViewport,
    toggleNavDrawer,
    toggleRailDrawer,
    setRealtimeStatus,
    pushEvent,
    clearEvents,
    refreshHealth,
    startHealthPolling,
    stopHealthPolling,
    connectRealtime,
    disconnectRealtime
  }
})