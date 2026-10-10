<template>
  <div class="device-credential-panel">
    <h3 class="card-title">证书与令牌</h3>
    <p class="card-desc">认证方式由所属产品决定；证书私钥与令牌仅签发时返回一次，平台不留存</p>

    <div v-loading="authModeLoading" class="device-credential-panel__body">
      <div class="credential-meta">
        <span class="credential-meta__label">认证方式</span>
        <span class="auth-badge" :class="`auth-badge--${authModeClass}`">
          {{ authModeLabel }}
        </span>
      </div>

      <template v-if="authMode === 'X509'">
        <p class="credential-desc">
          为设备签发由平台 CA 签署的 X.509 证书；重复签发将覆盖旧证书，旧证书需另行吊销。
        </p>
        <div class="credential-actions">
          <el-button
            type="primary"
            :loading="issuing"
            :disabled="!canWrite"
            @click="$emit('issue-certificate')"
          >
            签发证书
          </el-button>
          <el-button
            type="danger"
            plain
            :loading="revoking"
            :disabled="!canWrite"
            @click="$emit('revoke-certificate')"
          >
            吊销证书
          </el-button>
        </div>
        <div v-if="revoked" class="credential-note">
          最近吊销：序列号 {{ revoked.serial }}，时间 {{ formatTime(revoked.revokedAt) }}
          <template v-if="revoked.reason">，原因：{{ revoked.reason }}</template>
        </div>
      </template>

      <template v-else-if="authMode === 'JWT'">
        <p class="credential-desc">
          为设备签发有时效的 JWT 令牌，设备以令牌作为 MQTT 密码接入；过期后需重新签发。
        </p>
        <div class="credential-actions">
          <el-button
            type="primary"
            :loading="tokenIssuing"
            :disabled="!canWrite"
            @click="$emit('issue-token')"
          >
            签发令牌
          </el-button>
        </div>
      </template>

      <template v-else-if="authMode === 'SECRET'">
        <p class="credential-desc">
          当前产品使用一机一密，设备接入凭据在创建设备时生成，无需签发证书或令牌。
        </p>
      </template>

      <template v-else>
        <p class="credential-desc">正在获取产品认证方式，请稍候。</p>
      </template>

      <p v-if="!canWrite" class="credential-hint">仅管理员可签发或吊销设备凭据。</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { AUTH_MODE_LABELS } from '@/composables/useDeviceCredentials'

const props = defineProps({
  authMode: { type: String, default: '' },
  authModeLoading: { type: Boolean, default: false },
  issuing: { type: Boolean, default: false },
  revoking: { type: Boolean, default: false },
  tokenIssuing: { type: Boolean, default: false },
  revoked: { type: Object, default: null },
  canWrite: { type: Boolean, default: true }
})

defineEmits(['issue-certificate', 'revoke-certificate', 'issue-token'])

const authModeLabel = computed(() => AUTH_MODE_LABELS[props.authMode] || '未知')

const authModeClass = computed(() => (props.authMode || 'unknown').toLowerCase())

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.device-credential-panel__body {
  min-height: 60px;
}

.credential-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.credential-meta__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.auth-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.auth-badge--x509 {
  background: var(--color-success-light);
  color: var(--color-success);
}

.auth-badge--jwt {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.credential-desc {
  margin: 0 0 var(--spacing-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.credential-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.credential-note {
  margin-top: var(--spacing-md);
  padding: 8px 10px;
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.credential-hint {
  margin: var(--spacing-md) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
