<template>
  <header class="topbar">
    <div class="topbar__left">
      <button
        v-if="!ui.isNarrow"
        class="topbar__icon-btn"
        :title="navToggleTitle"
        @click="handleNavToggle"
      >
        <el-icon :size="18">
          <Menu v-if="ui.isPhone" />
          <Expand v-else-if="navCollapsed" />
          <Fold v-else />
        </el-icon>
      </button>

      <BrandLogo :size="28" :text-size="16" to="/workbench/dashboard" class="topbar__brand" />

      <div class="topbar__divider" />

      <AppBreadcrumb class="topbar__breadcrumb" />
    </div>

    <div class="topbar__right">
      <el-input
        v-model="keyword"
        class="topbar__search"
        placeholder="搜索设备名称 / 标识"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <button
        v-if="ui.railAsDrawer"
        class="topbar__icon-btn"
        title="上下文栏"
        @click="ui.toggleRailDrawer()"
      >
        <el-icon :size="18"><Operation /></el-icon>
      </button>

      <el-tag class="topbar__env" size="small" type="info" effect="plain">{{ env }}</el-tag>

      <el-popover
        v-model:visible="notificationVisible"
        trigger="click"
        placement="bottom-end"
        :width="340"
        popper-class="topbar-notify-popper"
      >
        <template #reference>
          <el-badge
            :value="unreadCount"
            :max="99"
            :hidden="unreadCount === 0"
            class="topbar__badge"
          >
            <button class="topbar__icon-btn" title="通知">
              <el-icon :size="18"><Bell /></el-icon>
            </button>
          </el-badge>
        </template>

        <div class="notify">
          <div class="notify__header">
            <span class="notify__title">通知</span>
            <span v-if="unreadCount > 0" class="notify__count">{{ unreadCount }} 条未处理</span>
          </div>

          <div v-if="recentLoading && recentAlerts.length === 0" class="notify__hint">加载中...</div>
          <div v-else-if="recentAlerts.length === 0" class="notify__hint">暂无活动告警</div>
          <ul v-else class="notify__list">
            <li
              v-for="alert in recentAlerts"
              :key="alert.id"
              class="notify__item"
              @click="goToAlert(alert)"
            >
              <el-tag size="small" :type="SEVERITY_TAG_TYPES[alert.severity] || 'info'">
                {{ SEVERITY_LABELS[alert.severity] || alert.severity }}
              </el-tag>
              <div class="notify__body">
                <p class="notify__alert-title">{{ alert.title }}</p>
                <p class="notify__meta">
                  {{ alert.deviceKey }} · {{ formatRelativeTime(alert.lastTriggeredAt) }}
                </p>
              </div>
            </li>
          </ul>

          <div class="notify__footer">
            <button class="notify__more" @click="goToAllAlerts">查看全部</button>
          </div>
        </div>
      </el-popover>

      <el-dropdown @command="handleUserCommand">
        <div class="topbar__user">
          <svg-icon name="user" :size="18" />
          <span class="topbar__username">{{ authStore.user?.username || '未登录' }}</span>
          <el-icon :size="12"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人信息</el-dropdown-item>
            <el-dropdown-item command="home" divided>返回官网</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Expand, Search, Bell, ArrowDown, Menu, Operation } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { useAlertUnreadCountQuery, useAlertRecentQuery } from '@/composables/useAlerts'
import { SEVERITY_LABELS, SEVERITY_TAG_TYPES, formatRelativeTime } from '@/utils/alert'
import SvgIcon from '@/components/Icon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'

const router = useRouter()
const authStore = useAuthStore()
const ui = useUiStore()

const keyword = ref('')
const env = computed(() => import.meta.env.VITE_ENV || 'PROD')
const navCollapsed = computed(() => ui.navCollapsed)

const { unreadCount } = useAlertUnreadCountQuery()
const { recentAlerts, loading: recentLoading } = useAlertRecentQuery(10)
const notificationVisible = ref(false)

function goToAlert(alert) {
  notificationVisible.value = false
  router.push({ path: '/workbench/alerts', query: { alertId: alert.id } })
}

function goToAllAlerts() {
  notificationVisible.value = false
  router.push('/workbench/alerts')
}

const navToggleTitle = computed(() => {
  if (ui.isPhone) return '打开导航'
  return navCollapsed.value ? '展开导航' : '收起导航'
})

// 手机档汉堡唤起左导航抽屉；其余档位就地折叠
function handleNavToggle() {
  if (ui.isPhone) {
    ui.toggleNavDrawer()
  } else {
    ui.toggleNav()
  }
}

function handleSearch() {
  const value = keyword.value.trim()
  if (!value) return
  router.push({ path: '/workbench/devices', query: { keyword: value } })
}

function handleUserCommand(command) {
  if (command === 'logout') {
    authStore.logout()
  } else if (command === 'home') {
    router.push('/')
  } else if (command === 'profile') {
    ElMessage.info('个人信息功能开发中')
  }
}
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--grid-gutter);
  padding: 0 var(--spacing-lg);
  background: var(--color-white);
  border-bottom: 1px solid var(--border-color);
}

.topbar__left,
.topbar__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  min-width: 0;
}

.topbar__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--border-radius-sm);
  color: var(--color-text-regular);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.topbar__icon-btn:hover {
  background: var(--color-bg);
  color: var(--color-primary);
}

.topbar__brand {
  flex-shrink: 0;
}

.topbar__divider {
  width: 1px;
  height: 20px;
  background: var(--border-color);
}

.topbar__breadcrumb {
  min-width: 0;
}

.topbar__search {
  width: 240px;
}

.topbar__env {
  flex-shrink: 0;
}

.topbar__badge {
  display: inline-flex;
}

.topbar__user {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--border-radius-sm);
  cursor: pointer;
  color: var(--color-text-regular);
  transition: background-color var(--transition-fast);
}

.topbar__user:hover {
  background: var(--color-bg);
}

.topbar__username {
  font-size: var(--font-size-sm);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 平板及以下：隐藏全局搜索 */
@media (max-width: 1023px) {
  .topbar__search {
    display: none;
  }
}

/* 手机：仅保留 logo + 用户 */
@media (max-width: 767px) {
  .topbar__breadcrumb,
  .topbar__env,
  .topbar__badge,
  .topbar__divider {
    display: none;
  }
}
</style>

<!-- 通知下拉内容被 teleport 到 body，scoped 样式无法命中，故用 popper-class 做全局限定 -->
<style>
.topbar-notify-popper {
  padding: 0;
}

.notify__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.notify__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.notify__count {
  font-size: var(--font-size-xs);
  color: var(--color-danger, #f56c6c);
}

.notify__hint {
  padding: var(--spacing-lg) 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.notify__list {
  max-height: 320px;
  overflow-y: auto;
  margin: 0;
  padding: 0;
  list-style: none;
}

.notify__item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.notify__item:hover {
  background: var(--color-bg);
}

.notify__body {
  min-width: 0;
}

.notify__alert-title {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify__meta {
  margin: 2px 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.notify__footer {
  border-top: 1px solid var(--border-color-light);
  text-align: center;
}

.notify__more {
  width: 100%;
  padding: var(--spacing-sm) 0;
  border: none;
  background: transparent;
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  cursor: pointer;
}

.notify__more:hover {
  background: var(--color-bg);
}
</style>