<template>
  <el-drawer
    :model-value="modelValue"
    :title="editingId ? '编辑消息规则' : '新建消息规则'"
    :size="drawerSize"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <el-radio-group v-model="viewMode" size="small" class="rule-editor__view-toggle" aria-label="规则编辑视图">
      <el-radio-button value="form">表单</el-radio-button>
      <el-radio-button value="canvas">画布</el-radio-button>
    </el-radio-group>

    <el-form v-if="viewMode === 'form'" label-position="top" class="rule-form" @submit.prevent>
      <div class="form-section-title">① 基础信息</div>
      <el-form-item label="规则名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="如：高温联动降温" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="255"
          placeholder="可选，说明规则的用途"
        />
      </el-form-item>
      <el-form-item label="作用范围" required>
        <el-select v-model="form.scopeType" style="width: 100%" @change="handleScopeTypeChange">
          <el-option
            v-for="option in SCOPE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.scopeType === 'DEVICE'" label="作用设备" required>
        <el-select v-model="form.deviceId" clearable filterable placeholder="选择设备" style="width: 100%">
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
      </el-form-item>

      <template v-else-if="form.scopeType === 'GROUP'">
        <el-form-item label="作用分组" required>
          <el-select v-model="form.groupIds" multiple filterable clearable placeholder="选择分组" style="width: 100%">
            <el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" />
          </el-select>
          <p class="form-hint">按分组作用时，含子分组的设备一并生效</p>
        </el-form-item>
      </template>

      <el-form-item v-else-if="form.scopeType === 'TAG'" label="作用标签" required>
        <el-select v-model="form.tagIds" multiple filterable clearable placeholder="选择标签" style="width: 100%">
          <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </el-form-item>

      <div class="form-section-title">② 触发条件</div>
      <el-form-item label="条件组合">
        <el-radio-group v-model="form.conditionLogic" :disabled="!form.extraConditions.length">
          <el-radio-button value="AND">全部满足</el-radio-button>
          <el-radio-button value="OR">任一满足</el-radio-button>
        </el-radio-group>
        <span v-if="!form.extraConditions.length" class="extra-hint">添加第二个条件后可切换组合方式</span>
      </el-form-item>
      <el-form-item label="触发源" required>
        <el-select v-model="form.sourceType" style="width: 100%">
          <el-option
            v-for="option in SOURCE_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="form.sourceType === 'EVENT' ? '事件标识符' : '属性标识符'" required>
        <el-input
          v-model="form.identifier"
          maxlength="64"
          :placeholder="form.sourceType === 'EVENT' ? '如：overtemp' : '如：temperature'"
        />
      </el-form-item>

      <template v-if="form.sourceType === 'PROPERTY'">
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
            <el-input v-model="form.thresholdValue" placeholder="如：40" />
          </el-form-item>
        </div>
      </template>

      <el-form-item v-else label="事件类型（可选）">
        <el-select v-model="form.eventType" clearable placeholder="全部事件类型" style="width: 100%">
          <el-option
            v-for="option in EVENT_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <div v-if="form.extraConditions.length" class="form-section-title">
        附加条件（与上方条件{{ form.conditionLogic === 'OR' ? '任一' : '全部' }}满足即触发）
      </div>
      <el-form-item
        v-for="(condition, index) in form.extraConditions"
        :key="`cond-${index}`"
        :label="`附加条件 ${index + 1}`"
      >
        <div class="extra-item">
          <el-input
            v-model="condition.identifier"
            maxlength="64"
            placeholder="标识符"
            class="extra-item__identifier"
          />
          <el-select v-if="form.sourceType !== 'EVENT'" v-model="condition.operator" class="extra-item__op">
            <el-option
              v-for="option in OPERATOR_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-if="form.sourceType !== 'EVENT'"
            v-model="condition.thresholdValue"
            placeholder="阈值"
            class="extra-item__value"
          />
          <el-select v-else v-model="condition.eventType" clearable placeholder="事件类型" class="extra-item__value">
            <el-option label="info" value="info" />
            <el-option label="alert" value="alert" />
            <el-option label="fault" value="fault" />
          </el-select>
          <el-button link type="danger" @click="removeCondition(index)">移除</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button
          :disabled="form.extraConditions.length >= MAX_CONDITIONS"
          @click="addCondition"
        >
          + 添加条件
        </el-button>
        <span v-if="form.extraConditions.length >= MAX_CONDITIONS" class="extra-hint">
          条件数已达上限 {{ MAX_CONDITIONS }}
        </span>
      </el-form-item>

      <div class="form-section-title">③ 执行动作</div>
      <el-form-item label="动作类型" required>
        <el-select v-model="form.actionType" style="width: 100%">
          <el-option
            v-for="option in ACTION_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>

      <template v-if="form.actionType === 'UPDATE_PROPERTY'">
        <div class="form-row">
          <el-form-item label="目标属性" required>
            <el-input v-model="form.targetIdentifier" maxlength="64" placeholder="如：switch" />
          </el-form-item>
          <el-form-item label="写入值" required>
            <el-input v-model="form.targetValue" placeholder="如：on" />
          </el-form-item>
        </div>
      </template>

      <template v-else-if="form.actionType === 'SEND_COMMAND'">
        <el-form-item label="命令类型" required>
          <el-select v-model="form.commandType" style="width: 100%">
            <el-option label="属性设置 (property_set)" value="property_set" />
            <el-option label="服务调用 (service)" value="service" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标标识符" required>
          <el-input v-model="form.targetIdentifier" maxlength="64" placeholder="属性标识符或服务标识符" />
        </el-form-item>
        <el-form-item label="命令参数（JSON，可选）">
          <el-input
            v-model="form.commandParams"
            type="textarea"
            :rows="3"
            placeholder="如：{&quot;power&quot;: 1}"
          />
        </el-form-item>
      </template>

      <template v-else-if="form.actionType === 'FORWARD_MQTT'">
        <el-form-item label="目标 Topic" required>
          <el-input v-model="form.topic" maxlength="255" placeholder="如：factory/line1/alert" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="QoS">
            <el-select v-model="form.qos" style="width: 100%">
              <el-option label="0" :value="0" />
              <el-option label="1" :value="1" />
              <el-option label="2" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="载荷模板（可选）">
            <el-input v-model="form.payloadTemplate" placeholder="支持 ${value} 等占位符" />
          </el-form-item>
        </div>
      </template>

      <template v-else>
        <el-form-item label="目标 URL" required>
          <el-input v-model="form.url" maxlength="512" placeholder="https://erp.example.com/hook" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="请求方法">
            <el-select v-model="form.method" style="width: 100%">
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
            </el-select>
          </el-form-item>
          <el-form-item label="载荷模板（可选）">
            <el-input v-model="form.payloadTemplate" placeholder="支持 ${value} 等占位符" />
          </el-form-item>
        </div>
        <el-form-item label="请求头（JSON，可选）">
          <el-input
            v-model="form.headers"
            type="textarea"
            :rows="3"
            placeholder="如：{&quot;X-Tenant&quot;: &quot;acme&quot;}"
          />
        </el-form-item>
      </template>

      <div v-if="form.extraActions.length" class="form-section-title">附加动作（命中后依次执行）</div>
      <el-form-item
        v-for="(action, index) in form.extraActions"
        :key="`act-${index}`"
        :label="`附加动作 ${index + 1}`"
      >
        <div class="extra-item extra-item--action">
          <el-select v-model="action.actionType" class="extra-item__identifier">
            <el-option
              v-for="option in ACTION_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-if="action.actionType === 'FORWARD_HTTP'"
            v-model="action.url"
            placeholder="https://erp.example.com/hook"
            class="extra-item__value"
          />
          <el-input
            v-else-if="action.actionType === 'FORWARD_MQTT'"
            v-model="action.topic"
            placeholder="factory/line1"
            class="extra-item__value"
          />
          <el-input
            v-else
            v-model="action.targetIdentifier"
            maxlength="64"
            :placeholder="action.actionType === 'UPDATE_PROPERTY' ? '目标属性' : '目标标识符'"
            class="extra-item__value"
          />
          <el-input
            v-if="action.actionType === 'UPDATE_PROPERTY'"
            v-model="action.targetValue"
            placeholder="写入值"
            class="extra-item__value"
          />
          <el-button link type="danger" @click="removeAction(index)">移除</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button :disabled="form.extraActions.length >= MAX_ACTIONS" @click="addAction">
          + 添加动作
        </el-button>
        <span v-if="form.extraActions.length >= MAX_ACTIONS" class="extra-hint">
          动作数已达上限 {{ MAX_ACTIONS }}
        </span>
      </el-form-item>

      <div class="form-section-title">高级</div>
      <el-form-item label="冷却窗口（秒）">
        <el-input-number
          v-model="form.cooldownSeconds"
          :min="0"
          :max="86400"
          controls-position="right"
          style="width: 100%"
        />
        <p class="form-hint">窗口内重复命中只触发一次；0 表示不限制</p>
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <div v-else class="rule-editor__canvas-view">
      <el-form label-position="top" class="rule-editor__canvas-meta" @submit.prevent>
        <el-form-item label="规则名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="如：高温联动降温" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" placeholder="可选，说明规则的用途" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="冷却窗口（秒）">
            <el-input-number
              v-model="form.cooldownSeconds"
              :min="0"
              :max="86400"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>
      </el-form>
      <RuleOrchestrationView
        ref="canvasViewRef"
        class="rule-orchestration-view"
        :form="form"
        :devices="devices"
        :groups="groups"
        :tags="tags"
      />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :disabled="!editingId" @click="openTestDialog">
        <svg-icon name="bolt" :size="14" />
        试运行
      </el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ editingId ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-drawer>

  <el-dialog v-model="testVisible" title="规则试运行（干跑）" width="480px">
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="设备" required>
        <el-select v-model="testForm.deviceId" placeholder="选择设备" style="width: 100%">
          <el-option
            v-for="device in devices"
            :key="device.id"
            :label="device.deviceName || device.deviceKey"
            :value="device.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标识符" required>
        <el-input v-model="testForm.identifier" placeholder="待判定的属性 / 事件标识符" />
      </el-form-item>
      <el-form-item label="取值">
        <el-input v-model="testForm.valueText" placeholder="数值比较符下填数字，可留空" />
      </el-form-item>
    </el-form>

    <div v-if="testResult" class="test-result">
      <div class="test-row">
        <span class="test-label">命中结论</span>
        <el-tag :type="testResult.matched ? 'success' : 'info'" size="small">
          {{ testResult.matched ? '命中' : '未命中' }}
        </el-tag>
      </div>
      <div class="test-row">
        <span class="test-label">说明</span>
        <span class="test-value">{{ testResult.reason || '—' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">设备建模</span>
        <span class="test-value">{{ testResult.deviceModeled ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">标识符已定义</span>
        <span class="test-value">{{ testResult.identifierModeled ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">动作可执行</span>
        <span class="test-value">{{ testResult.actionExecutable ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">动作摘要</span>
        <span class="test-value">{{ testResult.actionSummary || '—' }}</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="testVisible = false">关闭</el-button>
      <el-button type="primary" :loading="testing" @click="handleTest">开始试运行</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'
import { useRuleMutations, useRuleTestMutation } from '@/composables/useRules'
import { useDeviceGroupListQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { SCOPE_TYPE_OPTIONS, resolveScopeType } from '@/utils/scope'
import {
  SOURCE_TYPE_OPTIONS,
  ACTION_TYPE_OPTIONS,
  OPERATOR_OPTIONS,
  EVENT_TYPE_OPTIONS,
  MAX_ACTIONS,
  MAX_CONDITIONS,
  emptyAction,
  emptyCondition,
  validateAction,
  validateCondition,
  buildRuleActionConfig,
  normalizeRuleScope
} from '@/utils/rule'

const RuleOrchestrationView = defineAsyncComponent(() => import('./RuleOrchestrationView.vue'))

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  rule: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const NUMERIC_OPERATORS = new Set(['GT', 'GTE', 'LT', 'LTE'])

const { saving, createRule, updateRule } = useRuleMutations()
const { testing, testRule } = useRuleTestMutation()
const { groups } = useDeviceGroupListQuery()
const { tags } = useTagListQuery()

const devices = ref([])
const editingId = computed(() => props.rule?.id ?? null)
const viewMode = ref('form')
const canvasViewRef = ref(null)
const drawerSize = computed(() => (viewMode.value === 'canvas' ? 'min(880px, 92vw)' : '560px'))

const testVisible = ref(false)
const testResult = ref(null)
const testForm = reactive({ deviceId: null, identifier: '', valueText: '' })

function emptyForm() {
  return {
    name: '',
    description: '',
    scopeType: 'ALL',
    deviceId: null,
    groupIds: [],
    tagIds: [],
    sourceType: 'PROPERTY',
    identifier: '',
    operator: 'GT',
    thresholdValue: '',
    eventType: '',
    actionType: 'UPDATE_PROPERTY',
    // T-33 多条件 / 多动作：首项仍走上方单值字段（保证单条件规则的提交体与改造前逐位一致），
    // 附加项才进数组；没有附加项时 buildPayload 不下发 conditions / actions 字段。
    conditionLogic: 'AND',
    extraConditions: [],
    extraActions: [],
    targetIdentifier: '',
    targetValue: '',
    commandType: 'property_set',
    commandParams: '',
    topic: '',
    qos: 1,
    url: '',
    method: 'POST',
    headers: '',
    payloadTemplate: '',
    cooldownSeconds: 0,
    enabled: 1
  }
}

const form = reactive(emptyForm())

/** 切档时清掉非当前档位的字段（口径抽到 utils/rule.js，与画布作用域面板共用）。 */
function handleScopeTypeChange() {
  normalizeRuleScope(form)
}

/** 规则实体 → 表单作用域字段；档位缺省按 deviceId 推断，兼容未带 scopeType 的存量行。 */
function scopeFromRule(rule) {
  const scopeType = resolveScopeType(rule)
  return {
    scopeType,
    deviceId: scopeType === 'DEVICE' ? (rule.deviceId ?? null) : null,
    groupIds: scopeType === 'GROUP' ? [...(rule.groupIds || [])] : [],
    tagIds: scopeType === 'TAG' ? [...(rule.tagIds || [])] : []
  }
}

/** 表单作用域字段 → 提交体，仅保留当前档位对应的字段。 */
function buildScopePayload() {
  const scopeType = form.scopeType || 'ALL'
  return {
    scopeType,
    deviceId: scopeType === 'DEVICE' ? (form.deviceId ?? null) : null,
    groupIds: scopeType === 'GROUP' ? [...form.groupIds] : [],
    tagIds: scopeType === 'TAG' ? [...form.tagIds] : []
  }
}

/** 把规则实体回填到表单，actionConfig 按动作类型拆分到各自字段。 */
function fillForm(rule) {
  Object.assign(form, emptyForm())
  if (!rule) return
  const config = rule.actionConfig || {}
  Object.assign(form, {
    name: rule.name || '',
    description: rule.description || '',
    ...scopeFromRule(rule),
    sourceType: rule.sourceType || 'PROPERTY',
    identifier: rule.identifier || '',
    operator: rule.operator || 'GT',
    thresholdValue: rule.thresholdValue || '',
    eventType: rule.eventType || '',
    actionType: rule.actionType || 'UPDATE_PROPERTY',
    conditionLogic: rule.conditionLogic || 'AND',
    cooldownSeconds: rule.cooldownSeconds ?? 0,
    enabled: rule.enabled ?? 1
  })
  // T-33：后端对单条件规则也会回传长度为 1 的 conditions / actions 数组，
  // 这里只把「第 2 项及以后」还原成附加项——首项就是上面已填好的单值字段。
  form.extraConditions = (rule.conditions || [])
    .slice(1)
    .map((item) => ({ ...emptyCondition(rule.sourceType || 'PROPERTY'), ...item }))
  form.extraActions = (rule.actions || []).slice(1).map((item) => ({
    ...emptyAction(item.actionType || 'UPDATE_PROPERTY'),
    ...item
  }))
  if (rule.actionType === 'UPDATE_PROPERTY') {
    form.targetIdentifier = config.identifier || ''
    form.targetValue = config.value ?? ''
  } else if (rule.actionType === 'SEND_COMMAND') {
    form.commandType = config.commandType || 'property_set'
    form.targetIdentifier = config.identifier || ''
    form.commandParams = config.params ? JSON.stringify(config.params) : ''
  } else if (rule.actionType === 'FORWARD_MQTT') {
    form.topic = config.topic || ''
    form.qos = config.qos ?? 1
    form.payloadTemplate = config.payloadTemplate || ''
  } else if (rule.actionType === 'FORWARD_HTTP') {
    form.url = config.url || ''
    form.method = config.method || 'POST'
    form.headers = config.headers ? JSON.stringify(config.headers) : ''
    form.payloadTemplate = config.payloadTemplate || ''
  }
}

watch(
  () => [props.modelValue, props.rule],
  ([visible]) => {
    if (visible) {
      fillForm(props.rule)
      viewMode.value = 'form'
      testResult.value = null
    }
  },
  { immediate: true }
)

function validateForm() {
  if (!form.name.trim()) return '请填写规则名称'
  if (form.scopeType === 'DEVICE' && !form.deviceId) return '请选择作用设备'
  if (form.scopeType === 'GROUP' && form.groupIds.length === 0) return '请选择至少一个作用分组'
  if (form.scopeType === 'TAG' && form.tagIds.length === 0) return '请选择至少一个作用标签'
  if (!form.identifier.trim()) {
    return form.sourceType === 'EVENT' ? '请填写事件标识符' : '请填写属性标识符'
  }
  if (form.sourceType === 'PROPERTY') {
    const threshold = String(form.thresholdValue).trim()
    if (!threshold) return '请填写阈值'
    if (NUMERIC_OPERATORS.has(form.operator) && Number.isNaN(Number(threshold))) {
      return '数值比较符下阈值必须是数字'
    }
  }
  switch (form.actionType) {
    case 'UPDATE_PROPERTY':
      if (!form.targetIdentifier.trim()) return '请填写目标属性标识符'
      if (!String(form.targetValue).trim()) return '请填写写入值'
      break
    case 'SEND_COMMAND':
      if (!form.targetIdentifier.trim()) return '请填写目标标识符'
      break
    case 'FORWARD_MQTT':
      if (!form.topic.trim()) return '请填写目标 Topic'
      if (form.topic.includes('#') || form.topic.includes('+')) return 'Topic 不允许包含通配符 # / +'
      break
    case 'FORWARD_HTTP':
      if (!form.url.trim()) return '请填写目标 URL'
      if (!/^https?:\/\//.test(form.url.trim())) return 'URL 必须以 http:// 或 https:// 开头'
      break
    default:
      break
  }
  if (form.cooldownSeconds < 0 || form.cooldownSeconds > 86400) return '冷却窗口需在 0 ~ 86400 秒'
  return validateExtra()
}

/** 追加一个附加条件；标识符与触发源对齐当前设置，结构与首项一致。 */
function addCondition() {
  if (form.extraConditions.length >= MAX_CONDITIONS) return
  form.extraConditions.push(emptyCondition(form.sourceType))
}

function removeCondition(index) {
  form.extraConditions.splice(index, 1)
}

function addAction() {
  if (form.extraActions.length >= MAX_ACTIONS) return
  form.extraActions.push(emptyAction('UPDATE_PROPERTY'))
}

function removeAction(index) {
  form.extraActions.splice(index, 1)
}

/** 校验附加条件与附加动作；返回首个错误文案，通过则返回空串。 */
function validateExtra() {
  for (let i = 0; i < form.extraConditions.length; i += 1) {
    const error = validateCondition(
      { ...form.extraConditions[i], sourceType: form.sourceType },
      NUMERIC_OPERATORS,
      i + 1
    )
    if (error) return error
  }
  for (const action of form.extraActions) {
    const error = validateAction(action)
    if (error) return error
  }
  return ''
}

/**
 * 组装多条件 / 多动作数组（仅在存在附加项时返回非空）。
 * <p>
 * 首项由表单的单值字段提供，附加项来自数组；**没有附加项时返回 null**，
 * buildPayload 相应地不下发 conditions / actions 字段 —— 单条件规则提交体与改造前逐位一致。
 */
function buildMultiPayload() {
  const conditions =
    form.extraConditions.length > 0
      ? [
          form.sourceType === 'PROPERTY'
            ? {
                identifier: form.identifier.trim(),
                operator: form.operator,
                thresholdValue: String(form.thresholdValue).trim()
              }
            : { identifier: form.identifier.trim(), eventType: form.eventType || null },
          ...form.extraConditions.map((item) =>
            form.sourceType === 'PROPERTY'
              ? {
                  identifier: String(item.identifier || '').trim(),
                  operator: item.operator,
                  thresholdValue: String(item.thresholdValue || '').trim()
                }
              : {
                  identifier: String(item.identifier || '').trim(),
                  eventType: item.eventType || null
                }
          )
        ]
      : null

  const actions =
    form.extraActions.length > 0
      ? [
          { actionType: form.actionType, actionConfig: buildRuleActionConfig(form) },
          ...form.extraActions.map((item) => ({
            actionType: item.actionType,
            actionConfig: buildRuleActionConfig(item)
          }))
        ]
      : null

  return { conditions, actions }
}

function buildPayload() {
  const payload = {
    name: form.name.trim(),
    description: form.description.trim() || null,
    ...buildScopePayload(),
    sourceType: form.sourceType,
    identifier: form.identifier.trim(),
    actionType: form.actionType,
    actionConfig: buildRuleActionConfig(form),
    cooldownSeconds: form.cooldownSeconds ?? 0,
    enabled: form.enabled
  }
  if (form.sourceType === 'PROPERTY') {
    payload.operator = form.operator
    payload.thresholdValue = String(form.thresholdValue).trim()
    payload.eventType = null
  } else {
    payload.operator = null
    payload.thresholdValue = null
    payload.eventType = form.eventType || null
  }
  const multi = buildMultiPayload()
  if (multi.conditions) {
    payload.conditionLogic = form.conditionLogic
    payload.conditions = multi.conditions
  }
  if (multi.actions) {
    payload.actions = multi.actions
  }
  return payload
}

async function handleSubmit() {
  const error = validateForm()
  if (error) {
    if (viewMode.value === 'canvas') canvasViewRef.value?.focusError?.(error)
    ElMessage.warning(error)
    return
  }
  let payload
  try {
    payload = buildPayload()
  } catch (e) {
    ElMessage.warning(e.message)
    return
  }
  try {
    if (editingId.value) {
      await updateRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    emit('saved')
    emit('update:modelValue', false)
  } catch {
    // 具体失败原因已由 axios 拦截器统一提示
  }
}

function openTestDialog() {
  testForm.deviceId = form.deviceId ?? null
  testForm.identifier = form.identifier
  testForm.valueText = ''
  testResult.value = null
  testVisible.value = true
}

async function handleTest() {
  if (!testForm.deviceId) {
    ElMessage.warning('请选择试运行设备')
    return
  }
  if (!testForm.identifier.trim()) {
    ElMessage.warning('请填写标识符')
    return
  }
  try {
    testResult.value = await testRule(editingId.value, {
      deviceId: testForm.deviceId,
      identifier: testForm.identifier.trim(),
      valueText: testForm.valueText
    })
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

onMounted(loadDevices)
</script>

<style scoped>
/* 附加条件 / 动作：一行内放标识符 + 比较符 + 阈值 + 移除，窄屏自然换行 */
.extra-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  width: 100%;
}

.extra-item--action {
  flex-wrap: wrap;
}

.extra-item__identifier {
  flex: 2 1 140px;
  min-width: 0;
}

.extra-item__op {
  flex: 0 0 96px;
}

.extra-item__value {
  flex: 1 1 120px;
  min-width: 0;
}

.extra-hint {
  margin-left: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
.rule-editor__view-toggle {
  margin-bottom: var(--spacing-md);
}

.rule-editor__canvas-view {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.rule-editor__canvas-meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: var(--spacing-md);
}

.rule-editor__canvas-meta :deep(.el-form-item) {
  margin-bottom: var(--spacing-sm);
}

.rule-editor__canvas-meta :deep(.el-form-item:nth-child(2)) {
  grid-column: 1 / -1;
}

.rule-editor__canvas-view :deep(.rule-orchestration-view) {
  min-height: 480px;
}

.form-section-title {
  margin: var(--spacing-md) 0 var(--spacing-sm);
  padding-left: var(--spacing-sm);
  border-left: 3px solid var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
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

.test-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius);
}

.test-row {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.test-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.test-value {
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}
</style>
