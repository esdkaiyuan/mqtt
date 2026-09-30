<template>
  <div class="device-card card">
    <div class="device-card-header">
      <div class="device-icon">
        <svg-icon name="device" :size="24" />
      </div>
      <div class="device-info">
        <h3 class="device-name">{{ device.deviceName }}</h3>
        <span :class="['device-type-tag', device.deviceType]">{{ formatDeviceType(device.deviceType) }}</span>
      </div>
      <span :class="['status-badge', device.status?.toLowerCase()]">
        {{ formatStatus(device.status) }}
      </span>
    </div>

    <div class="device-card-body">
      <div class="device-detail-row">
        <span class="detail-label">设备标识</span>
        <span class="detail-value">{{ device.deviceKey }}</span>
      </div>
      <div class="device-detail-row">
        <span class="detail-label">Topic</span>
        <span class="detail-value topic-text">{{ device.topic }}</span>
      </div>
      <div v-if="device.description" class="device-detail-row">
        <span class="detail-label">描述</span>
        <span class="detail-value">{{ device.description }}</span>
      </div>
    </div>

    <div class="device-card-actions">
      <button class="btn-link" @click="emit('view', device)">
        <svg-icon name="device" :size="14" />
        查看
      </button>
      <button class="btn-link" @click="emit('edit', device)">
        <svg-icon name="edit" :size="14" />
        编辑
      </button>
      <button class="btn-link-danger" @click="emit('delete', device)">
        <svg-icon name="delete" :size="14" />
        删除
      </button>
    </div>
  </div>
</template>

<script setup>
import SvgIcon from '@/components/Icon.vue'

defineProps({
  device: { type: Object, required: true }
})

const emit = defineEmits(['view', 'edit', 'delete'])

function formatStatus(status) {
  const statusMap = {
    'ONLINE': '在线',
    'OFFLINE': '离线',
    'INACTIVE': '未激活'
  }
  return statusMap[status] || status
}

function formatDeviceType(type) {
  const map = { 'sensor': '传感器', 'gateway': '网关', 'actuator': '执行器' }
  return map[type] || type
}
</script>

<style scoped>
.device-card {
  padding: var(--spacing-md);
  transition: box-shadow 0.2s;
}

.device-card:hover {
  box-shadow: var(--shadow-md);
}

.device-card-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.device-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-gray-light);
  border-radius: var(--border-radius-sm);
}

.device-info {
  flex: 1;
  min-width: 0;
}

.device-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.device-type-tag.sensor {
  background: #E8F3FF;
  color: #165DFF;
}

.device-type-tag.gateway {
  background: #E8FFEA;
  color: #00B42A;
}

.device-type-tag.actuator {
  background: #FFF3E8;
  color: #FF7D00;
}

.device-card-body {
  margin-bottom: var(--spacing-md);
}

.device-detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: var(--font-size-sm);
}

.detail-label {
  color: var(--color-gray-text);
  flex-shrink: 0;
}

.detail-value {
  color: var(--color-gray-dark);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.topic-text {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-xs);
}

.device-card-actions {
  display: flex;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color-light);
}

.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--color-primary);
  background: none;
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-link-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--color-danger);
  background: none;
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}
</style>
