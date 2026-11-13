/*
 * ECharts 按需引入。
 *
 * 直接 import * as echarts from 'echarts' 会把全部图表类型和组件打进 Trend 页面的
 * chunk（约 1MB）。这里只注册项目真正用到的：折线、饼图、热力图，
 * 以及网格、提示框、日历坐标系、视觉映射和 Canvas 渲染器。
 * 新增图表类型时记得在这里补上，否则图表会「空白但没报错」。
 */
import * as echarts from 'echarts/core'
import { LineChart, PieChart, HeatmapChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  CalendarComponent,
  VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  PieChart,
  HeatmapChart,
  GridComponent,
  TooltipComponent,
  CalendarComponent,
  VisualMapComponent,
  CanvasRenderer
])

export default echarts
