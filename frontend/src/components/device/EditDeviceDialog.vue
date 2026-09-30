<template>
  <el-dialog
    v-model="visible"
    title="编辑设备"
    width="500px"
    :close-on-click-modal="false"
  >
    <form class="dialog-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label class="form-label">设备名称</label>
        <input v-model="form.deviceName" class="form-input" />
      </div>
      <div class="form-group">
        <label class="form-label">设备类型</label>
        <select v-model="form.deviceType" class="form-input">
          <option value="sensor">传感器</option>
          <option value="gateway">网关</option>
          <option value="actuator">执行器</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">MQTT Topic</label>
        <input v-model="form.topic" class="form-input" />
      </div>
      <div class="form-group">
        <label class="form-label">设备描述</label>
        <textarea v-model="form.description" class="form-input form-textarea" rows="3"></textarea>
      </div>
    </form>
    <template #footer>
      <div class="dialog-footer">
        <button class="btn-secondary" @click="visible = false">取消</button>
        <button class="btn-primary" :disabled="loading" @click="handleSubmit">
          {{ loading ? '保存中...' : '保存' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  device: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const form = reactive({
  id: null,
  deviceName: '',
  deviceType: '',
  topic: '',
  description: ''
})

// 打开时把目标设备拷进本地草稿，避免直接改动 store 中的对象
watch(visible, (open) => {
  if (!open) return
  const device = props.device || {}
  form.id = device.id ?? null
  form.deviceName = device.deviceName || ''
  form.deviceType = device.deviceType || ''
  form.topic = device.topic || ''
  form.description = device.description || ''
})

function handleSubmit() {
  emit('submit', { ...form })
}
</script>

<style scoped>
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}
</style>
