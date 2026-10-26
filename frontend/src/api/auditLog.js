import api from './axios'

/**
 * 审计日志查询 API（T-53）。
 * 仅 ADMIN 可访问；导出走 exportApi.auditLogs。
 */
export const auditLogApi = {
  list: (params) => api.get('/audit-logs', { params })
}

export default auditLogApi