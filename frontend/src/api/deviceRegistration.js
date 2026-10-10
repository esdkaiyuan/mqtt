import api from './axios'

/**
 * 设备批量注册与激活 API（T-30）。
 *
 * 对标阿里云 IoT / 腾讯云物联网的「批量注册实例 → 激活 → 烧录凭据」标准流程。
 * axios 响应拦截器已解包为业务体，此处直接返回 Promise。
 */
export const deviceRegistrationApi = {
  /**
   * 批量注册设备（单批 1~1000）。
   * 响应中的 credentials 为一次性凭据明文，仅本次返回，平台不提供二次查询。
   */
  batchRegister: (data) => api.post('/device-registration/batch', data),

  /**
   * 设备激活：消费激活码。设备侧调用，该端点免登录。
   */
  activate: (data) => api.post('/device-registration/activate', data),

  /** 查询注册批次分页 */
  pageBatches: (params) => api.get('/device-registration/batches', { params }),

  /** 查询注册批次详情（含一次性凭据与失败明细） */
  getBatch: (batchNo) => api.get(`/device-registration/batches/${batchNo}`)
}

/**
 * 把批量注册结果整理成可直接下载的 CSV。
 *
 * 凭据是一次性的，页面关闭即不可再取，因此必须提供导出动作而不是让用户手工复制。
 * 字段统一加引号并转义内部引号，防止含逗号的描述破坏表格结构。
 */
export function buildCredentialsCsv(result) {
  const header = 'deviceKey,username,secret,activateCode'
  const rows = (result?.credentials || []).map((c) =>
    [c.deviceKey, c.username, c.secret, c.activateCode || '']
      .map((field) => `"${String(field ?? '').replace(/"/g, '""')}"`)
      .join(',')
  )
  return [header, ...rows].join('\n')
}

/** 触发浏览器下载（凭据一次性，下载后不可再取）。 */
export function downloadTextFile(filename, content) {
  // 加 BOM：否则 Excel 打开中文列名会乱码
  const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
