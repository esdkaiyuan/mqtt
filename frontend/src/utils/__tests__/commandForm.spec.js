import { describe, expect, it } from 'vitest'
import {
  buildParams,
  commandTopic,
  controlFor,
  defaultValue,
  initialValues,
  missingRequired,
  propertyFields,
  serviceFields
} from '../commandForm'

const CAPABILITY = {
  modeled: true,
  version: 1,
  properties: [
    { identifier: 'targetTemp', type: 'int', min: 16, max: 30, integer: true, enumKeys: [], textLength: null },
    { identifier: 'power', type: 'bool', min: null, max: null, integer: false, enumKeys: [], textLength: null }
  ],
  services: [
    {
      identifier: 'reboot',
      callType: 'async',
      input: [
        { identifier: 'delay', type: 'int', min: 0, max: 60, integer: true, enumKeys: [], textLength: null, required: true }
      ]
    }
  ]
}

describe('utils/commandForm', () => {
  it('数据类型映射到对应控件，未知类型退化为文本输入', () => {
    expect(controlFor('int')).toBe('number')
    expect(controlFor('bool')).toBe('switch')
    expect(controlFor('enum')).toBe('select')
    expect(controlFor('date')).toBe('date')
    expect(controlFor('struct')).toBe('json')
    expect(controlFor('array')).toBe('json')
    expect(controlFor('unknown')).toBe('input')
  })

  it('布尔初值为 false，其余留空', () => {
    expect(defaultValue('bool')).toBe(false)
    expect(defaultValue('int')).toBe('')
  })

  it('属性字段不带必填，服务入参保留 required 与数值范围', () => {
    const props = propertyFields(CAPABILITY)
    expect(props.map((item) => item.identifier)).toEqual(['targetTemp', 'power'])
    expect(props[0].required).toBe(false)
    expect(props[0].min).toBe(16)
    expect(props[0].integer).toBe(true)

    const params = serviceFields(CAPABILITY.services[0])
    expect(params[0].identifier).toBe('delay')
    expect(params[0].required).toBe(true)
  })

  it('初值按字段列表生成', () => {
    const values = initialValues(propertyFields(CAPABILITY))
    expect(values).toEqual({ targetTemp: '', power: false })
  })

  it('必填缺失时返回对应标识符', () => {
    const fields = serviceFields(CAPABILITY.services[0])
    expect(missingRequired(fields, { delay: '' })).toEqual(['delay'])
    expect(missingRequired(fields, { delay: 3 })).toEqual([])
  })

  it('组装 params 时跳过空值并解析 struct / array 的 JSON 文本', () => {
    const fields = [
      { identifier: 'targetTemp', control: 'number' },
      { identifier: 'power', control: 'switch' },
      { identifier: 'ext', control: 'json' }
    ]

    const { params } = buildParams(fields, { targetTemp: 26, power: false, ext: '{"a":1}' })
    expect(params).toEqual({ targetTemp: 26, power: false, ext: { a: 1 } })

    const blank = buildParams(fields, { targetTemp: '', power: false, ext: '' })
    expect(blank.params).toEqual({ power: false })
  })

  it('struct / array 的非法 JSON 返回错误而不抛异常', () => {
    const fields = [{ identifier: 'ext', control: 'json' }]

    const result = buildParams(fields, { ext: 'not json' })
    expect(result.params).toBeUndefined()
    expect(result.error).toContain('ext')
  })

  it('下行 Topic 与后端发布主题一致', () => {
    expect(commandTopic('dev-1')).toBe('device/dev-1/cmd/down')
    expect(commandTopic('')).toBe('')
  })
})