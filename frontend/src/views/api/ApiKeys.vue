<template>
  <div class="api-keys-page page-container">
    <div class="page-header">
      <h2>API密钥管理</h2>
      <button class="btn-primary" @click="openCreateDialog">
        <svg-icon name="add" :size="14" />
        创建API密钥
      </button>
    </div>

    <div class="api-keys-list">
      <div v-if="loading" class="loading-overlay">加载中...</div>

      <div v-else-if="apiKeys.length === 0" class="empty-state">
        <p class="empty-state-text">暂无API密钥，点击"创建API密钥"开始</p>
      </div>

      <div v-else class="keys-grid">
        <div class="key-card card" v-for="key in apiKeys" :key="key.id">
          <div class="key-header">
            <div class="key-info">
              <h3 class="key-name">{{ key.name }}</h3>
              <span :class="['status-badge', key.isActive ? 'online' : 'offline']">
                {{ key.isActive ? '启用' : '已禁用' }}
              </span>
            </div>
            <button class="btn-link-danger" @click="deleteKey(key)" v-if="key.isActive">
              <svg-icon name="delete" :size="14" />
              撤销
            </button>
          </div>

          <div class="key-details">
            <div class="key-row">
              <span class="key-label">密钥值</span>
              <code class="key-value">{{ maskKey(key.keyValue) }}</code>
            </div>
            <div class="key-row">
              <span class="key-label">权限</span>
              <span class="key-perms">{{ key.permissions || '全部权限' }}</span>
            </div>
            <div class="key-row">
              <span class="key-label">过期时间</span>
              <span class="key-expiry">{{ key.expiresAt || '永不过期' }}</span>
            </div>
            <div class="key-row">
              <span class="key-label">最后使用</span>
              <span>{{ key.lastUsedAt ? formatDate(key.lastUsedAt) : '从未使用' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="创建API密钥" width="520px" :close-on-click-modal="false">
      <form @submit.prevent="handleCreate" class="dialog-form">
        <div class="form-group">
          <label class="form-label">密钥名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="如：MyApp-Android" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">权限范围</label>
          <input v-model="form.permissions" placeholder='如：["device:read","device:write","data:read"]' class="form-input" />
          <p class="form-hint">留空表示拥有全部权限。多个权限用JSON数组格式。</p>
        </div>
        <div class="form-group">
          <label class="form-label">过期时间</label>
          <input v-model="form.expiresAt" type="datetime-local" class="form-input" />
          <p class="form-hint">留空表示永不过期。</p>
        </div>
      </form>
      <template #footer>
        <button class="btn-secondary" @click="showCreate = false">取消</button>
        <button class="btn-primary" @click="handleCreate" :disabled="createLoading">
          {{ createLoading ? '创建中...' : '创建' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/axios'
import SvgIcon from '@/components/Icon.vue'

const loading = ref(false)
const apiKeys = ref([])
const showCreate = ref(false)
const createLoading = ref(false)

const form = ref({
  name: '',
  permissions: '["device:read","device:write","data:read"]',
  expiresAt: ''
})

async function loadKeys() {
  loading.value = true
  try {
    const response = await api.get('/api-keys')
    apiKeys.value = response.data || []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.value = { name: '', permissions: '["device:read","device:write","data:read"]', expiresAt: '' }
  showCreate.value = true
}

async function handleCreate() {
  if (!form.value.name) {
    ElMessage.warning('请输入密钥名称')
    return
  }
  createLoading.value = true
  try {
    const response = await api.post('/api-keys', {
      name: form.value.name,
      permissions: form.value.permissions,
      expiresAt: form.value.expiresAt || null
    })
    ElMessage.success('API密钥创建成功')
    showCreate.value = false
    // Show the key value to user (only shown once!)
    ElMessageBox.alert(
      '请妥善保存您的API密钥，关闭后将无法再次查看完整值：\n\n' + response.data.keyValue,
      'API密钥已创建',
      { confirmButtonText: '我已保存' }
    )
    form.value = { name: '', permissions: '["device:read","device:write","data:read"]', expiresAt: '' }
    loadKeys()
  } catch (error) {
  } finally {
    createLoading.value = false
  }
}

async function deleteKey(key) {
  try {
    await ElMessageBox.confirm(
      `确定要撤销API密钥"${key.name}"吗？撤销后无法恢复。`,
      '确认撤销',
      { confirmButtonText: '撤销', cancelButtonText: '取消', type: 'warning' }
    )
    await api.delete(`/api-keys/${key.id}`)
    ElMessage.success('API密钥已撤销')
    loadKeys()
  } catch (error) {
  }
}

function maskKey(keyValue) {
  if (!keyValue || keyValue.length < 16) return keyValue
  return keyValue.slice(0, 8) + '...' + keyValue.slice(-4)
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadKeys()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.page-header h2 {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.keys-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--spacing-lg);
}

.key-card {
  padding: var(--spacing-lg);
  transition: box-shadow 0.2s;
}

.key-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.key-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.key-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.key-details {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.key-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
}

.key-label {
  color: var(--color-text-tertiary);
}

.key-value {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: 4px;
  color: var(--color-text-primary);
}

.key-perms {
  color: var(--color-text-regular);
  font-size: var(--font-size-xs);
}

.key-expiry {
  color: var(--color-text-regular);
  font-size: var(--font-size-sm);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--spacing-xs);
}

.required {
  color: var(--color-danger);
}
</style>
