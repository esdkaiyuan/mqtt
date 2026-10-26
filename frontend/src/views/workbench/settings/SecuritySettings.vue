<template>
  <div class="security-page page">
    <PageHeader title="安全设置" desc="管理当前账号的多因素认证（TOTP 动态口令）" />

    <div class="card security-card">
      <template v-if="loading">
        <div class="loading-overlay">加载中...</div>
      </template>
      <template v-else>
        <div class="security-status">
          <span class="security-status__label">多因素认证</span>
          <el-tag :type="enabled ? 'success' : 'info'" size="small">
            {{ enabled ? '已启用' : '未启用' }}
          </el-tag>
        </div>
        <p class="security-desc">
          启用后，登录需在密码之外再输入 authenticator 生成的 6 位动态口令。
          密钥仅绑定过程中展示一次，平台只保存加密后的密钥。
        </p>

        <template v-if="enabled">
          <div class="security-actions">
            <el-input v-model="disableCode" placeholder="输入动态口令" maxlength="6" class="security-input" />
            <el-button type="danger" plain :loading="submitting" @click="handleDisable">
              停用 MFA
            </el-button>
          </div>
        </template>
        <template v-else>
          <div class="security-actions">
            <el-button type="primary" :loading="submitting" @click="handleBeginSetup">
              绑定 MFA
            </el-button>
          </div>
        </template>
      </template>
    </div>

    <el-dialog v-model="setupVisible" title="绑定多因素认证" width="560px">
      <template v-if="setup">
        <el-steps :active="1" simple>
          <el-step title="录入 authenticator" />
          <el-step title="回填动态口令" />
        </el-steps>

        <div class="setup-block">
          <div class="setup-block__title">1. 用 authenticator 录入下面任意一项</div>
          <div class="setup-secret">
            <code>{{ setup.secret }}</code>
            <el-button link type="primary" @click="copyText(setup.secret)">复制密钥</el-button>
          </div>
          <div class="setup-uri">
            <span class="setup-uri__label">或录入 otpauth URI：</span>
            <code>{{ setup.otpauthUri }}</code>
            <el-button link type="primary" @click="copyText(setup.otpauthUri)">复制 URI</el-button>
          </div>
          <p class="form-hint">
            发行方：{{ setup.issuer }}；位数：{{ setup.digits }}；周期：{{ setup.periodSeconds }} 秒。
            密钥只显示一次，请妥善保存。
          </p>
        </div>

        <div class="setup-block">
          <div class="setup-block__title">2. 输入当前动态口令完成绑定</div>
          <el-input v-model="code" placeholder="6 位动态口令" maxlength="6" class="setup-code" />
        </div>
      </template>

      <template #footer>
        <el-button @click="setupVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!code" @click="handleEnable">
          确认启用
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { authApi } from '@/api/auth'

const enabled = ref(false)
const loading = ref(false)
const submitting = ref(false)
const setupVisible = ref(false)
const setup = ref(null)
const code = ref('')
const disableCode = ref('')

async function loadStatus() {
  loading.value = true
  try {
    enabled.value = await authApi.mfaStatus()
  } catch {
    enabled.value = false
  } finally {
    loading.value = false
  }
}

async function handleBeginSetup() {
  submitting.value = true
  try {
    setup.value = await authApi.mfaSetup()
    code.value = ''
    setupVisible.value = true
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleEnable() {
  if (!code.value || code.value.length !== 6) {
    ElMessage.warning('请输入 6 位动态口令')
    return
  }
  submitting.value = true
  try {
    await authApi.mfaEnable({ code: code.value })
    ElMessage.success('已启用多因素认证')
    setupVisible.value = false
    await loadStatus()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDisable() {
  if (!disableCode.value || disableCode.value.length !== 6) {
    ElMessage.warning('请输入 6 位动态口令')
    return
  }
  try {
    await ElMessageBox.confirm('停用后登录将不再需要动态口令，确定继续吗？', '停用 MFA', {
      confirmButtonText: '停用',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await authApi.mfaDisable({ code: disableCode.value })
    ElMessage.success('已停用多因素认证')
    disableCode.value = ''
    await loadStatus()
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.security-card {
  padding: var(--spacing-lg);
}

.security-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.security-status__label {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.security-desc {
  margin: var(--spacing-sm) 0 var(--spacing-lg);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.security-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.security-input {
  max-width: 220px;
}

.setup-block {
  margin-bottom: var(--spacing-lg);
}

.setup-block__title {
  margin-bottom: var(--spacing-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.setup-secret,
.setup-uri {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
  flex-wrap: wrap;
}

.setup-secret code,
.setup-uri code {
  padding: 2px 6px;
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
  font-size: var(--font-size-sm);
  word-break: break-all;
}

.setup-uri__label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.setup-code {
  max-width: 220px;
}
</style>