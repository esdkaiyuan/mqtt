<template>
  <div class="api-keys-page page">
    <PageHeader title="API 密钥" desc="管理调用开放 API 的密钥、权限范围与有效期">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          创建 API 密钥
        </el-button>
      </template>
    </PageHeader>

    <div class="api-keys-list">
      <div v-if="loading" class="loading-overlay">加载中...</div>

      <EmptyState v-else-if="apiKeys.length === 0" description="暂无 API 密钥，点击「创建 API 密钥」开始" />

      <div v-else class="keys-grid">
        <div class="key-card card" v-for="key in apiKeys" :key="key.id">
          <div class="key-header">
            <div class="key-info">
              <h3 class="key-name">{{ key.name }}</h3>
              <span :class="['status-badge', key.isActive ? 'online' : 'offline']">
                {{ key.isActive ? '启用' : '已禁用' }}
              </span>
            </div>
            <el-button v-if="key.isActive" link type="danger" @click="deleteKey(key)">
              <svg-icon name="delete" :size="14" />
              撤销
            </el-button>
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
    <el-dialog v-model="showCreate" title="创建 API 密钥" width="520px" :close-on-click-modal="false">
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="密钥名称" required>
          <el-input v-model="form.name" placeholder="如：MyApp-Android" />
        </el-form-item>
        <el-form-item label="权限范围">
          <el-input
            v-model="form.permissions"
            :placeholder="PERMISSIONS_PLACEHOLDER"
          />
          <p class="form-hint">留空表示拥有全部权限，多个权限使用 JSON 数组格式。</p>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expiresAt"
            type="datetime"
            placeholder="留空表示永不过期"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/axios'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const loading = ref(false)
const apiKeys = ref([])
const showCreate = ref(false)
const createLoading = ref(false)

const PERMISSIONS_PLACEHOLDER = '["device:read","device:write","data:read"]'

const form = ref({
  name: '',
  permissions: PERMISSIONS_PLACEHOLDER,
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
  form.value = { name: '', permissions: PERMISSIONS_PLACEHOLDER, expiresAt: '' }
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
    form.value = { name: '', permissions: PERMISSIONS_PLACEHOLDER, expiresAt: '' }
    loadKeys()
  } catch (error) {
    // 创建失败的具体原因已由 axios 拦截器统一提示，此处只需结束 loading
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
    // 用户取消确认对话框时 ElMessageBox 会 reject；接口失败亦由拦截器提示，这里静默即可
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
.keys-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--grid-gutter);
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
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
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
  border-radius: var(--border-radius-sm);
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

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-hint {
  width: 100%;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  margin-top: var(--spacing-xs);
  line-height: 1.5;
}
</style>
