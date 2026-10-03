import api from './axios'

/**
 * OTA 固件升级（T-22）接口。
 *
 * 约定：
 * - 所有方法返回 axios 拦截器处理后的完整 Result 包装体，页面侧统一用 unwrapResult 解包；
 * - 固件上传为 multipart/form-data，参数 productId / version / description 走表单字段，file 为文件本体；
 * - 被升级任务引用的固件删除会返回 6234，运行中的任务不可删除 / 重投（6238），错误提示由 axios 拦截器统一弹出。
 */
export const otaApi = {
  /** 固件包列表：可按 productId 过滤，返回 OtaFirmwareVO[] */
  listFirmwares: (params) => api.get('/ota/firmwares', { params }),
  /** 固件包详情：不存在返回 6231 */
  firmwareDetail: (id) => api.get(`/ota/firmwares/${id}`),
  /** 下载固件包（附件流） */
  downloadFirmware: (id) => api.get(`/ota/firmwares/${id}/download`, { responseType: 'blob' }),
  /** 上传固件包：FormData(productId, version, file, description)；版本重复返回 6233 */
  uploadFirmware: (formData) =>
    api.post('/ota/firmwares', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  /** 删除固件包：被升级任务引用时返回 6234 */
  removeFirmware: (id) => api.delete(`/ota/firmwares/${id}`),
  /** 创建升级任务：Body 为 OtaTaskCreateRequest(name, firmwareId, target)，返回任务详情 */
  createTask: (data) => api.post('/ota/tasks', data),
  /** 升级任务列表（分页 page / size） */
  listTasks: (params) => api.get('/ota/tasks', { params }),
  /** 升级任务详情：不存在返回 6236 */
  taskDetail: (id) => api.get(`/ota/tasks/${id}`),
  /** 任务逐台记录（分页 page / size，可传 status 过滤） */
  taskRecords: (id, params) => api.get(`/ota/tasks/${id}/records`, { params }),
  /** 重投未成功记录：任务已成功时返回 6238 */
  retryTask: (id) => api.post(`/ota/tasks/${id}/retry`),
  /** 删除升级任务及其记录：任务运行中返回 6238 */
  removeTask: (id) => api.delete(`/ota/tasks/${id}`)
}

export default otaApi
