import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('../axios', () => ({
  default: {
    get: (...args) => get(...args),
    post: (...args) => post(...args),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

const { sceneApi } = await import('../scene')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('sceneApi 版本历史', () => {
  it('版本列表走 GET /scenes/{id}/versions', () => {
    sceneApi.listSceneVersions(5)
    expect(get).toHaveBeenCalledWith('/scenes/5/versions')
  })

  it('回滚走 POST /scenes/{id}/versions/{versionId}/restore', () => {
    sceneApi.restoreSceneVersion(5, 70)
    expect(post).toHaveBeenCalledWith('/scenes/5/versions/70/restore')
  })
})
