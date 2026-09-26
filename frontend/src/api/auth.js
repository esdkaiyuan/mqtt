import api from './axios'

export const authApi = {
  /**
   * 用户注册
   */
  register: (data) => api.post('/auth/register', data),

  /**
   * 用户登录
   */
  login: (data) => api.post('/auth/login', data),

  /**
   * 用户登出
   */
  logout: () => api.post('/auth/logout'),

  /**
   * 获取当前用户信息
   */
  getCurrentUser: () => api.get('/auth/current'),

  /**
   * 修改密码
   * @param {Object} data - { oldPassword, newPassword }
   */
  changePassword: (data) => api.post('/auth/change-password', data)
}
