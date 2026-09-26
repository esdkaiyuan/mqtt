<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- Left branding panel -->
      <div class="login-brand">
        <div class="brand-content">
          <div class="brand-logo">
            <svg class="logo-icon" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="#165DFF"/>
              <path d="M8 16C8 11.6 11.6 8 16 8C20.4 8 24 11.6 24 16C24 20.4 20.4 24 16 24" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
              <circle cx="16" cy="16" r="3" fill="white"/>
              <path d="M16 10V13M16 19V22M13 16H10M19 16H22" stroke="white" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <h1 class="brand-title">MQTT Cloud</h1>
          <p class="brand-desc">构建万物互联的<br>智能IoT平台</p>
          <div class="brand-features">
            <div class="brand-feature">
              <span class="brand-feature-icon">&#x1F4E1;</span>
              <span>设备接入管理</span>
            </div>
            <div class="brand-feature">
              <span class="brand-feature-icon">&#x1F4CA;</span>
              <span>实时消息监控</span>
            </div>
            <div class="brand-feature">
              <span class="brand-feature-icon">&#x1F300;</span>
              <span>数据可视化</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right login form -->
      <div class="login-form-area">
        <div class="login-form-wrapper">
          <h2 class="form-title">登录平台</h2>
          <p class="form-subtitle">使用您的账号登录管理后台</p>

          <form @submit.prevent="handleLogin" class="login-form">
            <div class="form-group">
              <label class="form-label">用户名</label>
              <div class="input-wrapper">
                <svg-icon name="user" :size="16" color="#86909C" />
                <input
                  v-model="form.username"
                  type="text"
                  placeholder="请输入用户名"
                  class="form-input with-icon"
                  autocomplete="username"
                />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">密码</label>
              <div class="input-wrapper">
                <svg-icon name="user" :size="16" color="#86909C" />
                <input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  class="form-input with-icon"
                  autocomplete="current-password"
                />
              </div>
            </div>

            <button
              type="submit"
              class="btn-login"
              :disabled="loading"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </button>

            <div v-if="errorMessage" class="error-message">
              {{ errorMessage }}
            </div>
          </form>

          <div class="login-footer">
            <span>还没有账号？</span>
            <router-link to="/register">立即注册</router-link>
          </div>

          <div class="login-hint">
            <p>默认账号：admin / admin123</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({
  username: '',
  password: ''
})

const loading = ref(false)
const errorMessage = ref('')

async function handleLogin() {
  errorMessage.value = ''

  if (!form.value.username || !form.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    await authStore.login(form.value)
  } catch (error) {
    // axios 拦截器已显示错误消息，此处无需重复提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F5F7FA;
}

.login-layout {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.08);
}

.login-brand {
  flex: 1;
  background: linear-gradient(135deg, #165DFF 0%, #4080FF 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.brand-content {
  max-width: 280px;
}

.brand-logo {
  margin-bottom: 24px;
}

.logo-icon {
  width: 48px;
  height: 48px;
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
}

.brand-desc {
  font-size: 16px;
  opacity: 0.85;
  line-height: 1.8;
  margin-bottom: 40px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  opacity: 0.9;
}

.brand-feature-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  font-size: 14px;
}

.login-form-area {
  flex: 1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.login-form-wrapper {
  width: 100%;
  max-width: 320px;
}

.form-title {
  font-size: 24px;
  font-weight: 600;
  color: #1D2129;
  margin-bottom: 8px;
}

.form-subtitle {
  font-size: 14px;
  color: #86909C;
  margin-bottom: 32px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #4E5969;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper .svg-icon {
  position: absolute;
  left: 12px;
  pointer-events: none;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #E5E6EB;
  border-radius: 6px;
  font-size: 14px;
  font-family: var(--font-family);
  color: #1D2129;
  background-color: #fff;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #165DFF;
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.1);
}

.form-input.with-icon {
  padding-left: 36px;
}

.form-input::placeholder {
  color: #C9CDD4;
}

.btn-login {
  width: 100%;
  height: 44px;
  background: #165DFF;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-family);
  margin-top: 8px;
}

.btn-login:hover {
  background: #4080FF;
}

.btn-login:disabled {
  background: #C9CDD4;
  cursor: not-allowed;
}

.error-message {
  margin-top: 4px;
  padding: 10px 12px;
  background-color: #FFECEC;
  color: #F53F3F;
  border-radius: 6px;
  font-size: 13px;
}

.login-footer {
  text-align: center;
  font-size: 13px;
  color: #86909C;
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.login-footer a {
  color: #165DFF;
  font-weight: 500;
  text-decoration: none;
}

.login-hint {
  text-align: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #F2F3F5;
}

.login-hint p {
  font-size: 12px;
  color: #C9CDD4;
}

@media (max-width: 768px) {
  .login-brand {
    display: none;
  }
  .login-form-area {
    padding: 32px 24px;
  }
  .login-layout {
    max-width: 420px;
  }
}
</style>
