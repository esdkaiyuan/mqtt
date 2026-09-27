import api from './axios'

export const productApi = {
  /**
   * 获取产品列表
   */
  getList: () => api.get('/products')
}