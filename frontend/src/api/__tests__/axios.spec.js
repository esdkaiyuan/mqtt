import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api, { getToken } from '../axios'
import { useAuthStore } from '@/stores/auth'

/**
 * Element Plus 的命令式 API 由 unplugin-auto-import 注入到 axios.js，
 * 测试环境用替身拦截，避免真实 ElMessage 触发渲染。
 */
vi.mock('element-plus/es/components/message/index.mjs', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

const { ElMessage } = await import('element-plus/es/components/message/index.mjs')

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

/** 构造一个带 response 的失败错误，模拟 axios 网络层 4xx/5xx 拒绝。 */
function httpError(status, data) {
  const error = new Error(`Request failed with status code ${status}`)
  error.response = { status, data, headers: {}, config: {} }
  return error
}

describe('api/axios', () => {
  let adapter

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    localStorage.clear()
    adapter = vi.fn()
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    // handleUnauthorized 的 1s 防抖标记是模块级状态，
    // 推平定时器让它在用例之间复位，避免状态串扰。
    vi.advanceTimersByTime(1500)
    vi.useRealTimers()
  })

  describe('请求拦截器', () => {
    it('已登录时注入 Bearer token', async () => {
      localStorage.setItem('token', 'token-abc')
      setActivePinia(createPinia())
      adapter.mockResolvedValue(okResponse({ code: 200, data: [], message: 'ok' }))

      await api.get('/devices')

      expect(adapter).toHaveBeenCalledTimes(1)
      const config = adapter.mock.calls[0][0]
      expect(config.headers.Authorization).toBe('Bearer token-abc')
      // adapter 拿到的是未拼接的配置：baseURL 与 url 分开传递
      expect(config.baseURL).toBe('/api')
      expect(config.url).toBe('/devices')
    })

    it('未登录时不注入 Authorization 头', async () => {
      adapter.mockResolvedValue(okResponse({ code: 200, data: [], message: 'ok' }))

      await api.get('/devices')

      const config = adapter.mock.calls[0][0]
      expect(config.headers.Authorization).toBeUndefined()
    })
  })

  describe('响应拦截器', () => {
    it('成功时返回整个 Result 包装体（由调用方 unwrap）', async () => {
      const payload = { code: 200, message: 'ok', data: { records: [{ id: 1 }], total: 1 } }
      adapter.mockResolvedValue(okResponse(payload))

      const result = await api.get('/history')

      expect(result).toEqual(payload)
      expect(result.data.records).toHaveLength(1)
    })

    it('HTTP 200 但业务码非 200 时抛错并提示', async () => {
      adapter.mockResolvedValue(okResponse({ code: 500, message: '业务异常' }))

      await expect(api.get('/devices')).rejects.toMatchObject({ code: 500 })

      expect(ElMessage.error).toHaveBeenCalledWith('业务异常')
    })

    it('HTTP 401 时清理会话并跳转登录', async () => {
      localStorage.setItem('token', 'expired-token')
      localStorage.setItem('user', JSON.stringify({ id: 1, username: 'admin' }))
      const pinia = createPinia()
      setActivePinia(pinia)
      const authStore = useAuthStore()
      const navigate = vi.fn()
      authStore.on('navigate', navigate)
      expect(authStore.token).toBe('expired-token')

      adapter.mockRejectedValue(httpError(401, { code: 401, message: '未授权' }))

      await expect(api.get('/devices')).rejects.toThrow()

      expect(authStore.token).toBe('')
      expect(authStore.user).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
      expect(navigate).toHaveBeenCalledWith('/login')
    })

    it('网络错误时给出「网络错误」提示', async () => {
      adapter.mockRejectedValue(new Error('Network Error'))

      await expect(api.get('/devices')).rejects.toThrow('Network Error')

      expect(ElMessage.error).toHaveBeenCalledWith('网络错误，请稍后重试')
    })

    it('redirecting 复位后再次 401 仍会触发跳转', async () => {
      const pinia = createPinia()
      setActivePinia(pinia)
      const authStore = useAuthStore()
      const navigate = vi.fn()
      authStore.on('navigate', navigate)

      adapter.mockRejectedValue(httpError(401, {}))
      await expect(api.get('/devices')).rejects.toThrow()
      expect(navigate).toHaveBeenCalledTimes(1)

      // 1 秒防抖窗口内不重复跳转
      adapter.mockRejectedValue(httpError(401, {}))
      await expect(api.get('/devices')).rejects.toThrow()
      expect(navigate).toHaveBeenCalledTimes(1)

      vi.advanceTimersByTime(1000)

      adapter.mockRejectedValue(httpError(401, {}))
      await expect(api.get('/devices')).rejects.toThrow()
      expect(navigate).toHaveBeenCalledTimes(2)
    })
  })

  describe('getToken', () => {
    it('读取 localStorage 中的令牌，缺失时返回空串', () => {
      expect(getToken()).toBe('')

      localStorage.setItem('token', 'token-xyz')

      expect(getToken()).toBe('token-xyz')
    })
  })
})
