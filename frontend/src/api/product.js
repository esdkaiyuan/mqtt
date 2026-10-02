import api from './axios'

export const productApi = {
  /**
   * 获取产品列表
   */
  getList: () => api.get('/products'),

  /**
   * 创建产品
   */
  create: (data) => api.post('/products', data),

  /**
   * 更新产品
   */
  update: (id, data) => api.put(`/products/${id}`, data),

  /**
   * 删除产品（命中业务码 6003 表示产品下存在设备，无法删除）
   */
  remove: (id) => api.delete(`/products/${id}`),

  /**
   * 停用产品（幂等）
   */
  disable: (id) => api.post(`/products/${id}/disable`),

  /**
   * 启用产品（幂等）
   */
  enable: (id) => api.post(`/products/${id}/enable`),

  // ---------- 物模型（T-14） ----------

  /**
   * 查询物模型；未建模时 data.thingModel 为 null、version 为 0
   */
  getThingModel: (id) => api.get(`/products/${id}/thing-model`),

  /**
   * 保存物模型（服务端先校验，通过后版本号 +1）
   */
  saveThingModel: (id, thingModel) => api.put(`/products/${id}/thing-model`, { thingModel }),

  /**
   * 清空物模型（版本号 +1，幂等）
   */
  clearThingModel: (id) => api.delete(`/products/${id}/thing-model`),

  /**
   * 只校验不保存，返回全部错误路径
   */
  validateThingModel: (id, thingModel) =>
    api.post(`/products/${id}/thing-model/validate`, { thingModel }),

  /**
   * 导出 TSL JSON 附件（Blob）
   */
  exportThingModel: (id) =>
    api.get(`/products/${id}/thing-model/export`, { responseType: 'blob' }),

  /**
   * 导入 TSL（等价于保存，同样校验）
   */
  importThingModel: (id, thingModel) =>
    api.post(`/products/${id}/thing-model/import`, { thingModel })
}

export default productApi