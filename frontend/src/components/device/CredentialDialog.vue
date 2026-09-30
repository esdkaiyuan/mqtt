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
        <button type="button" class="btn-link" @click="copyText(credential.username)">
          复制
        </button>
      </div>
    </div>
    <div class="credential-row">
      <span class="credential-label">设备密钥</span>
      <div class="credential-value">
        <code>{{ credential.deviceSecret }}</code>
        <button type="button" class="btn-link" @click="copyText(credential.deviceSecret)">
          复制
        </button>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <button class="btn-primary" @click="visible = false">我已保存</button>
      </div>
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
  background: #FFF7E6;
  border: 1px solid #FFD591;
  border-radius: var(--border-radius-sm);
  color: #AD6800;
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
  color: var(--color-gray-text);
  font-size: var(--font-size-sm);
}

.credential-value {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: 6px 10px;
  background: var(--color-gray-light);
  border-radius: var(--border-radius-sm);
  min-width: 0;
}

.credential-value code {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--color-primary);
  background: none;
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}
</style>
