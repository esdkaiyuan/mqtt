import api from './axios'

/**
 * 设备 CSV 导入 API（T-53）。
 */
export const deviceImportApi = {
  /** 上传 CSV：deviceKey / deviceName / productKey 为必需列，返回一次性凭据。 */
  importFile: (file) => {
    const form = new FormData()
    form.append('file', file)
    return api.post('/devices/import', form)
  },

  /** 下载导入模板（CSV，带 BOM）。 */
  downloadTemplate: () =>
    api.get('/devices/import-template', { responseType: 'blob', returnFullResponse: true })
}

export default deviceImportApi