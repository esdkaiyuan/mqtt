<template>
  <el-dialog
    :model-value="modelValue"
    :title="`调用服务 · ${service?.identifier || ''}`"
    width="520px"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <p class="dialog-desc">
      服务调用会按物模型校验参数后下发到设备的命令主题，状态可在「最近命令」中查看。
    </p>
    <el-form label-position="top" @submit.prevent>
      <el-form-item
        v-for="field in fields"
        :key="field.identifier"
        :label="field.identifier"
        :required="field.required"
      >
        <el-input-number
          v-if="field.control === 'number'"
          v-model="values[field.identifier]"
          :min="field.min ?? undefined"
          :max="field.max ?? undefined"
          :step="field.integer ? 1 : 0.1"
          :precision="field.integer ? 0 : undefined"
          controls-position="right"
        />
        <el-switch v-else-if="field.control === 'switch'" v-model="values[field.identifier]" />
        <el-select
          v-else-if="field.control === 'select'"
          v-model="values[field.identifier]"
          placeholder="请选择"
        >
          <el-option v-for="key in field.enumKeys" :key="key" :value="key" :label="key" />
        </el-select>
        <el-date-picker
          v-else-if="field.control === 'date'"
          v-model="values[field.identifier]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择时间"
        />
        <el-input
          v-else-if="field.control === 'json'"
          v-model="values[field.identifier]"
          type="textarea"
          :rows="3"
          :placeholder="JSON_PLACEHOLDER"
        />
        <el-input
          v-else
          v-model="values[field.identifier]"
          :maxlength="field.textLength ?? undefined"
          placeholder="请输入"
        />
      </el-form-item>
      <p v-if="fields.length === 0" class="empty-hint">该服务没有入参，点击「立即调用」即可。</p>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="sending" @click="submit">立即调用</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceApi } from '@/api/device'
import { buildParams, initialValues, missingRequired, serviceFields } from '@/utils/commandForm'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  service: { type: Object, default: null },
  deviceKey: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'submitted'])

const JSON_PLACEHOLDER = '{"key": "value"}'

const values = ref({})
const sending = ref(false)
const fields = computed(() => serviceFields(props.service))

watch(
  [() => props.modelValue, () => props.service],
  () => {
    if (props.modelValue) values.value = initialValues(fields.value)
  },
  { immediate: true }
)

async function submit() {
  if (!props.service || !props.deviceKey) return
  const missing = missingRequired(fields.value, values.value)
  if (missing.length > 0) {
    ElMessage.warning(`请填写必填参数：${missing.join('、')}`)
    return
  }
  const { params, error } = buildParams(fields.value, values.value)
  if (error) {
    ElMessage.warning(error)
    return
  }
  sending.value = true
  try {
    await deviceApi.sendCommand(props.deviceKey, {
      type: 'service',
      identifier: props.service.identifier,
      params,
      callType: props.service.callType || 'async'
    })
    ElMessage.success('服务调用已下发')
    emit('update:modelValue', false)
    emit('submitted')
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.dialog-desc {
  margin-bottom: var(--spacing-md);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

.empty-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
