<template>
  <el-dialog
    v-model="visible"
    title="创建设备"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item label="所属产品" required>
        <el-select
          v-model="form.productId"
          placeholder="请选择产品"
          :loading="productsLoading"
          filterable
        >
          <el-option
            v-for="product in products"
            :key="product.id"
            :label="`${product.productName}（${product.productKey}）`"
            :value="product.id"
          />
        </el-select>
        <p v-if="!productsLoading && products.length === 0" class="form-hint">
          暂无可用产品，请先通过 POST /api/products 创建并启用产品
        </p>
      </el-form-item>
      <el-form-item label="设备名称" required>
        <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
      </el-form-item>
      <el-form-item label="设备标识" required>
        <el-input v-model="form.deviceKey" placeholder="全局唯一标识，如 sensor-001" />
      </el-form-item>
      <el-form-item label="设备类型" required>
        <el-select v-model="form.deviceType" placeholder="请选择类型">
          <el-option label="传感器" value="sensor" />
          <el-option label="网关" value="gateway" />
          <el-option label="执行器" value="actuator" />
        </el-select>
      </el-form-item>
      <el-form-item label="MQTT Topic" required>
        <el-input v-model="form.topic" placeholder="如 device/sensor-001/data" />
      </el-form-item>
      <el-form-item label="设备描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="设备描述信息（选填）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">创建设备</el-button>
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
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.dialog-form :deep(.el-select) {
  width: 100%;
}

.form-hint {
  width: 100%;
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
