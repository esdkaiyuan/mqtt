import api from './axios'

/**
 * 后端 `DashboardSaveDTO` 的 `config` 字段是**原始 JSON 字符串**
 * （实体 `Dashboard.config` 承载字符串，由服务层用 ObjectMapper 解析），
 * 因此前端必须自行 `JSON.stringify`；这里统一在 API 层归一化，
 * 调用方可直接传配置对象，也不会因为重复传入字符串而二次编码。
 */
function normalizeSaveData(data = {}) {
  const config = data.config
  return {
    name: data.name,
    config:
      typeof config === 'string'
        ? config
        : JSON.stringify(config ?? { panels: [] })
  }
}

export const dashboardApi = {
  /**
   * 看板列表（仅本人）：返回 DashboardSummaryVO[]，含 id / name / updatedAt / panelCount，
   * **不含 config 明细**，列表页无需展开面板配置。
   */
  list: () => api.get('/dashboards'),

  /**
   * 新建看板：`data = { name, config }`，config 为 DashboardConfig（对象或 JSON 字符串）。
   * 超过本人看板数上限时后端返回 6230。
   */
  create: (data) => api.post('/dashboards', normalizeSaveData(data)),

  /**
   * 看板详情：返回 DashboardDetailVO（含 config）。
   * 不存在或非本人一律返回 6228（后端刻意不区分，避免探测）。
   */
  detail: (id) => api.get(`/dashboards/${id}`),

  /**
   * 更新看板：整份覆盖 name 与 config（PUT 语义）。
   * name 为空 / 超 64 字符、config 非法、面板数超限时后端返回 6229。
   */
  update: (id, data) => api.put(`/dashboards/${id}`, normalizeSaveData(data)),

  /**
   * 删除看板：非本人或不存在返回 6228。
   */
  remove: (id) => api.delete(`/dashboards/${id}`)
}

export default dashboardApi
