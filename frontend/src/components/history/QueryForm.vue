<template>
  <div class="query-card card">
    <h3 class="card-title">查询条件</h3>
    <form class="query-form" @submit.prevent="emit('submit')">
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">设备</label>
          <select v-model="deviceId" class="form-input">
            <option value="">全部设备</option>
            <option v-for="device in deviceOptions" :key="device.id" :value="device.id">
              {{ device.deviceName }} ({{ device.deviceKey }})
            </option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Topic</label>
          <input v-model="topic" placeholder="Topic关键词（选填）" class="form-input" />
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">开始时间</label>
          <input v-model="startTime" type="datetime-local" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">结束时间</label>
          <input v-model="endTime" type="datetime-local" class="form-input" />
        </div>
      </div>
      <div class="form-actions">
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? '查询中...' : '查询' }}
        </button>
        <button type="button" class="btn-secondary" @click="emit('reset')">重置</button>
      </div>
    </form>
  </div>
</template>

<script setup>
defineProps({
  deviceOptions: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submit', 'reset'])

const deviceId = defineModel('deviceId', { type: String })
const topic = defineModel('topic', { type: String })
const startTime = defineModel('startTime', { type: String })
const endTime = defineModel('endTime', { type: String })
</script>

<style scoped>
.query-card {
  margin-bottom: var(--spacing-lg);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color-light);
}

.query-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-md);
}

.form-actions {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}
</style>
