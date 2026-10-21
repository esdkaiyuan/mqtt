import { ElMessage } from 'element-plus'

/**
 * 从 Content-Disposition 解析文件名。
 * <p>
 * 优先 `filename*=UTF-8''...`（Spring 对中文文件名会同时给 filename 与 filename*），
 * 其次普通 `filename="..."`；都解析不到时返回 null，由调用方给兜底名。
 */
export function filenameFromDisposition(header) {
  if (!header || typeof header !== 'string') return null
  const encoded = header.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''))
    } catch {
      // 编码非法时继续尝试普通 filename
    }
  }
  const plain = header.match(/filename\s*=\s*"([^"]+)"/i) || header.match(/filename\s*=\s*([^;]+)/i)
  return plain?.[1]?.trim().replace(/^"|"$/g, '') || null
}

function headerValue(response, name) {
  const headers = response?.headers
  if (!headers) return null
  if (typeof headers.get === 'function') return headers.get(name)
  return headers[name] ?? headers[name.toLowerCase()] ?? null
}

/**
 * 下载 axios 全响应形式的附件。
 * <p>
 * 仅用于 `returnFullResponse: true` 的请求（响应拦截器原样返回 response），
 * 这样既拿得到 Blob，也拿得到 Content-Disposition 与截断标记。
 */
export function downloadResponse(response, fallbackName = 'export.csv') {
  const blob = response?.data instanceof Blob ? response.data : new Blob([response?.data ?? ''])
  const fileName = filenameFromDisposition(headerValue(response, 'content-disposition')) || fallbackName
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 立即 revoke 在部分浏览器会让下载拿不到内容，延后一拍释放
  setTimeout(() => URL.revokeObjectURL(url), 1000)

  if (String(headerValue(response, 'x-export-truncated')).toLowerCase() === 'true') {
    ElMessage.warning('导出结果已达到行数上限，文件内容可能已截断')
  }
  return fileName
}

export default downloadResponse
