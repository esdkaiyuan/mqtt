<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-header">
        <h1 class="register-title">注册账号</h1>
        <p class="register-subtitle">创建您的MQTT云平台账号</p>
      </div>

      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <label class="form-label">用户名 <span class="required">*</span></label>
          <input
            v-model="form.username"
            type="text"
            placeholder="3-50个字符，字母数字下划线"
            class="form-input"
            :class="{ 'input-error': errors.username }"
          />
          <div v-if="errors.username" class="form-error">{{ errors.username }}</div>
        </div>

        <div class="form-group">
          <label class="form-label">邮箱</label>
          <input
            v-model="form.email"
            type="email"
            placeholder="选填"
            class="form-input"
            :class="{ 'input-error': errors.email }"
          />
          <div v-if="errors.email" class="form-error">{{ errors.email }}</div>
        </div>

        <div class="form-group">
          <label class="form-label">手机号</label>
          <input
            v-model="form.phone"
            type="tel"
            placeholder="选填"
            class="form-input"
            :class="{ 'input-error': errors.phone }"
          />
          <div v-if="errors.phone" class="form-error">{{ errors.phone }}</div>
        </div>

        <div class="form-group">
          <label class="form-label">密码 <span class="required">*</span></label>
          <input
            v-model="form.password"
            type="password"
            placeholder="6-100个字符"
            class="form-input"
            :class="{ 'input-error': errors.password }"
          />
          <div v-if="errors.password" class="form-error">{{ errors.password }}</div>
        </div>

        <div class="form-group">
          <label class="form-label">确认密码 <span class="required">*</span></label>
          <input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            class="form-input"
            :class="{ 'input-error': errors.confirmPassword }"
          />
          <div v-if="errors.confirmPassword" class="form-error">{{ errors.confirmPassword }}</div>
        </div>

        <button
          type="submit"
          class="btn-register"
          :disabled="loading"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const errors = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const loading = ref(false)
const submitError = ref('')

function validateForm() {
  let isValid = true
  Object.keys(errors).forEach(key => errors[key] = '')

  if (!form.username) {
    errors.username = '请输入用户名'
    isValid = false
  } else if (form.username.length < 3 || form.username.length > 50) {
    errors.username = '用户名长度3-50个字符'
    isValid = false
  } else if (!/^[a-zA-Z0-9_]+$/.test(form.username)) {
    errors.username = '用户名只能包含字母、数字、下划线'
    isValid = false
  }

  if (form.email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(form.email)) {
      errors.email = '邮箱格式不正确'
      isValid = false
    }
  }

  if (form.phone) {
    const phoneRegex = /^1[3-9]\d{9}$/
    if (!phoneRegex.test(form.phone)) {
      errors.phone = '手机号格式不正确'
      isValid = false
    }
  }

  if (!form.password) {
    errors.password = '请输入密码'
    isValid = false
  } else if (form.password.length < 6 || form.password.length > 100) {
    errors.password = '密码长度6-100个字符'
    isValid = false
  }

  if (form.password !== form.confirmPassword) {
    errors.confirmPassword = '两次输入的密码不一致'
    isValid = false
  }

  return isValid
}

async function handleRegister() {
  submitError.value = ''

  if (!validateForm()) {
    return
  }

  loading.value = true
  try {
    await authStore.register({
      username: form.username,
      email: form.email || undefined,
      phone: form.phone || undefined,
      password: form.password
    })
  } catch (error) {
    // axios 拦截器已显示错误消息
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--color-gray-light);
  padding: var(--spacing-lg);
}

.register-card {
  width: 100%;
  max-width: 420px;
  background: var(--color-white);
  border-radius: var(--border-radius);
  padding: 40px 32px;
  box-shadow: var(--shadow-md);
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: 8px;
}

.register-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.register-form {
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.required {
  color: var(--color-danger);
  margin-left: 2px;
}

.btn-register {
  width: 100%;
  height: 44px;
  background-color: var(--color-primary);
  color: var(--color-white);
  border: none;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-md);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
  font-family: var(--font-family);
  margin-top: 8px;
}

.btn-register:hover {
  background-color: var(--color-primary-hover);
}

.btn-register:disabled {
  background-color: var(--color-gray-border);
  cursor: not-allowed;
}

.input-error {
  border-color: var(--color-danger) !important;
}

.register-footer {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.register-footer a {
  color: var(--color-primary);
  font-weight: 500;
}
</style>
