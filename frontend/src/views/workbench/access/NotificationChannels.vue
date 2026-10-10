<template>
  <div class="notification-channels-page page">
    <PageHeader title="通知渠道" desc="配置邮件 / 短信渠道，让告警同时推到邮箱与手机">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <svg-icon name="add" :size="16" />
          新建渠道
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="channels.length === 0"
      description="暂无通知渠道，点击「新建渠道」开始配置"
    />

    <div v-else class="channel-grid">
      <div v-for="channel in channels" :key="channel.id" class="channel-card card">
        <div class="channel-card__header">
          <div>
            <h3 class="channel-card__name">{{ channel.name }}</h3>
            <div class="channel-card__tags">
              <el-tag size="small" effect="plain">
                {{ CHANNEL_TYPE_LABELS[channel.channelType] || channel.channelType }}
              </el-tag>
              <span :class="['status-badge', channel.isActive ? 'online' : 'offline']">
                {{ channel.isActive ? '启用' : '已停用' }}
              </span>
            </div>
          </div>
          <div class="channel-card__actions">
            <el-button link type="primary" :loading="testingId === channel.id" @click="handleTest(channel)">
              测试发送
            </el-button>
            <el-button link type="primary" @click="openEdit(channel)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(channel)">删除</el-button>
          </div>
        </div>

        <div class="channel-card__details">
          <div class="channel-row">
            <span class="channel-row__label">事件</span>
            <span class="channel-row__value channel-row__value--primary">{{ formatEvents(channel.events) }}</span>
          </div>
          <div class="channel-row">
            <span class="channel-row__label">收件人</span>
            <span class="channel-row__value">{{ formatRecipients(channel) }}</span>
          </div>
          <div class="channel-row">
            <span class="channel-row__label">最近投递</span>
            <span class="channel-row__value">{{ formatDateTime(channel.lastTriggeredAt) }}</span>
          </div>
          <div class="channel-row">
            <span class="channel-row__label">连续失败</span>
            <span class="channel-row__value">{{ channel.failureCount ?? 0 }} 次</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑通知渠道' : '新建通知渠道'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="channel-form" @submit.prevent>
        <div class="form-row">
          <el-form-item label="渠道类型" required>
            <el-select v-model="form.channelType" :disabled="Boolean(editingId)" style="width: 100%">
              <el-option
                v-for="option in CHANNEL_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="渠道名称" required>
            <el-input v-model="form.name" maxlength="100" placeholder="如：值班组邮箱" />
          </el-form-item>
        </div>

        <el-form-item label="订阅事件" required>
          <el-select v-model="form.events" multiple style="width: 100%" placeholder="选择需要通知的事件">
            <el-option
              v-for="option in EVENT_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <template v-if="form.channelType === 'EMAIL'">
          <div class="form-row">
            <el-form-item label="SMTP 地址" required>
              <el-input v-model="form.email.host" placeholder="smtp.example.com" />
            </el-form-item>
            <el-form-item label="端口" required>
              <el-input-number
                v-model="form.email.port"
                :min="1"
                :max="65535"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </div>
          <el-form-item label="发件人" required>
            <el-input v-model="form.email.from" placeholder="alert@example.com" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="SMTP 用户名">
              <el-input v-model="form.email.username" placeholder="可选" />
            </el-form-item>
            <el-form-item label="SMTP 密码">
              <el-input
                v-model="form.email.password"
                type="password"
                show-password
                :placeholder="editingId ? '留空或保持 ****** 表示不修改' : '可选'"
              />
            </el-form-item>
          </div>
          <el-form-item label="收件人">
            <el-input v-model="form.email.to" placeholder="逗号分隔，留空发送到本人邮箱" />
          </el-form-item>
          <el-form-item label="启用 SSL">
            <el-switch v-model="form.email.ssl" />
          </el-form-item>
        </template>

        <template v-else>
          <div class="form-row">
            <el-form-item label="短信网关地址" required>
              <el-input v-model="form.sms.url" placeholder="https://sms.example.com/send" />
            </el-form-item>
            <el-form-item label="请求方法" required>
              <el-select v-model="form.sms.method" style="width: 100%">
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="请求体模板">
            <el-input
              v-model="form.sms.bodyTemplate"
              type="textarea"
              :rows="3"
              :placeholder="BODY_TEMPLATE_PLACEHOLDER"
            />
          </el-form-item>
          <el-form-item label="成功关键字">
            <el-input v-model="form.sms.successKeyword" placeholder="响应包含该关键字即视为成功" />
          </el-form-item>
          <el-form-item label="请求头（JSON）">
            <el-input
              v-model="form.sms.headers"
              type="textarea"
              :rows="3"
              :placeholder="editingId ? '留空或保持 ****** 表示不修改' : HEADERS_PLACEHOLDER"
            />
          </el-form-item>
          <el-form-item label="收件手机号">
            <el-input v-model="form.sms.phones" placeholder="逗号分隔，留空发送到本人手机号" />
          </el-form-item>
        </template>

        <el-form-item label="启用">
          <el-switch v-model="form.isActive" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useNotificationChannelsQuery, useNotificationChannelMutations } from '@/composables/useNotificationChannels'
import { formatDateTime } from '@/utils/alert'
import {
  CHANNEL_TYPE_LABELS,
  CHANNEL_TYPE_OPTIONS,
  EVENT_OPTIONS,
  formatEvents,
  parseJson
} from '@/utils/notification'

const { channels, loading } = useNotificationChannelsQuery()
const { saving, createChannel, updateChannel, deleteChannel, testChannel } = useNotificationChannelMutations()

