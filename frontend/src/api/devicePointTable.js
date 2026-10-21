import api from './axios'

/**
 * 设备点位表 API（T-31 P2）。
 *
 * 点表是「设备地址与换算规则的唯一声明处」，其错误配置会直接表现为采不到值或值不对，
 * 所以列表接口会返回最近采集状态与失败原因，前端据此提示而非让用户自行排查。
 */
export const devicePointTableApi = {
  /** 查询设备点位表；executor 传 'EDGE' / 'PLATFORM' 可筛选，留空表示全部 */
  listPoints: (deviceId, executor) =>
    api.get(`/devices/${deviceId}/points`, { params: executor ? { executor } : {} }),

  /** 批量保存（按 pointKey upsert，整批成功或整批失败） */
  savePoints: (deviceId, points) => api.post(`/devices/${deviceId}/points`, points),

  /** 删除点位（连带清理该点位的采集日志） */
  deletePoint: (deviceId, pointId) => api.delete(`/devices/${deviceId}/points/${pointId}`),

  /**
   * 下发点表到边缘网关（T-31 P3，路线 A）。
   *
   * 返回的 status 为 'QUEUED' 表示设备当前离线、已入队待补发，配置尚未送达；
   * 调用方须据此提示「将在设备上线后自动生效」而非「下发成功」。
   */
  dispatch: (deviceId) => api.post(`/devices/${deviceId}/points/dispatch`)
}

/** 执行方可选值。空串表示不筛选。 */
export const EXECUTOR_OPTIONS = [
  { label: '边缘网关采集', value: 'EDGE' },
  { label: '平台侧轮询采集', value: 'PLATFORM' }
]

/** 点位类型可选值。 */
export const POINT_TYPE_OPTIONS = [
  { label: '保持寄存器', value: 'HOLDING_REGISTER' },
  { label: '输入寄存器', value: 'INPUT_REGISTER' },
  { label: '线圈', value: 'COIL' },
  { label: '离散输入', value: 'DISCRETE_INPUT' },
  { label: 'TAG', value: 'TAG' },
  { label: '节点', value: 'NODE' }
]

/** 数据类型可选值。 */
export const DATA_TYPE_OPTIONS = [
  { label: '整数（16 位）', value: 'int' },
  { label: '整数（32 位有符号）', value: 'int32' },
  { label: '整数（32 位无符号）', value: 'uint32' },
  { label: '整数（64 位有符号）', value: 'int64' },
  { label: '整数（64 位无符号）', value: 'uint64' },
  { label: '浮点', value: 'float' },
  { label: '双精度', value: 'double' },
  { label: '布尔', value: 'bool' },
  { label: '字符串', value: 'string' }
]

/** 寄存器类点位：地址必须是数字，供前端表单做即时校验。 */
export function isRegisterType(pointType) {
  return ['HOLDING_REGISTER', 'INPUT_REGISTER', 'COIL', 'DISCRETE_INPUT'].includes(pointType)
}

/** 生成一行空白点位，表单初始与「新增」动作用。 */
export function createEmptyPoint(executor) {
  return {
    id: null,
    pointKey: '',
    pointType: 'HOLDING_REGISTER',
    address: '',
    unitId: null,
    dataType: 'float',
    scale: null,
    offset: null,
    unit: '',
    executor: executor || 'EDGE',
    readIntervalMs: 60000,
    enabled: 1
  }
}

/**
 * 解析粘贴的 JSON 点表（批量导入现场点表）。
 *
 * 现场点表通常来自 PLC 工程文档或组态软件导出，字段名千差万别，
 * 因此这里接受常见别名并做宽松归一，而不是要求用户先把文件改成我们的格式。
 *
 * @returns {{points: Array, skipped: Array<{index: number, reason: string}>}}
 *          解析成功即返回 points；逐条缺字段的跳过并记入 skipped，不整体失败 ——
 *          一份 50 行的现场点表因 1 行笔误而全部无法导入，是很不好的体验
 */
export function parsePointsJson(text) {
  const trimmed = (text || '').trim()
  if (!trimmed) {
    throw new Error('请粘贴内容')
  }

  let raw
  try {
    raw = JSON.parse(trimmed)
  } catch (e) {
    // 带上 cause：用户在浏览器里看到的只有这段文案，原始 SyntaxError 会丢在控制台之外，
    // 而带 cause 后开发者工具里能直接看到出错的字符位置
    throw new Error(`JSON 解析失败：${e.message}`, { cause: e })
  }

  // 兼容 { points: [...] } / { properties: [...] } / 顶层数组三种形态
  const list = Array.isArray(raw) ? raw : raw.points || raw.properties
  if (!Array.isArray(list)) {
    throw new Error('未找到点位数组（期望顶层数组，或含 points / properties 字段的对象）')
  }

  const points = []
  const skipped = []
  list.forEach((item, i) => {
    if (!item || typeof item !== 'object') {
      skipped.push({ index: i, reason: '不是对象' })
      return
    }
    const pointKey = pick(item, ['pointKey', 'point_key', 'key', 'name', 'identifier', 'tag'])
    if (!pointKey) {
      skipped.push({ index: i, reason: '缺少点位标识' })
      return
    }
    const point = createEmptyPoint()
    point.pointKey = String(pointKey)
    point.pointType = pick(item, ['pointType', 'point_type', 'type']) || 'HOLDING_REGISTER'
    point.address = stringifyAddress(pick(item, ['address', 'addr', 'register', 'reg']))
    point.unitId = toIntegerOrNull(pick(item, ['unitId', 'unit_id', 'slaveId', 'slave_id', 'stationId']))
    point.dataType = pick(item, ['dataType', 'data_type']) || 'float'
    point.scale = toNumberOrNull(pick(item, ['scale', 'factor']))
    point.offset = toNumberOrNull(pick(item, ['offset']))
    point.unit = pick(item, ['unit', 'units']) || ''
    point.executor = pick(item, ['executor']) || 'EDGE'
    point.readIntervalMs = toNumberOrNull(pick(item, ['readIntervalMs', 'interval', 'cycle'])) || 60000
    point.enabled = pick(item, ['enabled']) === false || pick(item, ['enabled']) === 0 ? 0 : 1
    points.push(point)
  })

  return { points, skipped }
}

/** 按候选字段名顺序取第一个非空值。 */
function pick(item, names) {
  for (const name of names) {
    const v = item[name]
    if (v !== undefined && v !== null && v !== '') {
      return v
    }
  }
  return undefined
}

/** 地址统一转字符串：组态软件常把寄存器号导出为数字，表单侧一律按文本处理。 */
function stringifyAddress(value) {
  if (value === undefined || value === null) {
    return ''
  }
  return String(value)
}

function toNumberOrNull(value) {
  if (value === undefined || value === null || value === '') {
    return null
  }
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function toIntegerOrNull(value) {
  const n = toNumberOrNull(value)
  return n !== null && Number.isInteger(n) ? n : null
}

/** 序列化点位表为可再次粘贴导入的 JSON，便于用户先导出再编辑。 */
export function stringifyPointsJson(points) {
  return JSON.stringify(points, null, 2)
}