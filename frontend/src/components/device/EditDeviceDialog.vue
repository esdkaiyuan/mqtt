<template>
  <el-dialog
    v-model="visible"
    title="编辑设备"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item label="设备名称">
        <el-input v-model="form.deviceName" />
      </el-form-item>
      <el-form-item label="设备类型">
        <el-select v-model="form.deviceType">
          <el-option label="传感器" value="sensor" />
          <el-option label="网关" value="gateway" />
          <el-option label="执行器" value="actuator" />
        </el-select>
      </el-form-item>
      <el-form-item label="MQTT Topic">
        <el-input v-model="form.topic" />
      </el-form-item>
      <el-form-item label="设备描述">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
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
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.dialog-form :deep(.el-select) {
  width: 100%;
}
</style>
