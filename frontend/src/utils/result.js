/**
 * 解包后端统一响应包装体 Result<T>。
 *
 * api/axios.js 的响应拦截器只负责在 code !== 200 时抛错，
 * 成功时返回的是整个 `{ code, message, data, timestamp }` 包装对象，
 * 因此业务侧必须显式取 `data` 才是真实数据。
 *
 * 兼容两类返回：
 * - 标准 Result 包装体 -> 取 data
 * - 直接返回数组/普通对象的接口 -> 原样透传
 */
export function unwrapResult(res, fallback) {
  if (res && typeof res === 'object' && !Array.isArray(res) && 'code' in res && 'data' in res) {
    return res.data ?? fallback
  }
  return res ?? fallback
}

export default unwrapResult
