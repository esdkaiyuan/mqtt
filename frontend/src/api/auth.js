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
   * 二步登录验证（T-58 MFA）
   */
  verifyMfa: (data) => api.post('/auth/mfa/verify', data),

  /**
   * 查询当前用户 MFA 状态
   */
  mfaStatus: () => api.get('/auth/mfa/status'),

  /**
   * 发起 MFA 绑定，返回密钥与 otpauth URI
   */
  mfaSetup: () => api.post('/auth/mfa/setup'),

  /**
   * 确认并启用 MFA
   */
  mfaEnable: (data) => api.post('/auth/mfa/enable', data),

  /**
   * 停用 MFA
   */
  mfaDisable: (data) => api.post('/auth/mfa/disable', data),

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
