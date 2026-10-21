import api from './axios'

/**
 * 看板分享与大屏公开访问（T-46）。
 *
 * 分享令牌明文只在 create / rotate 响应返回一次；公开视图无需登录。
 */
export const dashboardShareApi = {
  list: () => api.get('/dashboards/shares'),
  create: (data) => api.post('/dashboards/shares', data),
  update: (id, data) => api.put(`/dashboards/shares/${id}`, data),
  remove: (id) => api.delete(`/dashboards/shares/${id}`),
  rotate: (id) => api.post(`/dashboards/shares/${id}/rotate`),
  publicView: (token) => api.get(`/public/dashboard-shares/${token}`)
}

export default dashboardShareApi
