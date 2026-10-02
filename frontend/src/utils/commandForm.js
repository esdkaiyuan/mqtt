/**
 * 命令表单辅助：把「可下发能力」投影成表单字段，并把表单值组装成命令载荷。
 *
 * 与 `utils/thingModel.js` 分工一致：前端只做「生成结构合法、类型可用的值」，
 * 真正的参数校验仍以服务端 `ThingModelParamValidator` 为准（非法值由后端返回 6204）。
 */

/** 数据类型 → 控件类型；未覆盖的类型退化为文本输入。 */
const CONTROL_BY_TYPE = {
  int: 'number',
  float: 'number',
  double: 'number',
  bool: 'switch',
  text: 'input',
  enum: 'select',
  date: 'date',
  struct: 'json',
  array: 'json'
}

export function controlFor(type) {
  return CONTROL_BY_TYPE[type] || 'input'
}

/** 表单初值：布尔给 false（开关不能为空），数值留空由用户填写。 */
export function defaultValue(type) {
  return type === 'bool' ? false : ''
}

function toField(source, required) {
  return {
    identifier: source.identifier,
    type: source.type,
    control: controlFor(source.type),
    min: source.min ?? null,
    max: source.max ?? null,
    integer: Boolean(source.integer),
    enumKeys: source.enumKeys ?? [],
    textLength: source.textLength ?? null,
    required: Boolean(required)
  }
}

/** 属性设置字段：能力接口已只返回 rw 属性，此处不重复过滤。 */
export function propertyFields(capability) {
  return (capability?.properties ?? []).map((item) => toField(item, false))
}

/** 服务入参字段：保留 `required` 以在表单上标注必填。 */
export function serviceFields(service) {
  return (service?.input ?? []).map((item) => toField(item, item.required))
}

/** 按字段列表生成表单初值对象。 */
export function initialValues(fields) {
  const values = {}
  for (const field of fields ?? []) {
    values[field.identifier] = defaultValue(field.type)
  }
  return values
}

function isBlank(value) {
  return value === null || value === undefined || value === ''
}

/** 必填但未填写的字段标识符列表。 */
export function missingRequired(fields, values) {
  return (fields ?? [])
    .filter((field) => field.required && isBlank(values?.[field.identifier]))
    .map((field) => field.identifier)
}

/**
 * 组装 `params`：跳过空值；`struct` / `array` 由 JSON 文本解析为对象。
 *
 * @returns {{ params?: object, error?: string }} 解析失败时返回 `error`（不抛异常）
 */
export function buildParams(fields, values) {
  const params = {}
  for (const field of fields ?? []) {
    const raw = values?.[field.identifier]
    if (isBlank(raw)) continue
    if (field.control === 'json') {
      try {
        params[field.identifier] = JSON.parse(raw)
      } catch {
        return { error: `「${field.identifier}」不是合法的 JSON` }
      }
    } else {
      params[field.identifier] = raw
    }
  }
  return { params }
}

/** 下行 Topic（展示用），与后端发布主题保持一致。 */
export function commandTopic(deviceKey) {
  return deviceKey ? `device/${deviceKey}/cmd/down` : ''
}