import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

const SUCCESS_CODE = 200
let redirecting = false

function handleUnauthorized() {
  const authStore = useAuthStore()
  authStore.clearSession()
  if (!redirecting && window.location.pathname !== '/login') {
    redirecting = true
    authStore.emit('navigate', '/login')
    window.setTimeout(() => {
      redirecting = false
    }, 1000)
  }
}

api.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => {
    const data = response.data
    // 兼容个别仍以 HTTP 200 返回业务错误的接口
    if (data && typeof data === 'object' && 'code' in data && data.code !== SUCCESS_CODE) {
      const message = data.message || '请求失败'
      ElMessage.error(message)
      if (data.code === 401) {
        handleUnauthorized()
      }
      return Promise.reject(Object.assign(new Error(message), { code: data.code }))
    }
    return data
  },
  (error) => {
    const status = error.response?.status
    const errorData = error.response?.data
    const message = errorData?.message || (status ? '请求失败' : '网络错误，请稍后重试')
    ElMessage.error(message)

    if (status === 401) {
      handleUnauthorized()
    }

    return Promise.reject(error)
  }
)

/**
 * 读取本地保存的登录令牌。
 * 供 fetch/EventSource 等无法走 axios 拦截器的场景使用。
 */
export function getToken() {
  return localStorage.getItem('token') || ''
}

export default api