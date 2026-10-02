<template>
  <el-dialog
    v-model="visible"
    title="设备凭据（仅显示一次）"
    width="520px"
    :close-on-click-modal="false"
  >
    <div class="credential-tip">
      以下凭据仅在本次创建时返回，平台不提供二次查询，请立即复制并妥善保存。
    </div>
    <div class="credential-row">
      <span class="credential-label">MQTT 用户名</span>
      <div class="credential-value">
        <code>{{ credential.username }}</code>
        <el-button link type="primary" @click="copyText(credential.username)">复制</el-button>
      </div>
    </div>
    <div class="credential-row">
      <span class="credential-label">设备密钥</span>
      <div class="credential-value">
        <code>{{ credential.deviceSecret }}</code>
        <el-button link type="primary" @click="copyText(credential.deviceSecret)">复制</el-button>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">我已保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  credential: {
    type: Object,
    default: () => ({ username: '', deviceSecret: '' })
  }
})

const visible = defineModel('visible', { type: Boolean })

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
</style>
