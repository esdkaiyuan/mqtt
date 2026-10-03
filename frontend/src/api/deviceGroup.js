import api from './axios'

/**
 * 设备分组控制台接口（T-18 设计文档 §10.1）。
 *
 * 分组按当前登录用户隔离，仅可读写本人数据；分组资源越权统一 403。
 * 「按分组筛选」与「分组内设备分页」均**包含所有后代分组**。
 */
export const deviceGroupApi = {
  /** 分组树（含 children 与直接关联设备数 deviceCount） */
  tree: () => api.get('/device-groups/tree'),

  /** 分组平铺列表（下拉 / 选择器用） */
  list: () => api.get('/device-groups'),

  /** 创建分组 */
  create: (data) => api.post('/device-groups', data),

  /** 分组详情 */
  getDetail: (id) => api.get(`/device-groups/${id}`),

  /** 更新分组（重命名 / 描述 / 排序 / 移动父节点） */
  update: (id, data) => api.put(`/device-groups/${id}`, data),

  /** 删除分组（仅空分组可删） */
  delete: (id) => api.delete(`/device-groups/${id}`),

  /** 分组内设备分页（含所有后代分组） */
  pageDevices: (id, params) => api.get(`/device-groups/${id}/devices`, { params }),

  /** 加入分组（单台 / 批量，唯一键去重幂等） */
  addDevices: (id, deviceIds) => api.post(`/device-groups/${id}/devices`, { deviceIds }),

  /** 移出分组（单台 / 批量） */
  removeDevices: (id, deviceIds) =>
    api.delete(`/device-groups/${id}/devices`, { data: { deviceIds } })
}

export default deviceGroupApi