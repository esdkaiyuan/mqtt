import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE || '/api'

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.detail || error.message
    console.error('API Error:', message)
    return Promise.reject(error)
  }
)

// Binary downloads use a client without the unwrapping interceptor, which
// would otherwise discard the Content-Disposition filename.
const downloadClient = axios.create({
  baseURL: BASE_URL,
  timeout: 60000
})

function parseFilename(disposition) {
  if (!disposition) return null
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  return match ? decodeURIComponent(match[1]) : null
}

export const dataApi = {
  getData(params = {}) {
    return api.get('/data', { params })
  },

  getDataById(id) {
    return api.get(`/data/${id}`)
  },

  getEvents(params = {}) {
    return api.get('/events', { params })
  },

  getStats(params = {}) {
    return api.get('/stats', { params })
  },

  getDeviceInfo(deviceId) {
    return api.get('/device/info', { params: { device_id: deviceId } })
  },

  async exportData(params = {}) {
    const response = await downloadClient.get('/export', {
      params,
      responseType: 'blob'
    })
    return {
      blob: response.data,
      filename: parseFilename(response.headers['content-disposition'])
    }
  }
}

export const annotationApi = {
  getAnnotations(params = {}) {
    return api.get('/annotations', { params })
  },

  saveAnnotation(annotation) {
    return api.post('/annotations', annotation)
  },

  updateAnnotation(id, updates) {
    return api.put(`/annotations/${id}`, updates)
  },

  deleteAnnotation(id) {
    return api.delete(`/annotations/${id}`)
  }
}

export default api