import api from './axios'

export const propertyHistoryApi = {
  /**
   * 属性时序聚合查询（T-21）：按设备集合与属性标识符集合，在给定时间窗与桶粒度下，
   * 返回逐「设备 × 属性」的聚合序列（PropertySeriesVO[]）。
   *
   * params：
   * - deviceIds：设备 ID，逗号分隔以匹配后端 `List<Long>` 绑定；
   * - identifiers：属性标识符，逗号分隔以匹配后端 `List<String>` 绑定；
   * - startTime / endTime：`yyyy-MM-dd HH:mm:ss`，半开区间，跨度上限见 app.property-history.max-range-days；
   * - bucket：时间桶白名单 1m / 5m / 15m / 30m / 1h / 6h / 1d（缺省 5m）。
   *
   * 失败提示（6225 时间或序列超限 / 6226 属性未建模 / 6227 桶非法 / 2002 设备不存在 / 2003 越权）
   * 由 axios 拦截器统一给出，页面不重复弹错。
   */
  query: (params) => api.get('/properties/history', { params })
}

export default propertyHistoryApi
