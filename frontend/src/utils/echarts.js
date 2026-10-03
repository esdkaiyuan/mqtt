/**
 * ECharts 按需引入
 * <p>
 * 只注册项目实际用到的图表与组件，避免打包整个 echarts（体积从约 1.1MB 降到约 300KB）。
 * 新增图表类型时需在此处补注册对应模块。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

export default echarts