<template>
  <div class="device-control-panel">
    <h3 class="card-title">设备控制</h3>
    <p class="card-desc">
      按物模型校验后下发到
      <code class="device-control-panel__topic">{{ topic }}</code>
      ，设备回执后命令状态自动流转
    </p>

    <el-alert
      v-if="!loading && !capability.modeled"
      type="warning"
      :closable="false"
      show-icon
      title="该产品未定义物模型"
      description="请先到产品页为所属产品定义属性 / 服务，之后才能下发命令。"
    />

    <template v-else>
      <el-radio-group v-model="mode" class="device-control-panel__mode">
        <el-radio-button value="property_set">属性设置</el-radio-button>
        <el-radio-button value="service">服务调用</el-radio-button>
      </el-radio-group>

      <el-form label-position="top" class="device-control-panel__form" @submit.prevent>
        <el-form-item v-if="mode === 'service'" label="服务">
          <el-select v-model="serviceId" placeholder="选择要调用的服务">
            <el-option
              v-for="service in capability.services"
              :key="service.identifier"
              :value="service.identifier"
              :label="service.identifier"
            />
          </el-select>
        </el-form-item>

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

        <p v-if="!loading && fields.length === 0" class="device-control-panel__empty">
          {{ mode === 'property_set' ? '物模型中没有可写（rw）属性' : '请先选择要调用的服务' }}
        </p>
      </el-form>

      <div class="device-control-panel__footer">
        <el-radio-group v-model="callType" size="small">
          <el-radio-button value="async">异步</el-radio-button>
          <el-radio-button value="sync">同步等待回执</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :loading="sending" :disabled="!canSubmit" @click="submit">
          下发命令
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  buildParams,
  commandTopic,
  initialValues,
  missingRequired,
  propertyFields,
  serviceFields
} from '@/utils/commandForm'

const props = defineProps({
  capability: { type: Object, required: true },
  deviceKey: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  sending: { type: Boolean, default: false }
})

const emit = defineEmits(['send'])

const JSON_PLACEHOLDER = '{"key": "value"}'

const mode = ref('property_set')
const callType = ref('async')
const serviceId = ref('')
const values = ref({})

const topic = computed(() => commandTopic(props.deviceKey))
const propertyFieldList = computed(() => propertyFields(props.capability))
const selectedService = computed(
  () => props.capability.services.find((item) => item.identifier === serviceId.value) ?? null
)
const fields = computed(() =>
  mode.value === 'property_set' ? propertyFieldList.value : serviceFields(selectedService.value)
)
const canSubmit = computed(() =>
  mode.value === 'property_set' ? propertyFieldList.value.length > 0 : Boolean(serviceId.value)
)

// 能力加载后默认选中第一个服务，避免「服务调用」页签空表单
watch(
  () => props.capability.services,
  (services) => {
    if (services.length > 0 && !services.some((item) => item.identifier === serviceId.value)) {
      serviceId.value = services[0].identifier
    }
  },
  { immediate: true }
)

// 切换模式 / 服务 / 能力后重置表单值，避免残留上一组字段
watch(
  [mode, serviceId, propertyFieldList],
  () => {
    values.value = initialValues(fields.value)
  },
  { immediate: true }
)

function submit() {
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
  if (Object.keys(params).length === 0) {
    ElMessage.warning('请至少填写一个参数')
    return
  }
  emit('send', {
    type: mode.value,
    identifier: mode.value === 'service' ? serviceId.value : null,
    params,
    callType: callType.value
  })
}
</script>

<style scoped>
.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
}

.device-control-panel__topic {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  padding: 1px 6px;
  border-radius: var(--border-radius-sm);
}

.device-control-panel__mode {
  margin-bottom: var(--spacing-md);
}

.device-control-panel__form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.device-control-panel__empty {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.device-control-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}
</style>