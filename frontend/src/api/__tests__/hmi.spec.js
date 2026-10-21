import { describe, it, expect, vi, beforeEach } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args),
    post: (...args) => post(...args),
    put: (...args) => put(...args),
    delete: (...args) => del(...args)
  }
}))

const { hmiApi } = await import('../hmi')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
  del.mockReset()
})

describe('hmiApi', () => {
  it('列表与详情走 /hmi-views', () => {
    hmiApi.list()
    hmiApi.detail(7)
    expect(get).toHaveBeenNthCalledWith(1, '/hmi-views')
    expect(get).toHaveBeenNthCalledWith(2, '/hmi-views/7')
  })

  it('草稿以 deviceId 查询，不落库', () => {
    hmiApi.draft(11)
    expect(get).toHaveBeenCalledWith('/hmi-views/draft', { params: { deviceId: 11 } })
  })

  it('创建 / 更新 / 删除路径一致', () => {
    hmiApi.create({ name: '面板' })
    hmiApi.update(7, { name: '面板2' })
    hmiApi.remove(7)
    expect(post).toHaveBeenCalledWith('/hmi-views', { name: '面板' })
    expect(put).toHaveBeenCalledWith('/hmi-views/7', { name: '面板2' })
    expect(del).toHaveBeenCalledWith('/hmi-views/7')
  })
})
