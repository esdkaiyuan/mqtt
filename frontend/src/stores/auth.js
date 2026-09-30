import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

const STORAGE_TOKEN = 'token'
const STORAGE_USER = 'user'

function readStoredUser() {
  try {
    const stored = localStorage.getItem(STORAGE_USER)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

function normalizeUser(data) {
  if (!data) return null
  return {
    id: data.id,
    username: data.username,
    email: data.email,
    phone: data.phone,
    role: data.role,
    status: data.status
  }
}

// Event bus for decoupled navigation (breaks circular dependency with router)
const _listeners = {}
function on(event, callback) {
  if (!_listeners[event]) _listeners[event] = []
  _listeners[event].push(callback)
}
function emit(event, payload) {
  if (_listeners[event]) _listeners[event].forEach((cb) => cb(payload))
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(STORAGE_TOKEN) || '')
  const user = ref(readStoredUser())

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const isAdmin = computed(() => role.value === 'ADMIN')

  function hasRole(roles) {
    if (!roles || roles.length === 0) return true
    return roles.includes(role.value)
  }

  function persistSession() {
    if (token.value) {
      localStorage.setItem(STORAGE_TOKEN, token.value)
    } else {
      localStorage.removeItem(STORAGE_TOKEN)
    }
    if (user.value) {
      localStorage.setItem(STORAGE_USER, JSON.stringify(user.value))
    } else {
      localStorage.removeItem(STORAGE_USER)
    }
  }

  async function login(loginDTO) {
    const result = await authApi.login(loginDTO)
    const data = result.data || result
    token.value = data.token || ''
    user.value = normalizeUser(data)
    persistSession()
    emit('navigate', '/dashboard')
  }

  async function register(registerDTO) {
    const result = await authApi.register(registerDTO)
    ElMessage.success('注册成功，请登录')
    emit('navigate', '/login')
    return result
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // ignore logout network errors
    } finally {
      clearSession()
      emit('navigate', '/login')
    }
  }

  async function fetchCurrentUser() {
    const result = await authApi.getCurrentUser()
    const data = result.data || result
    user.value = normalizeUser(data)
    persistSession()
    return data
  }

  /**
   * 已登录但本地缺少用户信息时（例如刷新页面后 localStorage 被清空），
   * 用 token 重新拉取一次用户信息，保证角色守卫与菜单渲染正确。
   */
  async function ensureUserLoaded() {
    if (!token.value || user.value) return user.value
    try {
      await fetchCurrentUser()
    } catch {
      clearSession()
    }
    return user.value
  }

  function clearSession() {
    token.value = ''
    user.value = null
    localStorage.removeItem(STORAGE_TOKEN)
    localStorage.removeItem(STORAGE_USER)
  }

  function setTokenAndUser(newToken, newUser) {
    token.value = newToken
    user.value = normalizeUser(newUser)
    persistSession()
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    isAdmin,
    hasRole,
    login,
    register,
    logout,
    fetchCurrentUser,
    ensureUserLoaded,
    clearSession,
    setTokenAndUser,
    on,
    emit
  }
})