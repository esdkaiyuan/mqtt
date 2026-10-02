<template>
  <div class="register-page">
    <div class="register-layout">
      <!-- Left branding panel -->
      <div class="register-brand">
        <div class="brand-content">
          <BrandLogo :size="48" :text-size="32" class="brand-logo" />
          <p class="brand-desc">创建账号，<br>开始接入你的设备</p>
          <div class="brand-features">
            <div class="brand-feature">
              <span class="brand-feature-icon">
                <svg-icon name="device" :size="16" />
              </span>
              <span>设备接入管理</span>
            </div>
            <div class="brand-feature">
              <span class="brand-feature-icon">
                <svg-icon name="message" :size="16" />
              </span>
              <span>实时消息监控</span>
            </div>
            <div class="brand-feature">
              <span class="brand-feature-icon">
                <svg-icon name="shield" :size="16" />
              </span>
              <span>密钥与权限管控</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right register form -->
      <div class="register-form-area">
        <div class="register-form-wrapper">
          <h2 class="form-title">注册账号</h2>
          <p class="form-subtitle">填写以下信息创建您的平台账号</p>

          <el-form label-position="top" class="register-form" @submit.prevent>
            <el-form-item label="用户名" required :error="errors.username">
              <el-input
                v-model="form.username"
                placeholder="3-50 个字符，字母数字下划线"
                autocomplete="username"
                size="large"
              />
            </el-form-item>

            <el-form-item label="邮箱" :error="errors.email">
              <el-input
                v-model="form.email"
                placeholder="选填"
                autocomplete="email"
                size="large"
              />
            </el-form-item>

            <el-form-item label="手机号" :error="errors.phone">
              <el-input
                v-model="form.phone"
                placeholder="选填"
                autocomplete="tel"
                size="large"
              />
            </el-form-item>

            <el-form-item label="密码" required :error="errors.password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="6-100 个字符"
                autocomplete="new-password"
                size="large"
                show-password
              />
            </el-form-item>

            <el-form-item label="确认密码" required :error="errors.confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                autocomplete="new-password"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>

            <el-button
              type="primary"
              size="large"
              class="register-submit"
              :loading="loading"
              @click="handleRegister"
            >
              注册
            </el-button>

            <div v-if="submitError" class="error-message">{{ submitError }}</div>
          </el-form>

          <div class="register-footer">
            <span>已有账号？</span>
            <router-link to="/login">返回登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'

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
  Object.keys(errors).forEach((key) => { errors[key] = '' })

  if (!form.username) {
    errors.username = '请输入用户名'
    isValid = false
  } else if (form.username.length < 3 || form.username.length > 50) {
    errors.username = '用户名长度 3-50 个字符'
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
    errors.password = '密码长度 6-100 个字符'
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
  } catch {
    // axios 拦截器已显示错误消息，此处无需重复提示
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
  background: var(--color-bg);
}

.register-layout {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  border-radius: var(--border-radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.register-brand {
  flex: 1;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-hover) 100%);
  color: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-4xl);
}

.brand-content {
  max-width: 280px;
}

.brand-logo {
  margin-bottom: var(--spacing-2xl);
}

.brand-logo :deep(.brand-logo__text) {
  color: var(--color-white);
}

.brand-desc {
  font-size: var(--font-size-lg);
  opacity: 0.85;
  line-height: 1.8;
  margin-bottom: var(--spacing-3xl);
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.brand-feature {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-md);
  opacity: 0.9;
}

.brand-feature-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--border-radius-sm);
}

.register-form-area {
  flex: 1;
  background: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-4xl);
}

.register-form-wrapper {
  width: 100%;
  max-width: 320px;
}

.form-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.form-subtitle {
  font-size: var(--font-size-md);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-2xl);
}

.register-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-lg);
}

.register-submit {
  width: 100%;
  margin-top: var(--spacing-sm);
}

.error-message {
  margin-top: var(--spacing-sm);
  padding: 10px 12px;
  background-color: var(--color-danger-light);
  color: var(--color-danger);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
}

.register-footer {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: var(--spacing-2xl);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
}

.register-footer a {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
}

@media (max-width: 768px) {
  .register-brand {
    display: none;
  }

  .register-form-area {
    padding: var(--spacing-3xl) var(--spacing-2xl);
  }

  .register-layout {
    max-width: 420px;
  }
}
</style>