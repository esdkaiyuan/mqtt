<template>
  <div class="sub-device-panel">
    <div class="sub-device-panel__head">
      <div class="sub-device-panel__intro">
        <h3 class="card-title">子设备</h3>
        <p class="card-desc">经本设备代连的下级设备；解绑只清除父子关系，设备与凭据保留</p>
      </div>
      <el-button
        type="primary"
        :loading="attaching"
        :disabled="!canWrite"
        @click="$emit('attach')"
      >
        挂载既有设备
      </el-button>
    </div>

    <div v-loading="loading" class="sub-device-panel__body">
      <ul v-if="subDevices.length" class="sub-device-list">
        <li v-for="item in subDevices" :key="item.id" class="sub-device-item">
          <div class="sub-device-main">
            <span class="sub-device-key">{{ item.deviceKey }}</span>
            <span class="sub-device-name">{{ item.deviceName }}</span>
          </div>
          <span :class="['status-badge', statusClass(item.status)]">{{ formatStatus(item.status) }}</span>
          <span class="sub-device-time">{{ formatTime(item.lastSeen) }}</span>
          <div class="sub-device-actions">
            <el-button size="small" @click="$emit('view', item)">详情</el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :loading="detaching"
              :disabled="!canWrite"
              @click="$emit('detach', item)"
            >
              解绑
            </el-button>
          </div>
        </li>
      </ul>
      <p v-else class="sub-device-empty">该设备下暂无子设备</p>

      <p v-if="!canWrite" class="sub-device-hint">仅管理员可挂载或解绑子设备</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  subDevices: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  attaching: { type: Boolean, default: false },
  detaching: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: true }
})

defineEmits(['attach', 'detach', 'view'])

const STATUS_LABELS = { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }

function formatStatus(status) {
  return STATUS_LABELS[status] || status || '-'
}

function statusClass(status) {
  return String(status || '').toLowerCase()
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.sub-device-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.sub-device-panel__intro {
  min-width: 0;
}

.sub-device-panel__body {
  min-height: 60px;
  margin-top: var(--spacing-md);
}

.sub-device-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.sub-device-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-light);
}

.sub-device-item:last-child {
  border-bottom: none;
}

.sub-device-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.sub-device-key {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.sub-device-name {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.sub-device-time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.sub-device-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.sub-device-empty {
  margin: 0;
  padding: var(--spacing-md) 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  text-align: center;
}

.sub-device-hint {
  margin: var(--spacing-md) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
