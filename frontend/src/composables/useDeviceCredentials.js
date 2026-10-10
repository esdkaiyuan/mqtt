import { ref } from 'vue'
import { deviceApi } from '@/api/device'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'

/**
 * 认证方式展示名，与后端 `product.auth_mode` 枚举一一对应。
 * 前端只做展示与分派，取值集合以后端 `AuthModeValue` 为准。
 */
export const AUTH_MODE_LABELS = {
  SECRET: '一机一密',
  X509: 'X.509 证书',
  JWT: 'JWT 令牌'
}

/**
 * 设备证书与令牌操作（T-25）。
 *
 * 认证方式由产品决定，设备详情接口不返回 `authMode`，因此按 `productId`
 * 从产品列表里取该产品的 `auth_mode`；取不到时回落 `SECRET`，与后端缺省一致。
 * 签发结果（证书私钥、令牌明文）只在内存保留，关闭弹层或刷新页面即消失，
 * 与服务端「不落库、不二次查询」的口径对齐。
 *
 * @param {import('vue').Ref<number|string|null>} deviceId
 * @param {import('vue').Ref<number|null>} productId
 */
export function useDeviceCredentials(deviceId, productId) {
  const authMode = ref('')
  const authModeLoading = ref(false)
  const issuing = ref(false)
  const revoking = ref(false)
  const tokenIssuing = ref(false)
  /** 一次性签发结果：证书链与私钥 PEM */
  const certificate = ref(null)
  /** 一次性令牌结果：token 与 expiresAt */
  const token = ref(null)
  /** 最近一次吊销结果：serial 与 revokedAt，用于面板展示吊销态 */
  const revoked = ref(null)

  async function loadAuthMode() {
    if (!productId.value) return
    authModeLoading.value = true
    try {
      const products = unwrapResult(await productApi.getList(), [])
      const matched = products.find((item) => item.id === productId.value)
      authMode.value = matched?.authMode || 'SECRET'
    } catch {
      // 失败提示由 axios 拦截器统一给出，此处保留上一次结果避免面板抖动
    } finally {
      authModeLoading.value = false
    }
  }

  /** 签发证书，返回一次性结果；失败由调用方捕获，弹层不打开。 */
  async function issueCertificate() {
    if (!deviceId.value) return null
    issuing.value = true
    try {
      certificate.value = unwrapResult(await deviceApi.issueCertificate(deviceId.value), null)
      // 重新签发即覆盖旧证书，此前记录的吊销态不再代表当前状态
      revoked.value = null
      return certificate.value
    } finally {
      issuing.value = false
    }
  }

  /** 吊销当前有效证书；reason 可空，上限 64 字符由服务端校验。 */
  async function revokeCertificate(reason = '') {
    if (!deviceId.value) return null
    revoking.value = true
    try {
      revoked.value = unwrapResult(
        await deviceApi.revokeCertificate(deviceId.value, { reason }),
        null
      )
      certificate.value = null
      return revoked.value
    } finally {
      revoking.value = false
    }
  }

  /** 签发设备令牌，返回一次性结果。 */
  async function issueToken() {
    if (!deviceId.value) return null
    tokenIssuing.value = true
    try {
      token.value = unwrapResult(await deviceApi.issueToken(deviceId.value), null)
      return token.value
    } finally {
      tokenIssuing.value = false
    }
  }

  return {
    authMode,
    authModeLoading,
    issuing,
    revoking,
    tokenIssuing,
    certificate,
    token,
    revoked,
    loadAuthMode,
    issueCertificate,
    revokeCertificate,
    issueToken
  }
}

export default useDeviceCredentials
