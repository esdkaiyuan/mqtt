<template>
  <div class="webhooks-page page">
    <PageHeader title="Webhook" desc="配置设备事件回调地址、签名与重试策略">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          创建 Webhook
        </el-button>
      </template>
    </PageHeader>

    <div class="webhooks-list">
      <div v-if="loading" class="loading-overlay">加载中...</div>

      <EmptyState v-else-if="webhooks.length === 0" description="暂无 Webhook 配置，点击「创建 Webhook」开始" />

      <div v-else class="webhooks-grid">
        <div class="webhook-card card" v-for="wh in webhooks" :key="wh.id">
          <div class="wh-header">
            <div>
              <h3 class="wh-name">{{ wh.name }}</h3>
              <span :class="['status-badge', wh.isActive ? 'online' : 'offline']">
                {{ wh.isActive ? '启用' : '已禁用' }}
              </span>
            </div>
            <el-button link type="danger" @click="deleteWebhook(wh)">
              <svg-icon name="delete" :size="14" />
              删除
            </el-button>
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
    <el-dialog v-model="showCreate" title="创建 Webhook" width="560px" :close-on-click-modal="false">
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="Webhook 名称" required>
          <el-input v-model="form.name" placeholder="如：设备数据上报回调" />
        </el-form-item>
        <el-form-item label="回调 URL" required>
          <el-input v-model="form.url" placeholder="https://your-server.com/webhook" />
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input v-model="form.secret" placeholder="用于 HMAC-SHA256 签名验证" />
        </el-form-item>
        <el-form-item label="监听设备">
          <el-input v-model="form.deviceKey" placeholder="设备标识，留空监听所有设备" />
        </el-form-item>
        <el-form-item label="事件类型" required>
          <el-input v-model="form.events" :placeholder="EVENTS_PLACEHOLDER" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="重试次数">
            <el-input-number
              v-model="form.retryCount"
              :min="0"
              :max="10"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="超时(秒)">
            <el-input-number
              v-model="form.timeoutSeconds"
              :min="1"
              :max="120"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </div>
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
const webhooks = ref([])
const showCreate = ref(false)
const createLoading = ref(false)

const EVENTS_PLACEHOLDER = '["device.data","device.heartbeat"]'

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
.webhooks-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--grid-gutter);
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
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
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

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}
</style>
