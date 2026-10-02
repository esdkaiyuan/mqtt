<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- Left branding panel -->
      <div class="login-brand">
        <div class="brand-content">
          <BrandLogo :size="48" :text-size="32" class="brand-logo" />
          <p class="brand-desc">构建万物互联的<br>智能 IoT 平台</p>
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
                <svg-icon name="chart" :size="16" />
              </span>
              <span>数据可视化</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right login form -->
      <div class="login-form-area">
        <div class="login-form-wrapper">
          <h2 class="form-title">登录平台</h2>
          <p class="form-subtitle">使用您的账号登录管理工作台</p>

          <el-form label-position="top" class="login-form" @submit.prevent>
            <el-form-item label="用户名">
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                autocomplete="username"
                size="large"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-button
              type="primary"
              size="large"
              class="login-submit"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>

            <div v-if="errorMessage" class="error-message">
              {{ errorMessage }}
            </div>
          </el-form>

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
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import SvgIcon from '@/components/Icon.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

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
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect) {
      router.replace(redirect)
    }
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
  background: var(--color-bg);
}

.login-layout {
  display: flex;
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  border-radius: var(--border-radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.login-brand {
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

.login-form-area {
  flex: 1;
  background: var(--color-white);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-4xl);
}

.login-form-wrapper {
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
  margin-bottom: var(--spacing-3xl);
}

.login-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-xl);
}

.login-submit {
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

.login-footer {
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-top: var(--spacing-2xl);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
}

.login-footer a {
  color: var(--color-primary);
  font-weight: var(--font-weight-medium);
  text-decoration: none;
}

.login-hint {
  text-align: center;
  margin-top: var(--spacing-2xl);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--color-border-light);
}

.login-hint p {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    padding: var(--spacing-3xl) var(--spacing-2xl);
  }

  .login-layout {
    max-width: 420px;
  }
}
</style>