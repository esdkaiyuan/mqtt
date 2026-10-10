<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑产品' : '创建产品'"
    width="520px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item label="产品标识" required>
        <el-input
          v-model="form.productKey"
          placeholder="小写字母、数字与连字符，如 env-sensor"
          :disabled="isEdit"
        />
        <p class="form-hint">
          全局唯一；创建后不可修改，设备认证用户名与主题前缀均以其为基准
        </p>
      </el-form-item>
      <el-form-item label="产品名称" required>
        <el-input v-model="form.productName" placeholder="展示名称，如 环境传感器" />
      </el-form-item>
      <el-form-item label="认证方式" required>
        <el-select v-model="form.authMode" class="auth-mode-select">
          <el-option
            v-for="option in AUTH_MODE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <p v-if="form.authMode !== 'SECRET'" class="form-hint form-hint--warn">
          切换认证方式后，存量设备需重新刷机并写入新凭据；原有一机一密凭据仍保留但不再参与认证
        </p>
      </el-form-item>
      <el-form-item label="主题前缀">
        <el-input v-model="form.topicPrefix" placeholder="默认 device/{deviceKey}" />
      </el-form-item>
      <el-form-item label="载荷格式">
        <el-input v-model="form.payloadFormat" placeholder="默认 JSON" />
      </el-form-item>
      <el-form-item label="产品描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="产品描述信息（选填）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  product: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const EMPTY_FORM = {
  productKey: '',
  productName: '',
  description: '',
  topicPrefix: '',
  payloadFormat: '',
  authMode: 'SECRET'
}

// 与后端 AuthModeValue 枚举对齐；SECRET 为一机一密，缺省值
const AUTH_MODE_OPTIONS = [
  { value: 'SECRET', label: '一机一密' },
  { value: 'X509', label: 'X.509 证书' },
  { value: 'JWT', label: 'JWT 令牌' }
]

const form = reactive({ ...EMPTY_FORM })

const isEdit = computed(() => Boolean(props.product?.id))

// 打开时从待编辑产品填充；新建则回到空白草稿，避免上一次输入残留
watch(visible, (open) => {
  if (!open) return
  Object.assign(form, EMPTY_FORM)
  if (props.product) {
    Object.assign(form, {
      productKey: props.product.productKey || '',
      productName: props.product.productName || '',
      description: props.product.description || '',
      topicPrefix: props.product.topicPrefix || '',
      payloadFormat: props.product.payloadFormat || '',
      authMode: props.product.authMode || 'SECRET'
    })
  }
})

function handleSubmit() {
  if (!form.productKey.trim() || !form.productName.trim()) {
    ElMessage.warning('请填写产品标识与名称')
    return
  }
  emit('submit', { ...form })
}
</script>

<style scoped>
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-hint {
  width: 100%;
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.auth-mode-select {
  width: 100%;
}

.form-hint--warn {
  color: var(--color-warning);
}
</style>