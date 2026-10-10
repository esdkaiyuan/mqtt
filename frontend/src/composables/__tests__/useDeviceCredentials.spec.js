import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/device', () => ({
  deviceApi: {
    issueCertificate: vi.fn(),
    revokeCertificate: vi.fn(),
    issueToken: vi.fn()
  }
}))

vi.mock('@/api/product', () => ({
  productApi: { getList: vi.fn() }
}))

import { deviceApi } from '@/api/device'
import { productApi } from '@/api/product'
import { AUTH_MODE_LABELS, useDeviceCredentials } from '../useDeviceCredentials'

const PRODUCT_X509 = { id: 1, productKey: 'env-sensor', productName: '环境传感器', authMode: 'X509' }

const ISSUED = {
  deviceId: 7,
  subject: 'CN=env-sensor.sensor-1,O=MQTT Cloud',
  serial: 'A1B2C3D4E5F60718',
  notBefore: '2026-10-06T00:00:00',
  notAfter: '2027-10-06T00:00:00',
  certificatePem: 'CERT',
  privateKeyPem: 'KEY'
}

const REVOKED = {
  deviceId: 7,
  serial: 'A1B2C3D4E5F60718',
  revokedAt: '2026-10-06T10:00:00',
  reason: '固件重刷'
}

const TOKEN = {
  deviceId: 7,
  username: 'env-sensor&sensor-1',
  token: 'eyJhbGci',
  expiresAt: '2026-10-06T11:00:00'
}

function setup({ deviceId = 7, productId = 1 } = {}) {
  const deviceIdRef = ref(deviceId)
  const productIdRef = ref(productId)
  const state = useDeviceCredentials(deviceIdRef, productIdRef)
  return { deviceIdRef, productIdRef, state }
}

describe('composables/useDeviceCredentials', () => {
  beforeEach(() => {
    productApi.getList.mockResolvedValue({ code: 200, data: [PRODUCT_X509] })
    deviceApi.issueCertificate.mockResolvedValue({ code: 200, data: ISSUED })
    deviceApi.revokeCertificate.mockResolvedValue({ code: 200, data: REVOKED })
    deviceApi.issueToken.mockResolvedValue({ code: 200, data: TOKEN })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('认证方式标签与后端枚举一一对应', () => {
    expect(Object.keys(AUTH_MODE_LABELS).sort()).toEqual(['JWT', 'SECRET', 'X509'])
    expect(AUTH_MODE_LABELS.X509).toBe('X.509 证书')
  })

  it('loadAuthMode 按 productId 命中产品认证方式', async () => {
    const { state } = setup({ productId: 1 })

    await state.loadAuthMode()

    expect(state.authMode.value).toBe('X509')
    expect(state.authModeLoading.value).toBe(false)
  })

  it('产品列表缺少对应 productId 时回落 SECRET', async () => {
    productApi.getList.mockResolvedValue({ code: 200, data: [{ id: 99, authMode: 'JWT' }] })
    const { state } = setup({ productId: 1 })

    await state.loadAuthMode()

    expect(state.authMode.value).toBe('SECRET')
  })

  it('产品未设置 authMode 时同样回落 SECRET', async () => {
    productApi.getList.mockResolvedValue({ code: 200, data: [{ id: 1, productKey: 'legacy' }] })
    const { state } = setup({ productId: 1 })

    await state.loadAuthMode()

    expect(state.authMode.value).toBe('SECRET')
  })

  it('productId 为空时不发起请求', async () => {
    const { state } = setup({ productId: null })

    await state.loadAuthMode()

    expect(productApi.getList).not.toHaveBeenCalled()
    expect(state.authMode.value).toBe('')
  })

  it('查询失败时保留上一次结果且不抛出', async () => {
    const { state } = setup({ productId: 1 })
    await state.loadAuthMode()
    expect(state.authMode.value).toBe('X509')

    productApi.getList.mockRejectedValueOnce(new Error('network'))
    await state.loadAuthMode()

    expect(state.authMode.value).toBe('X509')
    expect(state.authModeLoading.value).toBe(false)
  })

  it('issueCertificate 存一次性结果并清空此前的吊销态', async () => {
    const { state } = setup()
    state.revoked.value = REVOKED

    const result = await state.issueCertificate()

    expect(deviceApi.issueCertificate).toHaveBeenCalledWith(7)
    expect(result).toEqual(ISSUED)
    expect(state.certificate.value).toEqual(ISSUED)
    expect(state.revoked.value).toBeNull()
    expect(state.issuing.value).toBe(false)
  })

  it('revokeCertificate 透传原因并清空一次性证书', async () => {
    const { state } = setup()
    state.certificate.value = ISSUED

    const result = await state.revokeCertificate('固件重刷')

    expect(deviceApi.revokeCertificate).toHaveBeenCalledWith(7, { reason: '固件重刷' })
    expect(result).toEqual(REVOKED)
    expect(state.revoked.value).toEqual(REVOKED)
    expect(state.certificate.value).toBeNull()
    expect(state.revoking.value).toBe(false)
  })

  it('issueToken 存一次性令牌结果', async () => {
    const { state } = setup()

    const result = await state.issueToken()

    expect(deviceApi.issueToken).toHaveBeenCalledWith(7)
    expect(result).toEqual(TOKEN)
    expect(state.token.value).toEqual(TOKEN)
    expect(state.tokenIssuing.value).toBe(false)
  })

  it('deviceId 为空时不发起签发请求', async () => {
    const { state } = setup({ deviceId: null })

    expect(await state.issueCertificate()).toBeNull()
    expect(await state.revokeCertificate('x')).toBeNull()
    expect(await state.issueToken()).toBeNull()
    expect(deviceApi.issueCertificate).not.toHaveBeenCalled()
    expect(deviceApi.revokeCertificate).not.toHaveBeenCalled()
    expect(deviceApi.issueToken).not.toHaveBeenCalled()
  })

  it('签发失败时抛出且不写入结果', async () => {
    deviceApi.issueCertificate.mockRejectedValueOnce(new Error('6249'))
    const { state } = setup()

    await expect(state.issueCertificate()).rejects.toThrow('6249')
    expect(state.certificate.value).toBeNull()
    expect(state.issuing.value).toBe(false)
  })
})
