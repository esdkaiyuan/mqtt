/**
 * 物模型（TSL）前端辅助：空模型构造、数据类型默认值、展示摘要。
 *
 * 结构定义见 T-14 设计文档 §4；前端只负责生成结构合法的 JSON，
 * 标识符规范、数量上限、取值范围等仍以服务端校验（`ThingModelValidator`）为准。
 */

export const SCHEMA_VERSION = '1.0'

export const DATA_TYPES = [
  { value: 'int', label: '整数 int' },
  { value: 'float', label: '单精度 float' },
  { value: 'double', label: '双精度 double' },
  { value: 'bool', label: '布尔 bool' },
  { value: 'text', label: '文本 text' },
  { value: 'date', label: '时间 date' },
  { value: 'enum', label: '枚举 enum' },
  { value: 'struct', label: '结构体 struct' },
  { value: 'array', label: '数组 array' }
]

export const EVENT_TYPES = [
  { value: 'info', label: '信息 info' },
  { value: 'alert', label: '告警 alert' },
  { value: 'fault', label: '故障 fault' }
]

export const CALL_TYPES = [
  { value: 'async', label: '异步 async' },
  { value: 'sync', label: '同步 sync' }
]

export const ACCESS_MODES = [
  { value: 'r', label: '只读 r' },
  { value: 'rw', label: '读写 rw' }
]

const DATA_TYPE_LABELS = Object.fromEntries(DATA_TYPES.map((item) => [item.value, item.label]))

export function createEmptyThingModel() {
  return { schemaVersion: SCHEMA_VERSION, properties: [], events: [], services: [] }
}

/** 按数据类型给出默认约束，避免新增后立即因缺字段被服务端拒绝。 */
export function createDataType(type = 'int') {
  switch (type) {
    case 'int':
      return { type: 'int', min: 0, max: 100, step: 1, unit: '' }
    case 'float':
    case 'double':
      return { type, min: 0, max: 100, step: 0.1, unit: '' }
    case 'bool':
      return { type: 'bool' }
    case 'text':
      return { type: 'text', length: 64 }
    case 'date':
      return { type: 'date' }
    case 'enum':
      return { type: 'enum', specs: { 0: '选项一' } }
    case 'struct':
      return { type: 'struct', specs: [createParam(false)] }
    case 'array':
      return { type: 'array', item: { type: 'int', min: 0, max: 100, step: 1, unit: '' } }
    default:
      return { type: 'int', min: 0, max: 100, step: 1, unit: '' }
  }
}

export function createParam(required = false) {
  const param = { identifier: '', name: '', dataType: createDataType('int') }
  if (required) param.required = true
  return param
}

export function createProperty() {
  return {
    identifier: '',
    name: '',
    dataType: createDataType('int'),
    accessMode: 'r',
    required: false,
    description: ''
  }
}

export function createEvent() {
  return {
    identifier: '',
    name: '',
    type: 'info',
    outputData: [],
    description: ''
  }
}

export function createService() {
  return {
    identifier: '',
    name: '',
    callType: 'async',
    inputData: [],
    outputData: [],
    description: ''
  }
}

/** 深拷贝，编辑抽屉用草稿避免直接改动列表中的对象。 */
export function clone(value) {
  return JSON.parse(JSON.stringify(value))
}

/**
 * 解析物模型原文；无法解析或结构不符时回退为空模型，保证页面可继续编辑。
 */
export function parseThingModel(raw) {
  if (!raw) return createEmptyThingModel()
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return createEmptyThingModel()
    }
    return {
      schemaVersion: parsed.schemaVersion || SCHEMA_VERSION,
      properties: Array.isArray(parsed.properties) ? parsed.properties : [],
      events: Array.isArray(parsed.events) ? parsed.events : [],
      services: Array.isArray(parsed.services) ? parsed.services : []
    }
  } catch {
    return createEmptyThingModel()
  }
}

/** 序列化为保存 / 导出用的 JSON 文本（缩进 2 便于人工核对）。 */
export function serializeThingModel(model) {
  return JSON.stringify(model ?? createEmptyThingModel(), null, 2)
}

/** 数据类型的简短展示文案，如 `float(0~100)`、`enum(2)`、`array<int>`。 */
export function dataTypeSummary(dataType) {
  if (!dataType || !dataType.type) return '—'
  const { type } = dataType
  if (type === 'int' || type === 'float' || type === 'double') {
    const range = dataType.min !== undefined && dataType.max !== undefined
      ? `(${dataType.min}~${dataType.max})`
      : ''
    const unit = dataType.unit ? ` ${dataType.unit}` : ''
    return `${type}${range}${unit}`
  }
  if (type === 'text') return `text(${dataType.length ?? '—'})`
  if (type === 'enum') return `enum(${Object.keys(dataType.specs || {}).length})`
  if (type === 'struct') return `struct(${(dataType.specs || []).length})`
  if (type === 'array') return `array<${dataType.item?.type || '—'}>`
  return type
}

export function dataTypeLabel(type) {
  return DATA_TYPE_LABELS[type] || type || '—'
}