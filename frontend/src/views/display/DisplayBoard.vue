<template>
  <div class="display-page">
    <header class="display-header">
      <div class="display-brand">
        <span class="display-kicker">数据大屏</span>
        <h1 class="display-title">{{ share?.name || '数据大屏' }}</h1>
      </div>
      <div class="display-controls">
        <span class="display-clock">{{ clock }}</span>
        <button
          v-if="dashboards.length > 1"
          type="button"
          class="display-icon-button"
          :title="playing ? '暂停轮播' : '继续轮播'"
          @click="togglePlaying"
        >
          <svg-icon :name="playing ? 'pause' : 'play'" :size="16" />
        </button>
        <button type="button" class="display-icon-button" title="全屏" @click="toggleFullscreen">
          <svg-icon name="fullscreen" :size="16" />
        </button>
      </div>
    </header>

    <main v-if="loading && !share" class="display-state">正在加载大屏...</main>
    <main v-else-if="error || !share" class="display-state">
      <p class="display-state__title">链接无效或已失效</p>
      <p class="display-state__hint">请联系分享创建者重新生成链接</p>
      <el-button size="small" @click="refetch()">重试</el-button>
    </main>

    <template v-else>
      <div class="display-subhead">
        <div class="display-subhead__left">
          <h2 class="display-board-title">{{ currentBoard?.name || '—' }}</h2>
          <span class="display-board-meta">
            {{ dashboards.length }} 个看板 · 每 {{ intervalSeconds }} 秒轮播 · 数据每 60 秒刷新
          </span>
        </div>
        <div v-if="dashboards.length > 1" class="display-dots">
          <button
            v-for="(board, index) in dashboards"
            :key="board.id"
            type="button"
            class="display-dot"
            :class="{ 'display-dot--active': index === activeIndex }"
            :title="board.name"
            @click="selectBoard(index)"
          />
        </div>
      </div>

      <section v-if="panels.length" class="display-grid">
        <BoardPanel
          v-for="(panel, index) in panels"
          :key="panelKey(panel, index)"
          :panel="panel"
          :device-names="share.deviceNames || {}"
          :refresh-interval="60"
          dark
        />
      </section>
      <div v-else class="display-state">
        <p class="display-state__title">该看板还没有面板</p>
        <p class="display-state__hint">请在看板编辑页添加面板后刷新</p>
      </div>
    </template>

    <footer class="display-footer">只读分享 · 大屏数据按轮播自动切换</footer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import SvgIcon from '@/components/Icon.vue'
import BoardPanel from '@/components/dashboard/BoardPanel.vue'
import { dashboardShareApi } from '@/api/dashboardShare'
import { unwrapResult } from '@/utils/result'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))

const shareQuery = useQuery({
  queryKey: computed(() => ['public-dashboard-share', token.value]),
  queryFn: async () => unwrapResult(await dashboardShareApi.publicView(token.value), null),
  enabled: computed(() => Boolean(token.value)),
  retry: false
})

const share = computed(() => shareQuery.data.value ?? null)
const loading = computed(() => shareQuery.isFetching.value)
const error = computed(() => shareQuery.isError.value)
const refetch = shareQuery.refetch

const dashboards = computed(() => share.value?.dashboards ?? [])
const intervalSeconds = computed(() => Math.max(5, share.value?.intervalSeconds || 15))
const activeIndex = ref(0)
const currentBoard = computed(() => dashboards.value[activeIndex.value] ?? null)
const panels = computed(() => currentBoard.value?.config?.panels ?? [])

const playing = ref(true)
const now = ref(Date.now())
let clockTimer = null
let carouselTimer = null

const clock = computed(() =>
  new Date(now.value).toLocaleTimeString('zh-CN', { hour12: false })
)

function panelKey(panel, index) {
  return `${activeIndex.value}-${index}-${JSON.stringify(panel)}`
}

function selectBoard(index) {
  if (index < 0 || index >= dashboards.value.length) return
  activeIndex.value = index
  restartCarousel()
}

function togglePlaying() {
  playing.value = !playing.value
  restartCarousel()
}

function clearCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}

function restartCarousel() {
  clearCarousel()
  if (!playing.value || dashboards.value.length <= 1) return
  carouselTimer = setInterval(() => {
    activeIndex.value = (activeIndex.value + 1) % dashboards.value.length
  }, intervalSeconds.value * 1000)
}

function startClock() {
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen?.()
    } else {
      await document.documentElement.requestFullscreen?.()
    }
  } catch {
    // 浏览器拒绝全屏时保持当前页面
  }
}

watch(
  () => dashboards.value.length,
  () => {
    activeIndex.value = 0
    restartCarousel()
  }
)

watch(
  () => share.value?.name,
  (name) => {
    if (name) document.title = `${name} · 数据大屏`
  },
  { immediate: true }
)

watch(intervalSeconds, () => restartCarousel())

onMounted(() => {
  startClock()
  restartCarousel()
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  clearCarousel()
})
</script>

<style scoped>
.display-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  min-height: 100vh;
  padding: 24px 32px 20px;
  background:
    radial-gradient(circle at 12% 0%, rgba(22, 93, 255, 0.22), transparent 38%),
    radial-gradient(circle at 88% 8%, rgba(0, 180, 42, 0.12), transparent 34%),
    #0b1220;
  color: #e2e8f0;
}

.display-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.display-brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.display-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: #64748b;
}

.display-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #f1f5f9;
}

.display-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.display-clock {
  font-family: var(--font-family-mono);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
  color: #cbd5e1;
}

.display-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.6);
  color: #cbd5e1;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease;
}

.display-icon-button:hover {
  border-color: rgba(96, 165, 250, 0.7);
  color: #93c5fd;
}

.display-subhead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.display-subhead__left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.display-board-title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: #e2e8f0;
}

.display-board-meta {
  font-size: 12px;
  color: #64748b;
}

.display-dots {
  display: flex;
  align-items: center;
  gap: 6px;
}

.display-dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.35);
  cursor: pointer;
  transition: width 0.2s ease, background 0.2s ease;
}

.display-dot--active {
  width: 24px;
  background: #60a5fa;
}

.display-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 20px;
  align-items: stretch;
}

.display-state {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 50vh;
  color: #94a3b8;
}

.display-state__title {
  margin: 0;
  font-size: 16px;
  color: #e2e8f0;
}

.display-state__hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}

.display-footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 11px;
  color: #475569;
  text-align: center;
}

@media (max-width: 900px) {
  .display-page {
    padding: 16px;
  }

  .display-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .display-subhead {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
