<template>
  <div class="webhooks-page page-container">
    <div class="page-header">
      <h2>Webhook配置</h2>
      <button class="btn-primary" @click="openCreateDialog">
        <svg-icon name="add" :size="14" />
        创建Webhook
      </button>
    </div>

    <div class="webhooks-list">
      <div v-if="loading" class="loading-overlay">加载中...</div>

      <div v-else-if="webhooks.length === 0" class="empty-state">
        <p class="empty-state-text">暂无Webhook配置，点击"创建Webhook"开始</p>
      </div>

      <div v-else class="webhooks-grid">
        <div class="webhook-card card" v-for="wh in webhooks" :key="wh.id">
          <div class="wh-header">
            <div>
              <h3 class="wh-name">{{ wh.name }}</h3>
              <span :class="['status-badge', wh.isActive ? 'online' : 'offline']">
                {{ wh.isActive ? '启用' : '已禁用' }}
              </span>
            </div>
            <button class="btn-link-danger" @click="deleteWebhook(wh)">
              <svg-icon name="delete" :size="14" />
              删除
            </button>
          </div>

          <div class="wh-details">
            <div class="wh-row">
              <span class="wh-label">URL</span>
              <code class="wh-url">{{ wh.url }}</code>
            </div>
            <div class="wh-row">
              <span class="wh-label">事件</span>
              <span class="wh-events">{{ formatEvents(wh.events) }}</span>
            </div>
            <div class="wh-row">
              <span class="wh-label">重试</span>
              <span>{{ wh.retryCount }}次 / 超时{{ wh.timeoutSeconds }}s</span>
            </div>
            <div class="wh-row" v-if="wh.lastTriggeredAt">
              <span class="wh-label">最后触发</span>
              <span>{{ formatDate(wh.lastTriggeredAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="创建Webhook" width="560px" :close-on-click-modal="false">
      <form @submit.prevent="handleCreate" class="dialog-form">
        <div class="form-group">
          <label class="form-label">Webhook名称 <span class="required">*</span></label>
          <input v-model="form.name" placeholder="如：设备数据上报回调" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">回调URL <span class="required">*</span></label>
          <input v-model="form.url" placeholder="https://your-server.com/webhook" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">签名密钥</label>
          <input v-model="form.secret" placeholder="用于HMAC-SHA256签名验证" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">监听设备</label>
          <input v-model="form.deviceKey" placeholder="设备标识，留空监听所有设备" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">事件类型 <span class="required">*</span></label>
          <input v-model="form.events" placeholder="[&quot;device.data&quot;,&quot;device.heartbeat&quot;]" class="form-input" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">重试次数</label>
            <input v-model.number="form.retryCount" type="number" class="form-input" />
          </div>
          <div class="form-group">
            <label class="form-label">超时(秒)</label>
            <input v-model.number="form.timeoutSeconds" type="number" class="form-input" />
          </div>
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
const webhooks = ref([])
const showCreate = ref(false)
const createLoading = ref(false)

const form = ref({
  name: '',
  url: '',
  secret: '',
  deviceKey: '',
  events: '["device.data"]',
  retryCount: 3,
  timeoutSeconds: 10
})

async function loadWebhooks() {
  loading.value = true
  try {
    const response = await api.get('/webhooks')
    webhooks.value = response.data || []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  form.value = {
    name: '',
    url: '',
    secret: '',
    deviceKey: '',
    events: '["device.data"]',
    retryCount: 3,
    timeoutSeconds: 10
  }
  showCreate.value = true
}

async function handleCreate() {
  if (!form.value.name || !form.value.url) {
    ElMessage.warning('请填写Webhook名称和URL')
    return
  }
  createLoading.value = true
  try {
    await api.post('/webhooks', { ...form.value })
    ElMessage.success('Webhook创建成功')
    showCreate.value = false
    loadWebhooks()
  } catch (error) {
    // 创建失败的具体原因已由 axios 拦截器统一提示，此处只需结束 loading
  } finally {
    createLoading.value = false
  }
}

async function deleteWebhook(wh) {
  try {
    await ElMessageBox.confirm(
      `确定要删除Webhook"${wh.name}"吗？`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await api.delete(`/webhooks/${wh.id}`)
    ElMessage.success('Webhook已删除')
    loadWebhooks()
  } catch (error) {
    // 用户取消确认对话框时 ElMessageBox 会 reject；接口失败亦由拦截器提示，这里静默即可
  }
}

function formatEvents(events) {
  if (!events) return '-'
  try {
    const arr = JSON.parse(events)
    return arr.join(', ')
  } catch {
    return events
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadWebhooks()
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

.webhooks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--spacing-lg);
}

.webhook-card {
  padding: var(--spacing-lg);
}

.wh-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.wh-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.wh-details {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.wh-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
}

.wh-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.wh-url {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-regular);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wh-events {
  color: var(--color-primary);
  font-size: var(--font-size-xs);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}

.required {
  color: var(--color-danger);
}
</style>
