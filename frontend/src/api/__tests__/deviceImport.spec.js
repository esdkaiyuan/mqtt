import { describe, expect, it, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args),
    post: (...args) => post(...args)
  }
}))

const { deviceImportApi } = await import('../deviceImport')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('deviceImportApi', () => {
  it('上传 CSV 以 multipart/form-data 提交', () => {
    const file = new File(['deviceKey,deviceName,productKey'], 'devices.csv', { type: 'text/csv' })
    deviceImportApi.importFile(file)

    expect(post).toHaveBeenCalledTimes(1)
    const [url, form] = post.mock.calls[0]
    expect(url).toBe('/devices/import')
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('file')).toBe(file)
  })

  it('模板下载返回完整响应以读取文件名', () => {
    deviceImportApi.downloadTemplate()

    expect(get).toHaveBeenCalledWith('/devices/import-template', {
      responseType: 'blob',
      returnFullResponse: true
    })
  })
})