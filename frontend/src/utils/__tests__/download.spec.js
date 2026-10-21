import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

const message = vi.hoisted(() => ({ warning: vi.fn(), success: vi.fn(), error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: message }))

const { filenameFromDisposition, downloadResponse } = await import('../download')

let clickSpy

beforeEach(() => {
  clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:test')
  globalThis.URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  clickSpy.mockRestore()
  vi.clearAllMocks()
})

describe('utils/download', () => {
  it('优先解析 filename* 的 UTF-8 文件名', () => {
    const header = "attachment; filename=\"fallback.csv\"; filename*=UTF-8''%E6%97%A5%E5%BF%97.csv"
    expect(filenameFromDisposition(header)).toBe('日志.csv')
  })

  it('解析普通 filename 并去掉引号', () => {
    expect(filenameFromDisposition('attachment; filename="device-logs.csv"')).toBe('device-logs.csv')
  })

  it('缺头或非法编码时返回 null', () => {
    expect(filenameFromDisposition(null)).toBeNull()
    expect(filenameFromDisposition("attachment; filename*=UTF-8''%E0%A4%A")).toBeNull()
  })

  it('下载 Blob 并在截断时提示', () => {
    const response = {
      data: new Blob(['a,b\n1,2']),
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''%E6%97%A5%E5%BF%97.csv",
        'x-export-truncated': 'true'
      }
    }

    const name = downloadResponse(response, 'fallback.csv')

    expect(name).toBe('日志.csv')
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(message.warning).toHaveBeenCalledWith(expect.stringContaining('截断'))
  })

  it('无响应头时使用兜底文件名，且未截断不提示', () => {
    const response = { data: new Blob(['x']), headers: {} }

    expect(downloadResponse(response, 'fallback.csv')).toBe('fallback.csv')
    expect(message.warning).not.toHaveBeenCalled()
  })
})
