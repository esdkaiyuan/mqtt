import api from './axios'

export const hmiApi = {
  /** 本人上位机列表（按更新时间倒序） */
  list: () => api.get('/hmi-views'),

  /** 按设备物模型生成草稿（不落库） */
  draft: (deviceId) => api.get('/hmi-views/draft', { params: { deviceId } }),

  create: (data) => api.post('/hmi-views', data),

  detail: (id) => api.get(`/hmi-views/${id}`),

  update: (id, data) => api.put(`/hmi-views/${id}`, data),

  remove: (id) => api.delete(`/hmi-views/${id}`)
}

export default hmiApi
