/**
 * Rule canvas projection and write-back. The existing RuleEditor form remains
 * the source of truth; this module only derives graph data and applies edits.
 */

import {
  NODE_TYPE,
  formatCooldownEdgeLabel
} from '@/utils/orchestration'
import {
  ACTION_TYPE_LABELS,
  SOURCE_TYPE_LABELS,
  formatActionSummary,
  formatTriggerCondition,
  buildRuleActionConfig
} from '@/utils/rule'
import { normalizeRuleScope } from '@/utils/rule'
import { formatScopeLabel, SCOPE_TYPE_LABELS } from '@/utils/scope'

const LAYOUT = {
  trigger: { x: 0, y: 0 },
  scope: { x: 360, y: 0 },
  action: { x: 720, y: 0 }
}

const EDGE_TYPE = 'smoothstep'

const TRIGGER_FIELDS = [
  'sourceType',
  'identifier',
  'operator',
  'thresholdValue',
  'eventType'
]
const SCOPE_FIELDS = ['scopeType', 'deviceId', 'groupIds', 'tagIds']
const ACTION_FIELDS = [
  'actionType',
  'targetIdentifier',
  'targetValue',
  'commandType',
  'commandParams',
  'topic',
  'qos',
  'url',
  'method',
  'headers',
  'payloadTemplate'
]

function createNode(id, type, position, title, summary, status) {
  return {
    id,
    type,
    position: { ...position },
    draggable: false,
    data: { key: id, title, summary, status }
  }
}

function createEdge(source, target, label = '') {
  return { id: `${source}-${target}`, source, target, type: EDGE_TYPE, label }
}

/** Convert a RuleEditor form into its fixed trigger → scope → action graph. */
export function projectRule(form, candidates = {}) {
  if (!form) return { nodes: [], edges: [] }

  const sourceType = form.sourceType || 'PROPERTY'
  const scopeType = form.scopeType || 'ALL'
  const actionType = form.actionType
  let actionSummary
  try {
    actionSummary = formatActionSummary({ actionType, actionConfig: buildRuleActionConfig(form) })
  } catch {
    // JSON fields are edited as text; an incomplete value must not break the canvas render.
    actionSummary = ACTION_TYPE_LABELS[actionType] || actionType || '动作'
  }

  const nodes = [
    createNode(
      NODE_TYPE.TRIGGER,
      NODE_TYPE.TRIGGER,
      LAYOUT.trigger,
      '触发',
      formatTriggerCondition(form),
      { label: SOURCE_TYPE_LABELS[sourceType] || sourceType, type: 'primary' }
    ),
    createNode(
      NODE_TYPE.SCOPE,
      NODE_TYPE.SCOPE,
      LAYOUT.scope,
      '作用范围',
      formatScopeLabel({ ...form, scopeType }, candidates),
      { label: SCOPE_TYPE_LABELS[scopeType] || scopeType, type: 'primary' }
    ),
    createNode(
      NODE_TYPE.ACTION,
      NODE_TYPE.ACTION,
      LAYOUT.action,
      '动作',
      actionSummary,
      { label: ACTION_TYPE_LABELS[actionType] || actionType || '动作', type: 'success' }
    )
  ]

  return {
    nodes,
    edges: [
      createEdge(NODE_TYPE.TRIGGER, NODE_TYPE.SCOPE, formatScopeLabel({ ...form, scopeType }, candidates)),
      createEdge(NODE_TYPE.SCOPE, NODE_TYPE.ACTION, formatCooldownEdgeLabel(form.cooldownSeconds))
    ]
  }
}

function copyFields(target, patch, fields) {
  let changed = false
  fields.forEach((field) => {
    if (Object.prototype.hasOwnProperty.call(patch, field)) {
      target[field] = patch[field]
      changed = true
    }
  })
  return changed
}

/** Apply a property-panel edit to the form; rule graph structure is fixed. */
export function applyRuleEvent(form, event) {
  if (!form || !event || event.type !== 'node-update' || !event.patch) return false

  switch (event.id) {
    case NODE_TYPE.TRIGGER:
      return copyFields(form, event.patch, TRIGGER_FIELDS)
    case NODE_TYPE.SCOPE:
      {
        const changed = copyFields(form, event.patch, SCOPE_FIELDS)
        if (changed) normalizeRuleScope(form)
        return changed
      }
    case NODE_TYPE.ACTION:
      return copyFields(form, event.patch, ACTION_FIELDS)
    default:
      return false
  }
}
