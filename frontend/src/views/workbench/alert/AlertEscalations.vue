<template>
  <div class="alert-escalations-page page">
    <PageHeader title="升级策略" desc="告警超时未确认时自动升级级别、指派处理人并扩大通知范围">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <svg-icon name="add" :size="16" />
          新建策略
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="escalations.length === 0"
      description="暂无升级策略，点击「新建策略」开始配置"
    />

    <div v-else class="escalation-table card">
      <el-table :data="escalations" style="width: 100%">
        <el-table-column prop="name" label="策略名称" min-width="140" />
        <el-table-column label="适用级别" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">
              {{ ESCALATION_SEVERITY_LABELS[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="未确认时长" width="120">
          <template #default="{ row }">{{ formatAfterSeconds(row.afterSeconds) }}</template>
        </el-table-column>
        <el-table-column label="处理人" width="120">
          <template #default="{ row }">
            <span>{{ row.assigneeId ? `用户 #${row.assigneeId}` : '不指派' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="通知渠道" min-width="160">
          <template #default="{ row }">{{ formatChannelIds(row.channelIds) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              :loading="togglingId === row.id"
              @change="(value) => handleToggle(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无升级策略" :image-size="80" />
        </template>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑升级策略' : '新建升级策略'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="escalation-form" @submit.prevent>
        <el-form-item label="策略名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="如：严重告警 5 分钟未确认" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="适用级别" required>
            <el-select v-model="form.severity" style="width: 100%">
              <el-option
                v-for="option in ESCALATION_SEVERITY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="未确认时长（秒）" required>
            <el-input-number
              v-model="form.afterSeconds"
              :min="60"
              :max="86400"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="升级处理人">
          <el-input-number
            v-model="form.assigneeId"
            :min="1"
            :controls="false"
            placeholder="处理人用户ID，留空表示不指派"
            style="width: 100%"
          />
          <p class="form-hint">填写系统内用户的 ID；留空则只升级级别与通知范围，不改变处理人</p>
        </el-form-item>

        <el-form-item label="升级时通知渠道">
          <el-select v-model="form.channelIds" multiple clearable style="width: 100%" placeholder="默认按渠道自身的事件订阅投递">
            <el-option
              v-for="channel in channels"
              :key="channel.id"
              :label="`${channel.name}（${CHANNEL_TYPE_LABELS[channel.channelType] || channel.channelType}）`"
              :value="channel.id"
            />
          </el-select>
          <p class="form-hint">仅可选取本人已配置的渠道；未选择时升级通知按各渠道的事件订阅投递</p>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
import { useAlertEscalationsQuery, useAlertEscalationMutations } from '@/composables/useAlertEscalations'
import { useNotificationChannelsQuery } from '@/composables/useNotificationChannels'
import {
  CHANNEL_TYPE_LABELS,
  ESCALATION_SEVERITY_LABELS,
  ESCALATION_SEVERITY_OPTIONS,
  parseJson
} from '@/utils/notification'

const { escalations, loading } = useAlertEscalationsQuery()
const { channels } = useNotificationChannelsQuery()
const { saving, createEscalation, updateEscalation, deleteEscalation } = useAlertEscalationMutations()

const dialogVisible = ref(false)
const editingId = ref(null)
const togglingId = ref(null)

const form = reactive({
  name: '',
  severity: 'CRITICAL',
  afterSeconds: 300,
  assigneeId: null,
  channelIds: [],
  enabled: true
})

function resetForm() {
  Object.assign(form, {
    name: '',
    severity: 'CRITICAL',
    afterSeconds: 300,
    assigneeId: null,
    channelIds: [],
    enabled: true
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(policy) {
  editingId.value = policy.id
  Object.assign(form, {
    name: policy.name || '',
    severity: policy.severity || 'CRITICAL',
    afterSeconds: policy.afterSeconds ?? 300,
    assigneeId: policy.assigneeId ?? null,
    channelIds: parseJson(policy.channelIds, []) || [],
    enabled: policy.enabled === 1
  })
  dialogVisible.value = true
}

function buildPayload() {
  return {
    name: form.name.trim(),
    severity: form.severity,
    afterSeconds: form.afterSeconds,
    assigneeId: form.assigneeId ?? null,
    channelIds: form.channelIds.length > 0 ? form.channelIds : null,
    enabled: form.enabled
  }
}

function validateForm() {
  if (!form.name.trim()) return '请填写策略名称'
  if (!form.afterSeconds || form.afterSeconds < 60) return '未确认时长不得小于 60 秒'
  return ''
}

async function handleSubmit() {
  const error = validateForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateEscalation(editingId.value, payload)
      ElMessage.success('策略已更新')
    } else {
      await createEscalation(payload)
      ElMessage.success('策略已创建')
    }
    dialogVisible.value = false
  } catch {
    // 校验失败等具体原因已由 axios 拦截器统一提示
  }
}

/** 启停开关：以整条策略为基准改 enabled，避免只提交单字段被服务端判为非法配置 */
async function handleToggle(policy, value) {
  togglingId.value = policy.id
  try {
    await updateEscalation(policy.id, {
      name: policy.name,
      severity: policy.severity,
      afterSeconds: policy.afterSeconds,
      assigneeId: policy.assigneeId ?? null,
      channelIds: parseJson(policy.channelIds, []) || [],
      enabled: value
    })
    ElMessage.success(value ? '策略已启用' : '策略已停用')
  } catch {
    // 失败时列表缓存未失效，开关会回弹到原值
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(policy) {
  try {
    await ElMessageBox.confirm(`确定要删除策略「${policy.name}」吗？历史告警不受影响。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteEscalation(policy.id)
    ElMessage.success('策略已删除')
  } catch {
    // 失败原因由拦截器提示
  }
}

function formatAfterSeconds(seconds) {
  if (!seconds || seconds <= 0) return '—'
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`
  return `${seconds} 秒`
}

function formatChannelIds(channelIds) {
  const ids = parseJson(channelIds, [])
  if (!Array.isArray(ids) || ids.length === 0) return '按事件订阅'
  const names = ids.map((id) => {
    const matched = channels.value.find((channel) => channel.id === id)
    return matched ? matched.name : `#${id}`
  })
  return names.join('、')
}
</script>

<style scoped>
.escalation-table {
  padding: var(--spacing-sm);
}

.escalation-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}

.form-hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.5;
}
</style>