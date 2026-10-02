import api from './axios'

/**
 * 告警中心控制台接口（T-17 设计文档 §10.1）。
 *
 * 规则与记录均按当前登录用户隔离；确认 / 恢复为状态迁移操作，成功后需失效列表与未读数缓存。
 */
export const alertApi = {
  // ---------- 规则 ----------

  /** 规则列表，可按来源与启用状态过滤 */
  listRules: (params) => api.get('/alerts/rules', { params }),

  /** 规则详情 */
  getRule: (id) => api.get(`/alerts/rules/${id}`),

  /** 创建规则 */
  createRule: (data) => api.post('/alerts/rules', data),

  /** 更新规则 */
  updateRule: (id, data) => api.put(`/alerts/rules/${id}`, data),

  /** 删除规则（逻辑删除） */
  deleteRule: (id) => api.delete(`/alerts/rules/${id}`),

  // ---------- 记录 ----------

  /** 告警记录分页，按 last_triggered_at 倒序 */
  list: (params) => api.get('/alerts', { params }),

  /** 告警详情 */
  getDetail: (id) => api.get(`/alerts/${id}`),

  /** 确认告警（仅待处理可确认） */
  acknowledge: (id) => api.post(`/alerts/${id}/ack`),

  /** 人工置恢复 */
  recover: (id) => api.post(`/alerts/${id}/recover`),

  /** 未读数（活动告警数），供顶栏角标 */
  unreadCount: () => api.get('/alerts/unread-count'),

  /** 最近活动告警，供顶栏通知下拉 */
  recent: (limit = 10) => api.get('/alerts/recent', { params: { limit } })
}

export default alertApi