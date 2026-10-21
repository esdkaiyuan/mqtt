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
      <el-form-item label="接入协议">
        <el-select v-model="form.protocolType" class="auth-mode-select">
          <el-option
            v-for="option in PROTOCOL_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <p class="form-hint">
          MODBUS 由平台侧主站按点表轮询；HTTP / MQTT 设备主动上报；切换协议不会自动迁移存量设备接入方式
        </p>
      </el-form-item>
      <template v-if="isDriverProtocol">
        <el-form-item label="驱动标识">
          <el-input v-model="form.driverId" placeholder="MODBUS 默认 modbus-tcp" />
        </el-form-item>
        <el-form-item label="驱动配置（JSON）">
          <el-input
            v-model="form.driverConfig"
            type="textarea"
            :rows="5"
            :placeholder="DRIVER_CONFIG_PLACEHOLDER"
          />
          <p class="form-hint">
            MODBUS 示例：{"host":"192.168.10.20","port":502,"unitId":1}；host 必须为平台可达地址
          </p>
        </el-form-item>
      </template>
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
import { computed, reactive, ref, watch } from 'vue'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'

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
  authMode: 'SECRET',
  protocolType: 'MQTT',
  driverId: '',
  driverConfig: ''
}

const DRIVER_CONFIG_PLACEHOLDER = '{"host":"192.168.10.20","port":502,"unitId":1}'

const PROTOCOL_OPTIONS = [
  { value: 'MQTT', label: 'MQTT 直连' },
  { value: 'HTTP', label: 'HTTP 上报' },
  { value: 'MODBUS', label: 'Modbus（平台主站轮询）' },
  { value: 'OPC_UA', label: 'OPC-UA（预留）' },
  { value: 'GATEWAY_MQTT', label: '网关代理接入' }
]

// 与后端 AuthModeValue 枚举对齐；SECRET 为一机一密，缺省值
const AUTH_MODE_OPTIONS = [
  { value: 'SECRET', label: '一机一密' },
  { value: 'X509', label: 'X.509 证书' },
  { value: 'JWT', label: 'JWT 令牌' }
]

const form = reactive({ ...EMPTY_FORM })
const loadToken = ref(0)

const isEdit = computed(() => Boolean(props.product?.id))
const isDriverProtocol = computed(() => ['MODBUS', 'OPC_UA'].includes(form.protocolType))

// 打开时从待编辑产品填充；新建则回到空白草稿，避免上一次输入残留
watch(visible, (open) => {
  if (!open) return
  loadToken.value += 1
  Object.assign(form, EMPTY_FORM)
  if (props.product) {
    Object.assign(form, {
      productKey: props.product.productKey || '',
      productName: props.product.productName || '',
      description: props.product.description || '',
      topicPrefix: props.product.topicPrefix || '',
      payloadFormat: props.product.payloadFormat || '',
      authMode: props.product.authMode || 'SECRET',
      protocolType: props.product.protocolType || 'MQTT',
      driverId: props.product.driverId || '',
      driverConfig: ''
    })
    // driver_config 带内网地址，产品列表不返回；MODBUS / OPC_UA 编辑时单独拉取
    if (props.product.id && isDriverProtocol.value) {
      loadDriverConfig(props.product.id)
    }
  }
})

async function loadDriverConfig(productId) {
  const token = ++loadToken.value
  try {
    const data = unwrapResult(await productApi.getDriverConfig(productId))
    if (token !== loadToken.value || !visible.value || !data) return
    form.protocolType = data.protocolType || form.protocolType
    form.driverId = data.driverId || ''
    form.driverConfig = data.driverConfig || ''
  } catch {
    // 读取失败时保留列表字段，用户可重新填写；错误提示由拦截器给出
  }
}

function handleSubmit() {
  if (!form.productKey.trim() || !form.productName.trim()) {
    ElMessage.warning('请填写产品标识与名称')
    return
  }
  const payload = { ...form, driverId: form.driverId.trim(), driverConfig: form.driverConfig.trim() }
  if (isDriverProtocol.value && payload.driverConfig) {
    let config
    try {
      config = JSON.parse(payload.driverConfig)
    } catch {
      ElMessage.warning('驱动配置不是合法 JSON')
      return
    }
    if (payload.protocolType === 'MODBUS' && (!config || typeof config !== 'object' || !config.host)) {
      ElMessage.warning('Modbus 驱动配置缺少 host')
      return
    }
  }
  if (payload.protocolType === 'MODBUS' && !payload.driverConfig) {
    ElMessage.warning('Modbus 产品必须填写驱动配置')
    return
  }
  emit('submit', payload)
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