<template>
  <div class="device-card card" :class="{ 'device-card--selected': selected }">
    <div class="device-card-header">
      <el-checkbox
        v-if="selectable"
        class="device-card-select"
        :model-value="selected"
        @change="emit('toggle-select', device)"
      />
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
      <div v-if="hasGroups || hasTags" class="device-card-chips">
        <span
          v-for="group in device.groups"
          :key="`group-${group.id}`"
          class="group-chip"
        >
          <svg-icon name="building" :size="12" />
          {{ group.name }}
        </span>
        <el-tag
          v-for="tag in device.tags"
          :key="`tag-${tag.id}`"
          size="small"
          effect="plain"
          :style="tagStyle(tag)"
        >
          {{ tag.name }}
        </el-tag>
      </div>
    </div>

    <div class="device-card-actions">
      <el-button link type="primary" @click="emit('view', device)">
        <svg-icon name="device" :size="14" />
        查看
      </el-button>
      <el-button link type="primary" @click="emit('edit', device)">
        <svg-icon name="edit" :size="14" />
        编辑
      </el-button>
      <el-button link type="danger" @click="emit('delete', device)">
        <svg-icon name="delete" :size="14" />
        删除
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import SvgIcon from '@/components/Icon.vue'

const props = defineProps({
  device: { type: Object, required: true },
  /** 是否展示多选框（批量操作场景） */
  selectable: { type: Boolean, default: false },
  /** 当前是否被选中 */
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['view', 'edit', 'delete', 'toggle-select'])

const hasGroups = computed(() => (props.device.groups?.length ?? 0) > 0)
const hasTags = computed(() => (props.device.tags?.length ?? 0) > 0)

/** 标签 chip 绑定标签色：有 color 时以文字 / 边框着色，无 color 时用默认样式。 */
function tagStyle(tag) {
  return tag.color ? { color: tag.color, borderColor: tag.color } : {}
}

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
  box-shadow: var(--shadow-card-hover);
}

.device-card--selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary) inset;
}

.device-card-select {
  margin-right: 2px;
  flex-shrink: 0;
}

.device-card-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: var(--spacing-sm);
}

.group-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--color-bg);
  color: var(--color-text-regular);
  font-size: var(--font-size-xs);
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
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
}

.device-info {
  flex: 1;
  min-width: 0;
}

.device-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
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
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.device-type-tag.gateway {
  background: var(--color-success-light);
  color: var(--color-success);
}

.device-type-tag.actuator {
  background: var(--color-warning-light);
  color: var(--color-warning);
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
  color: var(--color-text-regular);
  flex-shrink: 0;
}

.detail-value {
  color: var(--color-text-primary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.topic-text {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.device-card-actions {
  display: flex;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color-light);
}
</style>
