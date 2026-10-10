<template>
  <el-dialog
    v-model="visible"
    title="设备证书（仅显示一次）"
    width="640px"
    :close-on-click-modal="false"
  >
    <div class="credential-tip">
      私钥仅在本次签发时返回，平台不落库、不提供二次查询，请立即复制并妥善保存。
    </div>
    <div class="credential-row">
      <span class="credential-label">证书主体</span>
      <div class="credential-value">
        <code>{{ certificate.subject }}</code>
        <el-button link type="primary" @click="copyText(certificate.subject)">复制</el-button>
      </div>
    </div>
    <div class="credential-row">
      <span class="credential-label">序列号</span>
      <div class="credential-value">
        <code>{{ certificate.serial }}</code>
        <el-button link type="primary" @click="copyText(certificate.serial)">复制</el-button>
      </div>
    </div>
    <div class="credential-row">
      <span class="credential-label">有效期</span>
      <div class="credential-value credential-value--plain">
        <code>{{ validityText }}</code>
      </div>
    </div>

    <div class="pem-block">
      <div class="pem-block__head">
        <span>证书链</span>
        <el-button link type="primary" @click="copyText(certificate.certificatePem)">复制</el-button>
      </div>
      <pre class="pem-block__body">{{ certificate.certificatePem }}</pre>
    </div>

    <div class="pem-block">
      <div class="pem-block__head">
        <span>私钥</span>
        <el-button link type="primary" @click="copyText(certificate.privateKeyPem)">复制</el-button>
      </div>
      <pre class="pem-block__body">{{ certificate.privateKeyPem }}</pre>
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">我已保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  certificate: {
    type: Object,
    default: () => ({
      subject: '',
      serial: '',
      notBefore: '',
      notAfter: '',
      certificatePem: '',
      privateKeyPem: ''
    })
  }
})

const visible = defineModel('visible', { type: Boolean })

const validityText = computed(() => {
  const { notBefore, notAfter } = props.certificate
  if (!notBefore && !notAfter) return '-'
  return `${formatTime(notBefore)} 至 ${formatTime(notAfter)}`
})

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}
</script>

<style scoped>
.credential-tip {
  padding: 10px 12px;
  margin-bottom: var(--spacing-md);
  background: var(--color-warning-light);
  border: 1px solid var(--color-warning-light);
  border-radius: var(--border-radius-sm);
  color: var(--color-warning);
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.credential-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 6px 0;
}

.credential-label {
  width: 90px;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.credential-value {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: 6px 10px;
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  min-width: 0;
}

.credential-value code {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pem-block {
  margin-top: var(--spacing-md);
}

.pem-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-xs);
}

.pem-block__body {
  margin: 0;
  max-height: 140px;
  overflow: auto;
  padding: 8px 10px;
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
