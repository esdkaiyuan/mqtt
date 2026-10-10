import api from './axios'

export const deviceApi = {
  /**
   * 创建设备
   */
  create: (data) => api.post('/devices', data),

  /**
   * 获取设备列表
   */
  getList: (params) => api.get('/devices', { params }),

  /**
   * 获取设备详情
   */
  getDetail: (deviceId) => api.get(`/devices/${deviceId}`),

  /**
   * 更新设备
   */
  update: (deviceId, data) => api.put(`/devices/${deviceId}`, data),

  /**
   * 删除设备
   */
  delete: (deviceId) => api.delete(`/devices/${deviceId}`),

  /**
   * 获取在线设备
   */
  getOnline: () => api.get('/devices/online'),

  /**
   * 获取设备状态历史
   */
  getStatusHistory: (deviceId) => api.get(`/devices/${deviceId}/status`),

  // ---------- 物模型派生数据（T-14 只读） ----------

  /**
   * 属性最新值列表（按 deviceKey 归属校验）
   */
  getProperties: (deviceKey) => api.get(`/devices/${deviceKey}/properties`),

  /**
   * 事件记录分页（按 deviceKey 归属校验，size 上限 100）
   */
  getEvents: (deviceKey, params) => api.get(`/devices/${deviceKey}/events`, { params }),

  // ---------- 命令下发与服务调用（T-15） ----------

  /**
   * 可下发能力：rw 属性与服务入参，供前端生成动态表单
   * 无物模型时 modeled=false 且两个集合为空
   */
  getCommandCapability: (deviceKey) => api.get(`/devices/${deviceKey}/command-capability`),

  /**
   * 下发命令：type=property_set / service，callType=sync 等待回执到终态
   */
  sendCommand: (deviceKey, data) => api.post(`/devices/${deviceKey}/commands`, data),

  /**
   * 命令记录分页（按创建时间倒序）
   */
  getCommandRecords: (deviceKey, params) => api.get(`/devices/${deviceKey}/commands`, { params }),

  // ---------- 设备影子（T-16） ----------

  /**
   * 查询设备影子：desired / reported / delta 三份状态与版本号
   * 影子不存在时返回空映射与 version=0；modeled=false 表示产品未定义物模型
   */
  getDeviceShadow: (deviceKey) => api.get(`/devices/${deviceKey}/shadow`),

  /**
   * 写入影子期望值：等价于 type=property_set 命令
   * 在线设备返回 SENT 命令记录，离线设备返回 QUEUED 并在上线后自动补发
   */
  setDeviceShadowDesired: (deviceKey, params) =>
    api.put(`/devices/${deviceKey}/shadow/desired`, { params }),

  // ---------- 设备分组与标签批量操作（T-18） ----------

  /**
   * 批量下发命令：目标为「手选设备 ∪ 分组（含子分组）∪ 标签」并集，逐台生成命令记录并强制异步。
   * 返回 BatchOperationResult（total / succeeded / failed / items），单台失败只记该台 error。
   */
  batchCommands: (data) => api.post('/devices/batch/commands', data),

  /** 批量启用连接许可（逐台幂等，不产生 commandId） */
  batchEnable: (target) => api.post('/devices/batch/enable', target),

  /** 批量禁用连接许可并踢线（逐台幂等，不产生 commandId） */
  batchDisable: (target) => api.post('/devices/batch/disable', target),

  /** 批量加入（action=ADD）/ 移出（action=REMOVE）分组 */
  batchAssignGroup: (data) => api.post('/devices/batch/groups', data),

  /** 批量打标（action=ADD）/ 去标（action=REMOVE） */
  batchAssignTag: (data) => api.post('/devices/batch/tags', data),

  // ---------- 设备日志时间线（T-20） ----------

  /**
   * 设备统一日志时间线：聚合报文 / 命令 / 事件 / 状态变更四类记录，按发生时间倒序分页。
   * types 逗号分隔（MESSAGE/COMMAND/EVENT/STATUS），startTime / endTime 形如 yyyy-MM-dd HH:mm:ss，
   * 时间跨度上限 31 天（后端参数 app.device-log.max-range-days）。
   */
  getDeviceLogs: (deviceId, params) => api.get(`/devices/${deviceId}/logs`, { params }),

  // ---------- 证书与令牌（T-25） ----------

  /**
   * 签发设备 X.509 证书：调用方须具备产品管理权限，产品 auth_mode 为 X509。
   * 返回证书链与私钥 PEM；私钥不留存，仅本次响应返回。
   */
  issueCertificate: (deviceId) => api.post(`/devices/${deviceId}/certificate`),

  /**
   * 吊销设备当前有效证书：reason 可空（上限 64 字符），吊销后认证即时失效。
   */
  revokeCertificate: (deviceId, data) => api.post(`/devices/${deviceId}/certificate/revoke`, data),

  /**
   * 签发设备 JWT 令牌：产品 auth_mode 为 JWT 时可用，明文令牌仅本次返回。
   */
  issueToken: (deviceId) => api.post(`/devices/${deviceId}/token`),

  // ---------- 设备拓扑（T-26） ----------

  /**
   * 在网关下创建设备并直接挂载为子设备：产品与归属取自网关，请求体不含 productId，
   * 返回含一次性明文密钥的创建结果。能力未开启时后端返回 404。
   */
  createSubDevice: (gatewayId, data) => api.post(`/devices/${gatewayId}/sub-devices`, data),

  /**
   * 把既有设备挂载为指定网关的子设备：校验父设备、同产品、层级与子设备数量上限。
   */
  attachSubDevice: (gatewayId, subDeviceId) => api.post(`/devices/${gatewayId}/sub-devices/${subDeviceId}`),

  /**
   * 解绑子设备：仅清除父子关系，设备行与凭据保留，可再挂载到其它网关。
   */
  detachSubDevice: (gatewayId, subDeviceId) =>
    api.delete(`/devices/${gatewayId}/sub-devices/${subDeviceId}`),

  /**
   * 分页查询网关下的子设备，返回含 deviceKey / status / lastSeen 的 Page<Device>。
   */
  listSubDevices: (gatewayId, params) => api.get(`/devices/${gatewayId}/sub-devices`, { params }),

  /**
   * 查询设备的父网关：普通设备与网关无父，返回 data 为 null。
   */
  getParentDevice: (subDeviceId) => api.get(`/devices/${subDeviceId}/parent`),

  /**
   * 变更设备拓扑角色，取值 DIRECT / GATEWAY / SUB。
   * 置为 GATEWAY 要求无父；置为 DIRECT 或 SUB 要求无子设备。
   */
  changeNodeType: (deviceId, nodeType) => api.put(`/devices/${deviceId}/node-type`, { nodeType })
}
