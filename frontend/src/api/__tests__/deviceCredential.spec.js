import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import api from '../axios'
import { deviceApi } from '../device'

/** 构造一个 axios 成功响应体并交给自定义 adapter 返回。 */
function okResponse(data, status = 200) {
  return { data, status, statusText: 'OK', headers: {}, config: {} }
}

const ISSUED = {
  deviceId: 7,
  subject: 'CN=env-sensor.sensor-1,O=MQTT Cloud',
  serial: 'A1B2C3D4E5F60718',
  notBefore: '2026-10-06T00:00:00',
  notAfter: '2027-10-06T00:00:00',
  certificatePem: '-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----',
  privateKeyPem: '-----BEGIN PRIVATE KEY-----\nMIIE\n-----END PRIVATE KEY-----'
}

describe('api/device 证书与令牌（T-25）', () => {
  let adapter
  let originalAdapter

  beforeEach(() => {
    // axios 请求拦截器会读取 auth store，测试环境需先激活一个 Pinia 实例。
    setActivePinia(createPinia())
    localStorage.clear()
    originalAdapter = api.defaults.adapter
    adapter = vi.fn().mockResolvedValue(okResponse({ code: 200, message: 'ok', data: ISSUED }))
    api.defaults.adapter = (config) => adapter(config)
  })

  afterEach(() => {
    api.defaults.adapter = originalAdapter
    vi.clearAllMocks()
  })

  it('签发证书走 POST /devices/{id}/certificate 且不带请求体', async () => {
    await deviceApi.issueCertificate(7)

    expect(adapter).toHaveBeenCalledTimes(1)
    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('post')
    expect(config.baseURL).toBe('/api')
    expect(config.url).toBe('/devices/7/certificate')
    expect(config.data).toBeUndefined()
  })

  it('签发证书返回完整 Result 包装体，交由调用方 unwrap', async () => {
    const result = await deviceApi.issueCertificate('sensor-1')

    expect(adapter.mock.calls[0][0].url).toBe('/devices/sensor-1/certificate')
    expect(result.code).toBe(200)
    expect(result.data.certificatePem).toContain('BEGIN CERTIFICATE')
    expect(result.data.privateKeyPem).toContain('BEGIN PRIVATE KEY')
  })

  it('吊销证书走 POST /devices/{id}/certificate/revoke 并透传 reason', async () => {
    adapter.mockResolvedValue(
      okResponse({
        code: 200,
        message: 'ok',
        data: { deviceId: 7, serial: 'A1B2C3D4E5F60718', revokedAt: '2026-10-06T10:00:00', reason: '固件重刷' }
      })
    )

    const result = await deviceApi.revokeCertificate(7, { reason: '固件重刷' })

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('post')
    expect(config.url).toBe('/devices/7/certificate/revoke')
    expect(JSON.parse(config.data).reason).toBe('固件重刷')
    expect(result.data.serial).toBe('A1B2C3D4E5F60718')
  })

  it('吊销不传原因时请求体为空 reason', async () => {
    await deviceApi.revokeCertificate(7, {})

    expect(JSON.parse(adapter.mock.calls[0][0].data).reason).toBeUndefined()
  })

  it('签发令牌走 POST /devices/{id}/token 且不带请求体', async () => {
    adapter.mockResolvedValue(
      okResponse({
        code: 200,
        message: 'ok',
        data: { deviceId: 7, username: 'env-sensor&sensor-1', token: 'eyJhbGci', expiresAt: '2026-10-06T11:00:00' }
      })
    )

    const result = await deviceApi.issueToken(7)

    const config = adapter.mock.calls[0][0]
    expect(config.method).toBe('post')
    expect(config.url).toBe('/devices/7/token')
    expect(config.data).toBeUndefined()
    expect(result.data.token).toBe('eyJhbGci')
  })
})
