import api from './axios'

/**
 * 通知渠道扩展控制台接口（T-27 设计文档 §10.1）。
 *
 * 站内信、渠道与升级策略一律按当前登录用户隔离，需携带认证令牌。
 * 渠道响应中的敏感字段已由后端脱敏为 `******`，更新时留空或回填该串表示保留原值。
 * `app.notification.enabled=false` 时渠道与升级策略端点整体返 404，站内信读取与已读保留。
 */
export const notificationApi = {
  // ---------- 站内信 ----------

  /** 站内信分页，支持 unreadOnly 过滤 */
  listNotifications: (params) => api.get('/notifications', { params }),

  /** 未读数，供顶栏角标 */
  unreadCount: () => api.get('/notifications/unread-count'),

  /** 标记单条已读（幂等） */
  markRead: (id) => api.post(`/notifications/${id}/read`),

  /** 全部标记已读 */
  markAllRead: () => api.post('/notifications/read-all'),

  /** 删除单条 */
  deleteNotification: (id) => api.delete(`/notifications/${id}`),

  /** 清空本人已读站内信 */
  clearRead: () => api.delete('/notifications'),

  // ---------- 通知渠道 ----------

  /** 列出本人渠道（敏感字段已脱敏） */
  listChannels: () => api.get('/notification-channels'),

  /** 渠道详情 */
  getChannel: (id) => api.get(`/notification-channels/${id}`),

  /** 新建渠道 */
  createChannel: (data) => api.post('/notification-channels', data),

  /** 更新渠道，敏感字段留空表示不修改 */
  updateChannel: (id, data) => api.put(`/notification-channels/${id}`, data),

  /** 删除渠道 */
  deleteChannel: (id) => api.delete(`/notification-channels/${id}`),

  /** 连通性测试，同步返回结果 */
  testChannel: (id) => api.post(`/notification-channels/${id}/test`),

  // ---------- 升级策略 ----------

  /** 列出本人升级策略 */
  listEscalations: () => api.get('/alert-escalations'),

  /** 升级策略详情 */
  getEscalation: (id) => api.get(`/alert-escalations/${id}`),

  /** 新建升级策略 */
  createEscalation: (data) => api.post('/alert-escalations', data),

  /** 更新升级策略 */
  updateEscalation: (id, data) => api.put(`/alert-escalations/${id}`, data),

  /** 删除升级策略（逻辑删除） */
  deleteEscalation: (id) => api.delete(`/alert-escalations/${id}`)
}

export default notificationApi