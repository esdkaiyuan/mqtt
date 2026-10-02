<template>
  <div class="query-card card">
    <h3 class="card-title">查询条件</h3>
    <el-form label-position="top" class="query-form" @submit.prevent="emit('submit')">
      <div class="form-row">
        <el-form-item label="设备">
          <el-select v-model="deviceId" placeholder="全部设备" clearable filterable>
            <el-option
              v-for="device in deviceOptions"
              :key="device.id"
              :label="`${device.deviceName} (${device.deviceKey})`"
              :value="device.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Topic">
          <el-input v-model="topic" placeholder="Topic 关键词（选填）" clearable />
        </el-form-item>
      </div>
      <div class="form-row">
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="startTime"
            type="datetime"
            placeholder="开始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="endTime"
            type="datetime"
            placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </div>
      <div class="form-actions">
        <el-button type="primary" :loading="loading" @click="emit('submit')">查询</el-button>
        <el-button @click="emit('reset')">重置</el-button>
      </div>
    </el-form>
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
  margin-bottom: var(--grid-gutter);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color-light);
}

.query-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--grid-gutter);
}

.form-actions {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}
</style>