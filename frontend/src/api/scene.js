import api from './axios'

/**
 * 场景联动接口（T-23 §10.1）。
 *
 * 后端路由中静态段 `/scenes/executions` 先于 `/scenes/{id}` 声明，
 * 因此执行记录查询走独立方法，避免与详情接口冲突。
 */
export const sceneApi = {
  listScenes: (params) => api.get('/scenes', { params }),
  getScene: (id) => api.get(`/scenes/${id}`),
  createScene: (data) => api.post('/scenes', data),
  updateScene: (id, data) => api.put(`/scenes/${id}`, data),
  deleteScene: (id) => api.delete(`/scenes/${id}`),
  setSceneEnabled: (id, enabled) => api.put(`/scenes/${id}/enabled`, { enabled }),
  testScene: (id, data) => api.post(`/scenes/${id}/test`, data),
  runScene: (id) => api.post(`/scenes/${id}/run`),
  listSceneExecutions: (params) => api.get('/scenes/executions', { params }),
  getSceneExecution: (id) => api.get(`/scenes/executions/${id}`),
  retrySceneExecution: (id) => api.post(`/scenes/executions/${id}/retry`)
}

export default sceneApi