/** 请求头示例含双引号，放在脚本里避免模板属性引号冲突 */
const HEADERS_PLACEHOLDER = '如 {"Authorization":"Bearer xxx"}'
/** 请求体模板示例含双引号与占位符，同样放在脚本里 */
const BODY_TEMPLATE_PLACEHOLDER = '如 {"phones":"${phones}","content":"${content}"}'

const dialogVisible = ref(false)
const editingId = ref(null)
const testingId = ref(null)

const form = reactive({
  channelType: 'EMAIL',
  name: '',
  events: ['alert.triggered'],
  isActive: true,
  email: { host: '', port: 465, username: '', password: '', from: '', ssl: true, to: '' },
  sms: { url: '', method: 'POST', headers: '', bodyTemplate: '', successKeyword: '', phones: '' }
})

function resetForm() {
  Object.assign(form, {
    channelType: 'EMAIL',
    name: '',
    events: ['alert.triggered'],
    isActive: true,
    email: { host: '', port: 465, username: '', password: '', from: '', ssl: true, to: '' },
    sms: { url: '', method: 'POST', headers: '', bodyTemplate: '', successKeyword: '', phones: '' }
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(channel) {
  editingId.value = channel.id
  resetForm()
  const config = parseJson(channel.config, {}) || {}
  form.channelType = channel.channelType
  form.name = channel.name || ''
  form.events = parseJson(channel.events, []) || []
  form.isActive = channel.isActive === 1
  if (channel.channelType === 'EMAIL') {
    Object.assign(form.email, {
      host: config.host || '',
      port: config.port ?? 465,
      username: config.username || '',
      password: config.password || '',
      from: config.from || '',
      ssl: config.ssl !== false,
      to: config.to || ''
    })
  } else {
    Object.assign(form.sms, {
      url: config.url || '',
      method: config.method || 'POST',
      headers: toHeadersText(config.headers),
      bodyTemplate: config.bodyTemplate || '',
      successKeyword: config.successKeyword || '',
      phones: config.phones || ''
    })
  }
  dialogVisible.value = true
}

/** 请求头既可能是脱敏串，也可能是对象；统一转成可编辑文本。 */
function toHeadersText(headers) {
  if (!headers) return ''
  if (typeof headers === 'string') return headers
  try {
    return JSON.stringify(headers)
  } catch {
    return ''
  }
}

function buildConfig() {
  if (form.channelType === 'EMAIL') {
    const config = {
      host: form.email.host.trim(),
      port: form.email.port,
      from: form.email.from.trim(),
      ssl: form.email.ssl
    }
    if (form.email.username.trim()) config.username = form.email.username.trim()
    if (form.email.password) config.password = form.email.password
    if (form.email.to.trim()) config.to = form.email.to.trim()
    return config
  }
  const config = {
    url: form.sms.url.trim(),
    method: form.sms.method
  }
  if (form.sms.bodyTemplate.trim()) config.bodyTemplate = form.sms.bodyTemplate
  if (form.sms.successKeyword.trim()) config.successKeyword = form.sms.successKeyword.trim()
  if (form.sms.phones.trim()) config.phones = form.sms.phones.trim()
  const headers = form.sms.headers.trim()
  if (headers) config.headers = headers === '******' ? headers : parseHeaders(headers)
  return config
}

/** 请求头输入为 JSON；解析失败返回 null 由调用方拦截。 */
function parseHeaders(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function validateForm() {
  if (!form.name.trim()) return '请填写渠道名称'
  if (form.events.length === 0) return '请至少订阅一个通知事件'
  if (form.channelType === 'EMAIL') {
    if (!form.email.host.trim()) return '请填写 SMTP 地址'
    if (!form.email.port) return '请填写 SMTP 端口'
    if (!form.email.from.trim()) return '请填写发件人'
  } else {
    if (!form.sms.url.trim()) return '请填写短信网关地址'
    if (form.sms.headers.trim() && form.sms.headers.trim() !== '******' && parseHeaders(form.sms.headers.trim()) === null) {
      return '请求头须为合法 JSON'
    }
  }
  return ''
}

async function handleSubmit() {
  const error = validateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  const payload = {
    channelType: form.channelType,
    name: form.name.trim(),
    events: form.events,
    isActive: form.isActive,
    config: buildConfig()
  }
  try {
    if (editingId.value) {
      await updateChannel(editingId.value, payload)
      ElMessage.success('渠道已更新')
    } else {
      await createChannel(payload)
      ElMessage.success('渠道已创建')
    }
    dialogVisible.value = false
  } catch {
    // 校验失败等具体原因已由 axios 拦截器统一提示
  }
}

async function handleTest(channel) {
  testingId.value = channel.id
  try {
    await testChannel(channel.id)
    ElMessage.success('测试消息已发送')
  } catch {
    // 失败原因（6264）由 axios 拦截器统一提示
  } finally {
    testingId.value = null
  }
}

async function handleDelete(channel) {
  try {
    await ElMessageBox.confirm(`确定要删除渠道「${channel.name}」吗？历史投递留痕保留。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteChannel(channel.id)
    ElMessage.success('渠道已删除')
  } catch {
    // 失败原因由拦截器提示
  }
}

function formatRecipients(channel) {
  const config = parseJson(channel.config, {}) || {}
  if (channel.channelType === 'EMAIL') {
    return config.to ? config.to : '本人邮箱'
  }
  return config.phones ? config.phones : '本人手机号'
}
</script>

<style scoped>
.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--grid-gutter);
}

.channel-card {
  padding: var(--spacing-lg);
}

.channel-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.channel-card__name {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}

.channel-card__tags {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.channel-card__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

.channel-card__details {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.channel-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.channel-row__label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.channel-row__value {
  color: var(--color-text-regular);
  text-align: right;
  word-break: break-all;
}

.channel-row__value--primary {
  color: var(--color-primary);
}

.channel-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}
</style>