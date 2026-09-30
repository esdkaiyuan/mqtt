<template>
  <el-dialog
    v-model="visible"
    title="创建设备"
    width="500px"
    :close-on-click-modal="false"
  >
    <form class="dialog-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label class="form-label">所属产品 <span class="required">*</span></label>
        <select
          v-model="form.productId"
          class="form-input"
          :disabled="productsLoading"
        >
          <option value="">{{ productsLoading ? '加载中...' : '请选择产品' }}</option>
          <option
            v-for="product in products"
            :key="product.id"
            :value="product.id"
          >
            {{ product.productName }}（{{ product.productKey }}）
          </option>
        </select>
        <p v-if="!productsLoading && products.length === 0" class="form-hint">
          暂无可用产品，请先通过 POST /api/products 创建并启用产品
        </p>
      </div>
      <div class="form-group">
        <label class="form-label">设备名称 <span class="required">*</span></label>
        <input
          v-model="form.deviceName"
          placeholder="请输入设备名称"
          class="form-input"
        />
      </div>
      <div class="form-group">
        <label class="form-label">设备标识 <span class="required">*</span></label>
        <input
          v-model="form.deviceKey"
          placeholder="全局唯一标识，如sensor-001"
          class="form-input"
        />
      </div>
      <div class="form-group">
        <label class="form-label">设备类型 <span class="required">*</span></label>
        <select v-model="form.deviceType" class="form-input">
          <option value="">请选择类型</option>
          <option value="sensor">传感器</option>
          <option value="gateway">网关</option>
          <option value="actuator">执行器</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">MQTT Topic <span class="required">*</span></label>
        <input
          v-model="form.topic"
          placeholder="如 device/sensor-001/data"
          class="form-input"
        />
      </div>
      <div class="form-group">
        <label class="form-label">设备描述</label>
        <textarea
          v-model="form.description"
          placeholder="设备描述信息（选填）"
          class="form-input form-textarea"
          rows="3"
        ></textarea>
      </div>
    </form>
    <template #footer>
      <div class="dialog-footer">
        <button class="btn-secondary" @click="visible = false">取消</button>
        <button class="btn-primary" :disabled="loading" @click="handleSubmit">
          {{ loading ? '创建中...' : '创建设备' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

defineProps({
  products: { type: Array, default: () => [] },
  productsLoading: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const EMPTY_FORM = {
  productId: '',
  deviceName: '',
  deviceKey: '',
  deviceType: '',
  topic: '',
  description: ''
}

const form = reactive({ ...EMPTY_FORM })

// 每次打开都从空白草稿开始，避免上一次的输入残留
watch(visible, (open) => {
  if (open) Object.assign(form, EMPTY_FORM)
})

function handleSubmit() {
  if (!form.productId || !form.deviceName || !form.deviceKey || !form.deviceType || !form.topic) {
    ElMessage.warning('请填写所有必填项')
    return
  }
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

.form-hint {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
}
</style>
