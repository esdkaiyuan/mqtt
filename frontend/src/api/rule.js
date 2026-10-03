import api from './axios'

/**
 * 消息规则控制台接口（T-19 设计文档 §10.1）。
 *
 * 规则与执行记录均按当前登录用户隔离；启停为状态迁移操作，
 * 手动重试仅允许失败（FAILED）记录，成功后需失效执行记录查询。
 */
export const ruleApi = {
  // ---------- 规则 ----------

  /** 规则分页列表，可按来源 / 动作 / 启用态 / 关键字过滤 */
  listRules: (params) => api.get('/rules', { params }),

  /** 规则详情 */
  getRule: (id) => api.get(`/rules/${id}`),

  /** 创建规则 */
  createRule: (data) => api.post('/rules', data),

  /** 更新规则 */
  updateRule: (id, data) => api.put(`/rules/${id}`, data),

  /** 删除规则（逻辑删除，执行记录保留） */
  deleteRule: (id) => api.delete(`/rules/${id}`),

  /** 启用 / 停用规则 */
  setRuleEnabled: (id, enabled) => api.put(`/rules/${id}/enabled`, { enabled }),

  /** 试运行（干跑，无副作用） */
  testRule: (id, data) => api.post(`/rules/${id}/test`, data),

  // ---------- 执行记录 ----------

  /** 执行记录分页，可按规则 / 设备 / 状态过滤 */
  listExecutions: (params) => api.get('/rules/executions', { params }),

  /** 执行记录详情（含转发载荷快照） */
  getExecution: (id) => api.get(`/rules/executions/${id}`),

  /** 手动重试（仅 FAILED 记录） */
  retryExecution: (id) => api.post(`/rules/executions/${id}/retry`)
}

export default ruleApi