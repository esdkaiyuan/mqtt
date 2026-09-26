import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
api.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('API Error:', error.response?.data || error.message)
    return Promise.reject(error)
  }
)

// Data API
export const dataApi = {
  // Get historical data
  getData(params = {}) {
    return api.get('/data', { params })
  },

  // Get data by ID
  getDataById(id) {
    return api.get(`/data/${id}`)
  },

  // Get fall events
  getFallEvents(params = {}) {
    return api.get('/events', { params })
  },

  // Get statistics
  getStats() {
    return api.get('/stats')
  },

  // Get device info
  getDeviceInfo() {
    return api.get('/device/info`)
  },

  // Export data as CSV
  exportData(params = {}) {
    return api.get('/export', {
      params,
      responseType: 'blob'
    })
  }
}

// Annotation API
export const annotationApi = {
  // Save annotation
  saveAnnotation(annotation) {
    return api.post('/annotations', annotation)
  },

  // Update annotation
  updateAnnotation(id, updates) {
    return api.put(`/annotations/${id}`, updates)
  },

  // Delete annotation
  deleteAnnotation(id) {
    return api.delete(`/annotations/${id}`)
  },

  // Get annotations
  getAnnotations(params = {}) {
    return api.get('/annotations', { params })
  }
}

export default api
