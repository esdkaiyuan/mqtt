<template>
  <div class="audit-page page">
    <PageHeader title="审计日志" desc="管理端与开放 API 的写操作留痕：谁、何时、从哪里、做了什么、结果如何" />

    <div class="filter-bar">
      <el-input
        v-model="filters.username"
        placeholder="用户名"
        clearable
        class="filter-item"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filters.action" placeholder="全部操作" clearable class="filter-item">
        <el-option
          v-for="(label, value) in ACTION_LABELS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-select v-model="filters.resourceType" placeholder="全部资源" clearable class="filter-item">
        <el-option
          v-for="(label, value) in RESOURCE_LABELS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-select v-model="filters.source" placeholder="全部来源" clearable class="filter-item filter-item--small">
        <el-option
          v-for="(label, value) in SOURCE_LABELS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-select v-model="filters.success" placeholder="全部结果" clearable class="filter-item filter-item--small">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="路径 / 资源 / 用户"
        clearable
        class="filter-item"
        @keyup.enter="handleSearch"
      />
      <el-date-picker
        v-model="filters.range"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
        class="filter-item filter-item--wide"
      />
      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button @click="handleExport">导出 CSV</el-button>
      </div>
    </div>

    <el-table
      :data="rows"
      v-loading="loading"
      row-key="id"
      size="small"
      class="audit-table"
      @row-click="openDetail"
    >
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="用户" min-width="130">
        <template #default="{ row }">
          <span v-if="row.username">{{ row.username }}</span>
          <span v-else class="text-muted">匿名</span>
        </template>
      </el-table-column>
      <el-table-column prop="role" label="角色" width="100" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">{{ SOURCE_LABELS[row.source] || row.source }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">{{ ACTION_LABELS[row.action] || row.action }}</template>
      </el-table-column>
      <el-table-column label="资源" min-width="150">
        <template #default="{ row }">
          <span>{{ RESOURCE_LABELS[row.resourceType] || row.resourceType }}</span>
          <code v-if="row.resourceId" class="audit-resource-id">{{ row.resourceId }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ row.durationMs }} ms</template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="70" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="audit-pagination">
      <el-pagination
        layout="total, prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        background
        @current-change="load"
      />
    </div>

    <el-drawer v-model="detailVisible" title="审计详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="用户">
            {{ detail.username || '匿名' }}<span v-if="detail.userId">（ID {{ detail.userId }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="角色">{{ detail.role || '—' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ SOURCE_LABELS[detail.source] || detail.source }}</el-descriptions-item>
          <el-descriptions-item label="操作">
            {{ ACTION_LABELS[detail.action] || detail.action }}（{{ detail.action }}）
          </el-descriptions-item>
          <el-descriptions-item label="资源">
            {{ RESOURCE_LABELS[detail.resourceType] || detail.resourceType }}
            <span v-if="detail.resourceId"> / {{ detail.resourceId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="请求">
            {{ detail.httpMethod }} {{ detail.path }}
          </el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="detail.success ? 'success' : 'danger'" size="small">
              {{ detail.success ? '成功' : '失败' }}（HTTP {{ detail.statusCode }}）
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.durationMs }} ms</el-descriptions-item>
          <el-descriptions-item label="客户端 IP">{{ detail.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="User-Agent">{{ detail.userAgent || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMessage" label="错误摘要">
            <span class="audit-error">{{ detail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { auditLogApi } from '@/api/auditLog'
import { exportApi } from '@/api/export'
import { downloadResponse } from '@/utils/download'

const ACTION_LABELS = {
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
  COMMAND: '下发命令',
  SHADOW_WRITE: '写影子',
  ENABLE: '启用',
  DISABLE: '禁用',
  RESET_SECRET: '重置密钥',
  EXPORT_CREDENTIALS: '导出凭据',
  IMPORT: '导入',
  ACK: '确认告警',
  RECOVER: '恢复告警',
  ASSIGN: '指派告警',
  RUN: '手动触发',
  TEST: '试运行',
  RETRY: '重试',
  REPLAY: '重放死信',
  DISCARD: '丢弃死信',
  PAUSE: '暂停任务',
  RESUME: '恢复任务',
  TERMINATE: '终止任务',
  ADVANCE: '推进批次',
  ROTATE: '轮换分享',
  ACTIVATE: '设备激活',
  ATTACH_SUB_DEVICE: '挂载子设备',
  DETACH_SUB_DEVICE: '解绑子设备',
  CHANGE_NODE_TYPE: '变更节点类型',
  ADD_MEMBER: '添加成员',
  UPDATE_MEMBER: '修改成员',
  REMOVE_MEMBER: '移除成员',
  ISSUE_CERTIFICATE: '签发证书',
  REVOKE_CERTIFICATE: '吊销证书',
  ISSUE_TOKEN: '签发令牌',
  MARK_READ: '标记已读',
  MARK_READ_ALL: '全部已读',
  SIMULATE: '模拟上行',
  LOGIN: '登录',
  LOGOUT: '登出',
  REGISTER: '注册',
  CHANGE_PASSWORD: '修改密码',
  OTHER: '其他'
}

const RESOURCE_LABELS = {
  DEVICE: '设备',
  DEVICE_BATCH: '设备批量',
  DEVICE_REGISTRATION: '设备注册',
  PRODUCT: '产品',
  RULE: '消息规则',
  SCENE: '场景',
  ALERT: '告警',
  ALERT_ESCALATION: '告警升级策略',
  OTA_FIRMWARE: 'OTA 固件',
  OTA_TASK: 'OTA 任务',
  DEVICE_GROUP: '设备分组',
  DEVICE_TAG: '设备标签',
  API_KEY: 'API 密钥',
  WEBHOOK: 'Webhook',
  NOTIFICATION_CHANNEL: '通知渠道',
  NOTIFICATION: '站内信',
  DASHBOARD: '看板',
  HMI_VIEW: '上位机',
  DEAD_LETTER: '死信',
  AUTH: '认证',
  OTHER: '其他'
}

const SOURCE_LABELS = {
  CONSOLE: '控制台',
  OPEN_API: '开放 API',
  DEVICE: '设备',
  ANONYMOUS: '匿名'
}

const filters = reactive({
  username: '',
  action: '',
  resourceType: '',
  source: '',
  success: null,
  keyword: '',
  range: []
})

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)

function buildParams() {
  return {
    username: filters.username || undefined,
    action: filters.action || undefined,
    resourceType: filters.resourceType || undefined,
    source: filters.source || undefined,
    success: filters.success === true || filters.success === false ? filters.success : undefined,
    keyword: filters.keyword || undefined,
    startTime: filters.range?.[0] || undefined,
    endTime: filters.range?.[1] || undefined
  }
}

async function load(targetPage = page.value) {
  page.value = targetPage
  loading.value = true
  try {
    const result = await auditLogApi.list({
      ...buildParams(),
      pageNum: targetPage,
      pageSize: size.value
    })
    rows.value = result.records || []
    total.value = result.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  load(1)
}

function handleReset() {
  filters.username = ''
  filters.action = ''
  filters.resourceType = ''
  filters.source = ''
  filters.success = null
  filters.keyword = ''
  filters.range = []
  load(1)
}

async function handleExport() {
  try {
    const response = await exportApi.auditLogs({ ...buildParams(), limit: 10000 })
    downloadResponse(response, 'audit-logs.csv')
    ElMessage.success('导出已开始')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(() => load(1))
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  flex-wrap: wrap;
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-item {
  width: 160px;
}

.filter-item--small {
  width: 120px;
}

.filter-item--wide {
  width: 340px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.audit-table {
  cursor: pointer;
}

.audit-resource-id {
  margin-left: var(--spacing-xs);
  padding: 0 4px;
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.audit-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}

.audit-error {
  color: var(--color-danger);
  word-break: break-all;
}
</style>