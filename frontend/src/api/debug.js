import api from './axios'

export const debugApi = {
  /**
   * 调试上下文：Broker 连接参数、上下行 Topic、物模型投影与属性最新值。
   * 不含设备密钥，密码位只返回提示。
   */
  getContext: (deviceKey) => api.get(`/devices/${deviceKey}/debug/context`),

  /**
   * 模拟上行：类型 data / heartbeat / lwt / reply / ota。
   * 与真机上报走同一条摄取管线，会真实触发规则 / 告警 / 影子 / SSE。
   */
  simulate: (deviceKey, data) => api.post(`/devices/${deviceKey}/debug/simulate`, data),

  /**
   * 属性代码生成。
   * target=device（javascript/python/java/arduino）或 host（javascript/python/java）。
   */
  getCode: (deviceKey, params) => api.get(`/devices/${deviceKey}/debug/code`, { params })
}

export default debugApi
