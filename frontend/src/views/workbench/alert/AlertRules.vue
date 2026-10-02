<template>
  <div class="alert-rules-page page">
    <PageHeader title="告警规则" desc="配置阈值 / 离线 / 事件三类告警的触发条件与抑制窗口">
      <template #actions>
        <el-button type="primary" @click="openCreateDrawer">
          <svg-icon name="add" :size="16" />
          新建规则
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-select
        :model-value="sourceType"
        placeholder="全部来源"
        clearable
        class="filter-item"
        @update:model-value="setSourceType"
      >
        <el-option
          v-for="option in SOURCE_TYPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        :model-value="enabled"
        placeholder="全部状态"
        clearable
        class="filter-item"
        @update:model-value="setEnabled"
      >
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <div class="filter-actions">
        <el-button :loading="loading" @click="refresh">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="rules.length === 0"
      description="暂无告警规则，点击「新建规则」开始配置"
    />

    <div v-else class="rule-table card">
      <el-table :data="rules" style="width: 100%">
        <el-table-column prop="name" label="规则名称" min-width="140" />
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ SOURCE_TYPE_LABELS[row.sourceType] || row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="SEVERITY_TAG_TYPES[row.severity] || 'info'">
              {{ SEVERITY_LABELS[row.severity] || row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发条件" min-width="180">
          <template #default="{ row }">
            <span class="rule-condition">{{ formatRuleCondition(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="作用设备" min-width="120">
          <template #default="{ row }">
            <span>{{ deviceLabel(row.deviceId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="抑制窗口" width="110">
          <template #default="{ row }">
            <span>{{ row.suppressWindowSeconds > 0 ? `${row.suppressWindowSeconds}s` : '默认' }}</span>
          </template>
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
            <el-button link type="primary" @click="openEditDrawer(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无告警规则" :image-size="80" />
        </template>
      </el-table>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="editingId ? '编辑告警规则' : '新建告警规则'"
      size="480px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="rule-form" @submit.prevent>
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="如：高温告警" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="来源类型" required>
            <el-select v-model="form.sourceType" style="width: 100%">
              <el-option
                v-for="option in SOURCE_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="告警级别" required>
            <el-select v-model="form.severity" style="width: 100%">
              <el-option
                v-for="option in SEVERITY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <template v-if="form.sourceType === 'THRESHOLD'">
          <el-form-item label="属性标识符" required>
            <el-input v-model="form.identifier" maxlength="64" placeholder="如：temperature" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="比较符" required>
              <el-select v-model="form.operator" style="width: 100%">
                <el-option
                  v-for="option in OPERATOR_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="阈值" required>
              <el-input v-model="form.thresholdValue" placeholder="如：80" />
            </el-form-item>
          </div>
        </template>

        <template v-else-if="form.sourceType === 'OFFLINE'">
          <el-form-item label="离线持续时长（秒）">
            <el-input-number
              v-model="form.offlineSeconds"
              :min="0"
              :max="86400"
              controls-position="right"
              style="width: 100%"
            />
            <p class="form-hint">0 表示设备状态变为离线即告警</p>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="事件标识符" required>
            <el-input v-model="form.identifier" maxlength="64" placeholder="如：overtemp" />
          </el-form-item>
          <el-form-item label="事件类型（可选）">
            <el-select v-model="form.eventType" clearable placeholder="全部事件类型" style="width: 100%">
              <el-option
                v-for="option in EVENT_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item label="作用设备">
          <el-select v-model="form.deviceId" clearable placeholder="全部设备" style="width: 100%">
            <el-option
              v-for="device in devices"
              :key="device.id"
              :label="device.deviceName || device.deviceKey"
              :value="device.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="抑制窗口（秒）">
          <el-input-number
            v-model="form.suppressWindowSeconds"
            :min="0"
            :max="86400"
            controls-position="right"
            style="width: 100%"
          />
          <p class="form-hint">窗口内重复触发只累加次数、不重复通知；0 表示使用全局默认</p>
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'
import { useAlertRulesQuery, useAlertRuleMutations } from '@/composables/useAlerts'
import {
  SOURCE_TYPE_LABELS,
  SOURCE_TYPE_OPTIONS,
  SEVERITY_LABELS,
  SEVERITY_TAG_TYPES,
  SEVERITY_OPTIONS,
  OPERATOR_OPTIONS,
  EVENT_TYPE_OPTIONS,
  formatRuleCondition
} from '@/utils/alert'

const NUMERIC_OPERATORS = new Set(['GT', 'GTE', 'LT', 'LTE'])

const { sourceType, enabled, rules, loading, refresh } = useAlertRulesQuery()
const { saving, createRule, updateRule, deleteRule } = useAlertRuleMutations()

const devices = ref([])
const drawerVisible = ref(false)
const editingId = ref(null)
const togglingId = ref(null)

const form = reactive({
  name: '',
  sourceType: 'THRESHOLD',
  severity: 'WARNING',
  deviceId: null,
  identifier: '',
  operator: 'GT',
  thresholdValue: '',
  eventType: '',
  offlineSeconds: 0,
  suppressWindowSeconds: 0,
  enabled: 1
})

function setSourceType(value) {
  sourceType.value = value || ''
}
function setEnabled(value) {
  enabled.value = value || ''
}

function deviceLabel(deviceId) {
  if (!deviceId) return '全部设备'
  const matched = devices.value.find((item) => item.id === deviceId)
  return matched ? matched.deviceName || matched.deviceKey : `#${deviceId}`
}

function resetForm() {
  Object.assign(form, {
    name: '',
    sourceType: 'THRESHOLD',
    severity: 'WARNING',
    deviceId: null,
    identifier: '',
    operator: 'GT',
    thresholdValue: '',
    eventType: '',
    offlineSeconds: 0,
    suppressWindowSeconds: 0,
    enabled: 1
  })
}

function openCreateDrawer() {
  editingId.value = null
  resetForm()
  drawerVisible.value = true
}

function openEditDrawer(rule) {
  editingId.value = rule.id
  Object.assign(form, {
    name: rule.name || '',
    sourceType: rule.sourceType || 'THRESHOLD',
    severity: rule.severity || 'WARNING',
    deviceId: rule.deviceId ?? null,
    identifier: rule.identifier || '',
    operator: rule.operator || 'GT',
    thresholdValue: rule.thresholdValue || '',
    eventType: rule.eventType || '',
    offlineSeconds: rule.offlineSeconds ?? 0,
    suppressWindowSeconds: rule.suppressWindowSeconds ?? 0,
    enabled: rule.enabled ?? 1
  })
  drawerVisible.value = true
}

function buildPayload() {
  const payload = {
    name: form.name.trim(),
    sourceType: form.sourceType,
    severity: form.severity,
    deviceId: form.deviceId ?? null,
    suppressWindowSeconds: form.suppressWindowSeconds ?? 0,
    enabled: form.enabled
  }
  if (form.sourceType === 'THRESHOLD') {
    payload.identifier = form.identifier.trim()
    payload.operator = form.operator
    payload.thresholdValue = String(form.thresholdValue).trim()
  } else if (form.sourceType === 'OFFLINE') {
    payload.offlineSeconds = form.offlineSeconds ?? 0
  } else {
    payload.identifier = form.identifier.trim()
    payload.eventType = form.eventType || null
  }
  return payload
}

function validateForm() {
  if (!form.name.trim()) return '请填写规则名称'
  if (form.sourceType === 'THRESHOLD') {
    if (!form.identifier.trim()) return '请填写属性标识符'
    const threshold = String(form.thresholdValue).trim()
    if (!threshold) return '请填写阈值'
    if (NUMERIC_OPERATORS.has(form.operator) && Number.isNaN(Number(threshold))) {
      return '数值比较符下阈值必须是数字'
    }
  }
  if (form.sourceType === 'EVENT' && !form.identifier.trim()) return '请填写事件标识符'
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
      await updateRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    drawerVisible.value = false
  } catch {
    // 校验失败等具体原因已由 axios 拦截器统一提示
  }
}

/** 启停开关：以整条规则为基准改 enabled，避免只提交单字段被服务端判为非法配置 */
async function handleToggle(rule, value) {
  togglingId.value = rule.id
  try {
    await updateRule(rule.id, { ...ruleToPayload(rule), enabled: value ? 1 : 0 })
    ElMessage.success(value ? '规则已启用' : '规则已停用')
  } catch {
    // 失败时列表缓存未失效，开关会回弹到原值
  } finally {
    togglingId.value = null
  }
}

function ruleToPayload(rule) {
  return {
    name: rule.name,
    sourceType: rule.sourceType,
    severity: rule.severity,
    deviceId: rule.deviceId ?? null,
    identifier: rule.identifier,
    operator: rule.operator,
    thresholdValue: rule.thresholdValue,
    eventType: rule.eventType,
    offlineSeconds: rule.offlineSeconds ?? 0,
    suppressWindowSeconds: rule.suppressWindowSeconds ?? 0,
    enabled: rule.enabled
  }
}

async function handleDelete(rule) {
  try {
    await ElMessageBox.confirm(`确定要删除规则「${rule.name}」吗？历史告警记录不受影响。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteRule(rule.id)
    ElMessage.success('规则已删除')
  } catch {
    // 失败原因由拦截器提示
  }
}

async function loadDevices() {
  try {
    const page = unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
    devices.value = page.records || []
  } catch {
    devices.value = []
  }
}

onMounted(() => {
  loadDevices()
})
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
  width: 180px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.rule-table {
  padding: var(--spacing-sm);
}

.rule-condition {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rule-form :deep(.el-form-item) {
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