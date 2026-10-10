import { describe, it, expect, vi, beforeEach } from 'vitest'

const post = vi.fn()
vi.mock('../axios', () => ({
  default: {
    get: vi.fn(),
    post: (...args) => post(...args),
    delete: vi.fn()
  }
}))

const {
  devicePointTableApi,
  isRegisterType,
  createEmptyPoint,
  parsePointsJson,
  stringifyPointsJson,
  EXECUTOR_OPTIONS,
  POINT_TYPE_OPTIONS,
  DATA_TYPE_OPTIONS
} = await import('../devicePointTable')

beforeEach(() => post.mockReset())

/**
 * 点位表 API 层纯函数用例（T-31 P2）。
 *
 * 重点覆盖 `parsePointsJson` —— 它是「现场点表迁移」这个真实场景的入口，
 * 现场文件来自 PLC 工程文档与组态软件导出，字段名千差万别。宽松解析的价值在于
 * 让用户把文件贴进来就能用，而逐条跳过缺字段行（而非整体失败）让 50 行里错 1 行
 * 仍然能导入另外 49 行。
 */
describe('devicePointTable helpers', () => {
  describe('isRegisterType', () => {
    it('寄存器类与线圈类点位需要数字地址', () => {
      expect(isRegisterType('HOLDING_REGISTER')).toBe(true)
      expect(isRegisterType('INPUT_REGISTER')).toBe(true)
      expect(isRegisterType('COIL')).toBe(true)
      expect(isRegisterType('DISCRETE_INPUT')).toBe(true)
    })

    it('tag 与节点点位允许自由文本地址', () => {
      expect(isRegisterType('TAG')).toBe(false)
      expect(isRegisterType('NODE')).toBe(false)
      expect(isRegisterType(undefined)).toBe(false)
    })
  })

  describe('createEmptyPoint', () => {
    it('默认值与后端 V18 建表默认一致', () => {
      const p = createEmptyPoint()
      expect(p.pointType).toBe('HOLDING_REGISTER')
      expect(p.dataType).toBe('float')
      expect(p.executor).toBe('EDGE')
      expect(p.readIntervalMs).toBe(60000)
      expect(p.enabled).toBe(1)
    })

    it('可指定执行方', () => {
      expect(createEmptyPoint('PLATFORM').executor).toBe('PLATFORM')
    })
  })

  describe('parsePointsJson', () => {
    it('解析顶层数组并填默认值', () => {
      const { points } = parsePointsJson('[{"pointKey":"temp","address":"40001"}]')
      expect(points).toHaveLength(1)
      expect(points[0].pointKey).toBe('temp')
      expect(points[0].address).toBe('40001')
      expect(points[0].executor).toBe('EDGE')
      expect(points[0].readIntervalMs).toBe(60000)
    })

    it('兼容 { points: [...] } 与 { properties: [...] } 两种外层结构', () => {
      expect(parsePointsJson('{"points":[{"name":"a"}]}').points[0].pointKey).toBe('a')
      expect(parsePointsJson('{"properties":[{"name":"b"}]}').points[0].pointKey).toBe('b')
    })

    it('识别常见字段别名（组态软件导出格式）', () => {
      const { points } = parsePointsJson(JSON.stringify([
        { point_key: 'k1', reg: 40001, factor: 0.1, cycle: 5000 },
        { identifier: 'k2', addr: 'DB1.DBD0', interval: 3000 },
        { tag: 'k3', address: 'Line1.Temp' }
      ]))
      expect(points.map((p) => p.pointKey)).toEqual(['k1', 'k2', 'k3'])
      expect(points[0].address).toBe('40001')
      expect(points[0].scale).toBe(0.1)
      expect(points[0].readIntervalMs).toBe(5000)
      expect(points[1].address).toBe('DB1.DBD0')
      expect(points[2].address).toBe('Line1.Temp')
    })

    it('跳过缺标识的行但保留其余行', () => {
      const { points, skipped } = parsePointsJson('[{"pointKey":"ok"},{"address":"40001"},{"name":""}]')
      // 三行里只有一行可用：整体失败会让另外两行的正确配置也白填
      expect(points).toHaveLength(1)
      expect(points[0].pointKey).toBe('ok')
      expect(skipped).toHaveLength(2)
      expect(skipped[0].index).toBe(1)
    })

    it('跳过非对象行', () => {
      const { points, skipped } = parsePointsJson('[{"pointKey":"ok"}, 123, null]')
      expect(points).toHaveLength(1)
      expect(skipped).toHaveLength(2)
    })

    it('保留显式指定的执行方', () => {
      const { points } = parsePointsJson('[{"pointKey":"a","executor":"PLATFORM"}]')
      expect(points[0].executor).toBe('PLATFORM')
    })

    it('enabled 为 false 或 0 时置为停用', () => {
      const { points } = parsePointsJson('[{"pointKey":"a","enabled":false},{"pointKey":"b","enabled":0}]')
      expect(points[0].enabled).toBe(0)
      expect(points[1].enabled).toBe(0)
    })

    it('非法 JSON 抛出可读错误', () => {
      expect(() => parsePointsJson('{not json')).toThrow(/JSON 解析失败/)
    })

    it('空内容报错', () => {
      expect(() => parsePointsJson('   ')).toThrow('请粘贴内容')
    })

    it('找不到点位数组时报错', () => {
      expect(() => parsePointsJson('{"foo":1}')).toThrow(/未找到点位数组/)
    })

    it('非数值 scale 归为 null 而非 NaN', () => {
      // NaN 会一路带到提交体，让服务端 BigDecimal 解析抛难懂的异常
      const { points } = parsePointsJson('[{"pointKey":"a","scale":"abc"}]')
      expect(points[0].scale).toBeNull()
    })

    it('scale 为 0 时保留 0（由表单校验给出更明确的提示）', () => {
      const { points } = parsePointsJson('[{"pointKey":"a","scale":0}]')
      expect(points[0].scale).toBe(0)
    })
  })

  describe('stringifyPointsJson', () => {
    it('导出后可被自身解析器还原（往返一致）', () => {
      const original = [{ pointKey: 'temp', address: '40001', scale: 0.1, unit: '℃' }]
      const { points } = parsePointsJson(stringifyPointsJson(original))
      expect(points[0].pointKey).toBe('temp')
      expect(points[0].scale).toBe(0.1)
      expect(points[0].unit).toBe('℃')
    })
  })

  describe('下发接口', () => {
    /**
     * 路径多一级 `/dispatch`，写错成 `/points/{id}/dispatch` 之类不会有任何编译期提示，
     * 只能靠这里守住 —— 前端页面点「下发到网关」时才发现 404 的排查成本很高。
     */
    it('下发走 POST 且路径为点位表路径下的 dispatch', () => {
      devicePointTableApi.dispatch(100)

      expect(post).toHaveBeenCalledTimes(1)
      expect(post).toHaveBeenCalledWith('/devices/100/points/dispatch')
    })
  })

  describe('选项常量', () => {
    it('执行方只有 EDGE 与 PLATFORM 两个值', () => {
      expect(EXECUTOR_OPTIONS.map((o) => o.value)).toEqual(['EDGE', 'PLATFORM'])
    })

    it('数据类型覆盖后端允许的全部值', () => {
      // 后端 normalize 只接受这五个，多一个前端就会提交出 6288
      expect(DATA_TYPE_OPTIONS.map((o) => o.value))
        .toEqual(['int', 'float', 'double', 'bool', 'string'])
    })

    it('点位类型覆盖 PointTypeValue 的六种', () => {
      expect(POINT_TYPE_OPTIONS.map((o) => o.value)).toEqual([
        'HOLDING_REGISTER', 'INPUT_REGISTER', 'COIL', 'DISCRETE_INPUT', 'TAG', 'NODE'
      ])
    })
  })
})