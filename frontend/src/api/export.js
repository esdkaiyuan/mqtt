import api from './axios'

/** 附件请求统一配置：responseType=blob，且让响应拦截器返回完整 response 以读取响应头。 */
const DOWNLOAD = { responseType: 'blob', returnFullResponse: true }

/**
 * CSV 导出（T-36 后端能力的前端接入）。
 * 与分页查询并列：查询用于浏览，导出用于一次性带走做二次分析。
 */
export const exportApi = {
  deviceLogs: (deviceKey, params) =>
    api.get(`/devices/${deviceKey}/logs/export`, { ...DOWNLOAD, params }),

  propertyHistory: (deviceKey, params) =>
    api.get(`/devices/${deviceKey}/properties/history/export`, { ...DOWNLOAD, params }),

  commands: (deviceKey, params) =>
    api.get(`/devices/${deviceKey}/commands/export`, { ...DOWNLOAD, params }),

  alerts: (params) => api.get('/alerts/export', { ...DOWNLOAD, params }),

  /** 设备台账导出（T-53）：不含密钥，按列表筛选条件导出。 */
  devices: (params) => api.get('/devices/export', { ...DOWNLOAD, params }),

  /** 审计日志导出（T-53）：仅 ADMIN。 */
  auditLogs: (params) => api.get('/audit-logs/export', { ...DOWNLOAD, params })
}

export default exportApi
