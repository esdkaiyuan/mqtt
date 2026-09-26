import api from './axios'

export const messageApi = {
  /**
   * 发布MQTT消息
   */
  publish: (data) => api.post('/messages/publish', data),

  /**
   * 查询消息列表（分页+过滤）
   * @param {Object} params - { pageNum, pageSize, topic, deviceId, direction, startTime, endTime }
   */
  getList: (params) => api.get('/messages', { params }),

  /**
   * 获取最近消息
   * @param {Number} limit - 数量限制，默认50
   */
  getRecent: (limit = 50) => api.get('/messages/recent', { params: { limit } })
}
