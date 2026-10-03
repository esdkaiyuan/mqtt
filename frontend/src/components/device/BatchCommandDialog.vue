<template>
  <el-dialog
    v-model="visible"
    title="批量下发命令"
    width="520px"
    :close-on-click-modal="false"
  >
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="`将对 ${count} 台设备逐台下发，单台失败不影响其余`"
      description="批量强制异步；参数按每台设备的物模型逐台校验，失败原因在结果明细中逐台展示。"
      class="batch-command__tip"
    />

    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item label="命令类型" required>
        <el-radio-group v-model="form.type">
          <el-radio-button value="property_set">属性设置</el-radio-button>
          <el-radio-button value="service">服务调用</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.type === 'service'" label="服务标识符" required>
        <el-input v-model="form.identifier" maxlength="64" placeholder="如：reboot" />
      </el-form-item>

      <el-form-item label="参数（JSON 对象）" required>
        <el-input
          v-model="form.params"
          type="textarea"
          :rows="5"
          placeholder="{&quot;temperature&quot;: 26}"
        />
        <p class="form-hint">
          属性设置填写属性键值对；服务调用填写服务入参。设备间物模型不同，参数不一致的实体会逐台失败并给出原因。
        </p>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">批量下发</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

defineProps({
  count: { type: Number, default: 0 },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const EMPTY_FORM = { type: 'property_set', identifier: '', params: '' }
const form = reactive({ ...EMPTY_FORM })

// 每次打开都从空白草稿开始，避免上一次的输入残留
watch(visible, (open) => {
  if (open) Object.assign(form, EMPTY_FORM)
})

function handleSubmit() {
  if (form.type === 'service' && !form.identifier.trim()) {
    ElMessage.warning('请填写服务标识符')
    return
  }
  const raw = form.params.trim()
  if (!raw) {
    ElMessage.warning('请填写命令参数')
    return
  }
  let params
  try {
    params = JSON.parse(raw)
  } catch {
    ElMessage.warning('参数需为合法 JSON 对象')
    return
  }
  if (params === null || typeof params !== 'object' || Array.isArray(params)) {
    ElMessage.warning('参数需为 JSON 对象')
    return
  }
  emit('submit', {
    type: form.type,
    identifier: form.type === 'service' ? form.identifier.trim() : null,
    params
  })
}
</script>

<style scoped>
.batch-command__tip {
  margin-bottom: var(--spacing-md);
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}
</style>