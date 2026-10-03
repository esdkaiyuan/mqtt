import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PropertyTrendChart from '../PropertyTrendChart.vue'

/**
 * `EchartChart` 在 onMounted 会真实调用 echarts.init，jsdom 下行为不可控，
 * 这里用替身承接 `option` / `height`，直接断言其 props。
 */
const EchartChartStub = {
  name: 'EchartChart',
  props: {
    option: { type: Object, default: null },
    height: { type: String, default: '' }
  },
  template: '<div class="echart-stub"></div>'
}

function mountChart(props = {}) {
  return mount(PropertyTrendChart, {
    props,
    global: { stubs: { EchartChart: EchartChartStub } }
  })
}

function optionOf(wrapper) {
  return wrapper.findComponent(EchartChartStub).props('option')
}

describe('components/chart/PropertyTrendChart', () => {
  it('多序列渲染：按 deviceNames 生成图例名并共享类目轴', () => {
    const wrapper = mountChart({
      title: '属性趋势 · 温度',
      series: [
        {
          deviceId: 1,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [{ time: '2026-10-03 10:00:00', avg: '12.5', count: 3 }]
        },
        {
          deviceId: 2,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [{ time: '2026-10-03 10:00:00', avg: '20', count: 4 }]
        }
      ],
      deviceNames: { 1: '车间温度计' }
    })

    const option = optionOf(wrapper)
    expect(option).not.toBeNull()
    expect(option.series).toHaveLength(2)
    // 序列名回退顺序：deviceNames 命中 → 未命中回落「设备 {id}」
    expect(option.series[0].name).toBe('车间温度计')
    expect(option.series[1].name).toBe('设备 2')
    // 多序列时展示图例
    expect(option.legend.show).toBe(true)
    // 数值型按聚合口径取值，字符串被转为 Number
    expect(option.series[0].data).toEqual([12.5])
    expect(option.series[1].data).toEqual([20])
    expect(option.xAxis.data).toEqual(['2026-10-03 10:00:00'])
  })

  it('无数据时 option 为 null 并由副标题承载空态文案', () => {
    const wrapper = mountChart({
      series: [
        { deviceId: 1, identifier: 'temperature', dataType: 'float', numeric: true, points: [] }
      ]
    })

    expect(optionOf(wrapper)).toBeNull()
    expect(wrapper.find('.chart-subtitle').text()).toBe('该时间段无上报数据')
  })

  it('序列集合为空时同样为空态', () => {
    const wrapper = mountChart({ series: [] })

    expect(optionOf(wrapper)).toBeNull()
    expect(wrapper.find('.chart-subtitle').exists()).toBe(true)
  })

  it('非数值型属性只画样本数（忽略聚合字段）', () => {
    const wrapper = mountChart({
      series: [
        {
          deviceId: 1,
          identifier: 'status',
          dataType: 'enum',
          numeric: false,
          points: [{ time: '2026-10-03 10:00:00', count: 7, avg: '1' }]
        }
      ]
    })

    const option = optionOf(wrapper)
    expect(option.series[0].data).toEqual([7])
  })

  it('显式选择 count 聚合时数值型也只画样本数', () => {
    const wrapper = mountChart({
      aggregation: 'count',
      series: [
        {
          deviceId: 1,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [{ time: '2026-10-03 10:00:00', count: 5, avg: '12.5' }]
        }
      ]
    })

    const option = optionOf(wrapper)
    expect(option.series[0].data).toEqual([5])
  })

  it('单序列折线叠加面积渐变，多序列不叠加', () => {
    const single = mountChart({
      series: [
        {
          deviceId: 1,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [{ time: '2026-10-03 10:00:00', avg: '12.5' }]
        }
      ]
    })
    expect(optionOf(single).series[0].areaStyle).toBeTruthy()
    expect(optionOf(single).legend.show).toBe(false)
  })

  it('柱状图按 bar 渲染并保留 boundaryGap', () => {
    const wrapper = mountChart({
      chartType: 'bar',
      series: [
        {
          deviceId: 1,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [{ time: '2026-10-03 10:00:00', avg: '12.5' }]
        }
      ]
    })

    const option = optionOf(wrapper)
    expect(option.series[0].type).toBe('bar')
    expect(option.xAxis.boundaryGap).toBe(true)
  })

  it('桶内无样本时留空形成断点', () => {
    const wrapper = mountChart({
      series: [
        {
          deviceId: 1,
          identifier: 'temperature',
          dataType: 'float',
          numeric: true,
          points: [
            { time: '2026-10-03 10:00:00', avg: '12.5' },
            { time: '2026-10-03 10:10:00', avg: null }
          ]
        }
      ]
    })

    expect(optionOf(wrapper).series[0].data).toEqual([12.5, null])
  })
})
