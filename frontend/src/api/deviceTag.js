import api from './axios'

/**
 * 设备标签控制台接口（T-18 设计文档 §10.2）。
 *
 * 标签按当前登录用户隔离，仅可读写本人数据；标签资源越权统一 403。
 * 删除标签会自动解除其全部设备关联。
 */
export const deviceTagApi = {
  /** 本人标签列表 */
  list: () => api.get('/device-tags'),

  /** 创建标签（同一用户下不重名、颜色需为 #RRGGBB） */
  create: (data) => api.post('/device-tags', data),

  /** 标签详情 */
  getDetail: (id) => api.get(`/device-tags/${id}`),

  /** 更新标签（重命名 / 改色） */
  update: (id, data) => api.put(`/device-tags/${id}`, data),

  /** 删除标签（自动解除设备关联） */
  delete: (id) => api.delete(`/device-tags/${id}`),

  /** 标签内设备分页 */
  pageDevices: (id, params) => api.get(`/device-tags/${id}/devices`, { params }),

  /** 打标签（单台 / 批量，唯一键去重幂等） */
  addDevices: (id, deviceIds) => api.post(`/device-tags/${id}/devices`, { deviceIds }),

  /** 去标签（单台 / 批量） */
  removeDevices: (id, deviceIds) =>
    api.delete(`/device-tags/${id}/devices`, { data: { deviceIds } })
}

export default deviceTagApi