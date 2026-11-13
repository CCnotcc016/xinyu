<template>
  <div class="page">
    <van-nav-bar title="情绪趋势" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="range" @change="loadTrend">
      <van-tab title="日" name="day" />
      <van-tab title="周" name="week" />
      <van-tab title="月" name="month" />
    </van-tabs>

    <div class="xinyu-cols">
      <div class="card xinyu-glass">
        <div class="card-title">情绪趋势 · {{ rangeLabel }}</div>
        <!-- 「日」视图里可以往前翻：默认看最近 3 日，想回顾某一天就一天一天往回退 -->
        <div v-if="range === 'day'" class="day-nav">
          <van-button size="small" plain @click="shiftDay(1)">← 前一天</van-button>
          <span class="day-nav__label">{{ dayWindowLabel }}</span>
          <van-button size="small" plain :disabled="dayOffset === 0" @click="shiftDay(-1)">
            后一天 →
          </van-button>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>

      <div class="card xinyu-glass">
        <div class="card-title">情绪分布</div>
        <div ref="distRef" class="chart"></div>
      </div>
    </div>

    <div class="card xinyu-glass">
      <div class="card-title">
        情绪日历
        <span class="card-title__hint">
          {{ calendar?.streak ?? 0 }} 天连续 · 最长 {{ calendar?.maxStreak ?? 0 }} 天
        </span>
      </div>
      <div ref="calendarRef" class="chart chart--calendar"></div>
      <p class="heat-legend">颜色越深表示那天的情绪越好，灰色是没记录的日子。</p>
    </div>

    <div class="card xinyu-glass">
      <div class="card-title">
        情绪洞察
        <span v-if="insights" class="card-title__hint">共 {{ insights.diaryCount }} 篇有情绪分</span>
      </div>
      <template v-if="insights && insights.diaryCount >= 2">
        <ul v-if="insights.highlights.length" class="insight-lines">
          <li v-for="line in insights.highlights" :key="line">{{ line }}</li>
        </ul>
        <div v-for="dim in dimensions" :key="dim.key" class="dim">
          <div class="dim__title">{{ dim.label }}</div>
          <div v-for="g in dim.items.slice(0, 3)" :key="g.key" class="bar-row">
            <span class="bar-row__key">{{ g.key }}</span>
            <span class="bar-row__track">
              <i class="bar-row__fill" :style="{ width: barWidth(g.avgScore) }" />
            </span>
            <span class="bar-row__val">{{ g.avgScore.toFixed(1) }} · {{ g.count }} 篇</span>
          </div>
        </div>
      </template>
      <p v-else class="insight-empty">
        再写几篇带 AI 情绪分的日记，这里就能看出天气、场景和心情之间的关系了。
      </p>
    </div>

    <div class="card xinyu-glass">
      <div class="card-title">
        AI 情绪周报
        <span v-if="report" class="card-title__hint">{{ report.weekStart }} ~ {{ report.weekEnd }}</span>
      </div>
      <template v-if="report">
        <div v-if="report.diaryCount > 0" class="report-stats">
          <div class="report-stat">
            <b>{{ report.diaryCount }}</b>
            <span>本周篇数</span>
          </div>
          <div class="report-stat">
            <b>{{ report.avgScore ?? '—' }}</b>
            <span>平均情绪分</span>
          </div>
          <div class="report-stat">
            <b :class="deltaClass">{{ deltaLabel }}</b>
            <span>较上周</span>
          </div>
          <div class="report-stat">
            <b>{{ report.dominantLabel || '—' }}</b>
            <span>主要情绪</span>
          </div>
        </div>
        <div v-if="report.labels?.length" class="report-tags">
          <van-tag v-for="t in report.labels" :key="t.name" plain type="primary">{{ t.name }} {{ t.value }}</van-tag>
        </div>
        <p class="report-text">{{ report.content }}</p>
        <div class="report-foot">
          <span class="report-source">{{ sourceLabel }}</span>
          <van-button size="mini" plain :loading="refreshing" @click="refreshReport">重新生成</van-button>
        </div>
      </template>
      <van-loading v-else class="report-loading" size="20">周报生成中…</van-loading>
    </div>

    <van-cell
      class="recap-entry xinyu-glass"
      title="月度 / 年度回顾"
      label="把这一段时间的情绪收成一页，看看自己的变化"
      icon="calendar-o"
      is-link
      @click="router.push('/recap')"
    />

  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import echarts from '../utils/echarts'
import { getTrend, getDistribution, getCalendar, getInsights, getWeeklyReport } from '../api/diary'
import { useTheme } from '../composables/useTheme'

const router = useRouter()
const range = ref('week')
const trendRef = ref(null)
const distRef = ref(null)
const calendarRef = ref(null)
let trendChart = null
let distChart = null
let calendarChart = null
let trendPoints = []
let distData = null
let calendarData = null

const calendar = ref(null)
const insights = ref(null)
const report = ref(null)
const refreshing = ref(false)

// 周报里的「较上周」：涨了绿、跌了黄，不评判好坏，只提示变化
const deltaLabel = computed(() => {
  const d = report.value?.delta
  if (d == null) return '—'
  return d > 0 ? `+${d}` : `${d}`
})
const deltaClass = computed(() => {
  const d = report.value?.delta
  if (d == null) return ''
  return d > 0 ? 'delta-up' : d < 0 ? 'delta-down' : ''
})
// 四个维度按固定顺序展示，空维度（比如没人写场景）直接不出现
const dimensions = computed(() => {
  const i = insights.value
  if (!i) return []
  return [
    { key: 'weather', label: '天气', items: i.weather || [] },
    { key: 'scene', label: '场景', items: i.scene || [] },
    { key: 'tags', label: '情绪标签', items: i.tags || [] },
    { key: 'weekday', label: '星期', items: i.weekday || [] }
  ].filter((d) => d.items.length > 0)
})

/** 条形长度按 10 分制折算，最低留一点宽度，别让 1 分看起来像没有数据 */
function barWidth(score) {
  return `${Math.max(8, Math.min(100, (score / 10) * 100))}%`
}

const sourceLabel = computed(() => {
  const s = report.value?.source
  if (s === 'AI') return '由 AI 根据本周统计生成'
  if (s === 'MOCK') return '未配置大模型，这是本地生成的示例周报'
  return '本周还没有日记'
})

// 标题里的窗口长度要跟后端 StatsService 的 days 对齐，「日」看的是最近 3 日
const RANGE_LABEL = { day: '最近 3 日', week: '最近 7 天', month: '最近 30 天' }

// 「日」视图往回翻的天数：0 = 今天（窗口 前2天~今天），1 = 再退一天，以此类推
const dayOffset = ref(0)

function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

// 窗口最后一天（end 参数），往后翻会变回今天
const anchorDate = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() - dayOffset.value)
  return d
})

const dayWindowLabel = computed(() => {
  const end = anchorDate.value
  const start = new Date(end)
  start.setDate(start.getDate() - 2)
  const s = `${start.getMonth() + 1}月${start.getDate()}日`
  const e = `${end.getMonth() + 1}月${end.getDate()}日`
  return dayOffset.value === 0 ? `${s} - ${e}（含今天）` : `${s} - ${e}`
})

// 往回翻之后标题再写「最近 3 日」就不对了，直接把窗口日期当标题
const rangeLabel = computed(() => {
  if (range.value === 'day' && dayOffset.value > 0) return dayWindowLabel.value
  return RANGE_LABEL[range.value] || ''
})

function shiftDay(delta) {
  const next = dayOffset.value + delta
  if (next < 0) return
  dayOffset.value = next
  loadTrend()
}

// 坐标轴、文字、气泡都要跟着深浅色换，否则深色下图表文字看不清
const { isDark } = useTheme()

function chartBase() {
  const dark = isDark.value
  // 深色下用亮青绿，浅色下用深青绿，和全局品牌色保持一致
  const accent = dark ? '#2dd4bf' : '#0d9488'
  const subColor = dark ? '#a3a3a3' : '#6e7079'
  const textColor = dark ? '#f5f5f5' : '#323233'
  return {
    accent,
    subColor,
    textStyle: { color: textColor },
    // 轴线跟文字同色，网格线用暗一点的灰，默认的浅灰在深色底上太刺眼
    axisLine: { lineStyle: { color: subColor } },
    splitLine: { lineStyle: { color: dark ? '#2a2a2c' : '#e0e6f1' } },
    tooltip: {
      // 图表嵌在玻璃卡里，自己再画一层底色就会把背后的极光糊掉
      backgroundColor: 'transparent',
      borderColor: dark ? '#3a3a3c' : '#ebedf0',
      textStyle: { color: textColor }
    }
  }
}

function renderTrend(points) {
  trendPoints = points
  const { accent, subColor, textStyle, tooltip, axisLine, splitLine } = chartBase()
  trendChart = trendChart || echarts.init(trendRef.value)
  trendChart.setOption({
    textStyle,
    tooltip: { ...tooltip, trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      axisLabel: { color: subColor },
      axisLine
    },
    yAxis: {
      type: 'value',
      min: 1,
      max: 10,
      interval: 1,
      axisLabel: { color: subColor },
      axisLine,
      splitLine
    },
    series: [
      {
        name: '情绪分',
        type: 'line',
        smooth: true,
        data: points.map((p) => p.avgScore),
        areaStyle: { opacity: 0.15 },
        lineStyle: { color: accent },
        itemStyle: { color: accent }
      }
    ]
  })
}

function renderDist(data) {
  distData = data
  const { subColor, textStyle, tooltip } = chartBase()
  distChart = distChart || echarts.init(distRef.value)
  distChart.setOption({
    textStyle,
    tooltip: { ...tooltip, trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '65%'],
        data: data.labels.map((i) => ({ name: i.name, value: i.value })),
        label: { formatter: '{b}: {c}', color: subColor }
      }
    ]
  })
}

async function loadTrend() {
  // 只有「日」视图才带 end 参数（周/月固定看最近），回到今天时就不传了
  const end = range.value === 'day' && dayOffset.value > 0 ? fmtDate(anchorDate.value) : undefined
  const points = await getTrend(range.value, end)
  renderTrend(points)
}

async function loadDist() {
  const data = await getDistribution()
  renderDist(data)
}

/**
 * 情绪热力图。
 *
 * 用 ECharts 的 calendar 坐标系而不是自己画格子：日历会自动按周排好列、
 * 标出月份，手机窄屏下也会等比缩放，比手写 div 网格省事也更整齐。
 */
function renderCalendar(data) {
  calendarData = data
  const { subColor, textStyle, tooltip, accent } = chartBase()
  const days = data.days || []
  if (!days.length) return
  calendarChart = calendarChart || echarts.init(calendarRef.value)
  calendarChart.setOption({
    textStyle,
    tooltip: {
      ...tooltip,
      formatter: (p) => {
        const d = days[p.dataIndex]
        if (!d.count) return `${d.date}<br/>这天没有记录`
        return `${d.date}<br/>情绪分 ${d.avgScore}<br/>${d.count} 篇 · ${d.label || ''}`
      }
    },
    // 用日记均分当热力值，1-10 直接映射颜色深浅
    visualMap: {
      show: false,
      min: 1,
      max: 10,
      inRange: { color: ['#c7d2fe', '#7dd3fc', '#5eead4', accent] }
    },
    calendar: {
      range: [days[0].date, days[days.length - 1].date],
      cellSize: ['auto', 12],
      left: 30,
      right: 6,
      top: 28,
      bottom: 6,
      itemStyle: { borderWidth: 2, borderColor: 'transparent', color: 'rgba(140,145,160,0.16)' },
      splitLine: { show: false },
      dayLabel: { show: false },
      monthLabel: { color: subColor, fontSize: 10, margin: 4 },
      yearLabel: { show: false }
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        // 没写日记的那天值是 null，视觉上留成浅灰空位
        data: days.map((d) => [d.date, d.avgScore])
      }
    ]
  })
}

async function loadCalendar() {
  const data = await getCalendar(91)
  calendar.value = data
  renderCalendar(data)
}

async function loadReport(force) {
  if (force) refreshing.value = true
  try {
    report.value = await getWeeklyReport(undefined, !!force)
  } finally {
    refreshing.value = false
  }
}

function refreshReport() {
  loadReport(true)
}

function onResize() {
  trendChart?.resize()
  distChart?.resize()
  calendarChart?.resize()
}

async function loadInsights() {
  try {
    insights.value = await getInsights()
  } catch {
    // 洞察是附加信息，拉不到就整块隐藏，不影响上面三张图
  }
}

onMounted(() => {
  loadTrend()
  loadDist()
  loadCalendar()
  loadInsights()
  loadReport(false)
  window.addEventListener('resize', onResize)
})

// 系统切换深浅色时，用同一份数据重画一遍（颜色是画的时候取的）
watch(isDark, () => {
  if (trendPoints.length) renderTrend(trendPoints)
  if (distData) renderDist(distData)
  if (calendarData) renderCalendar(calendarData)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  distChart?.dispose()
  calendarChart?.dispose()
})
</script>

<style scoped>
.card {
  margin-top: 16px;
  padding: 14px 12px 8px;
  border-radius: 18px;
}
/* 排成两列之后由 grid gap 控制间距，margin 会把卡片顶下去 */
@media (min-width: 1024px) {
  .card {
    margin-top: 0;
  }
  .xinyu-cols {
    margin-top: 16px;
  }
  .chart {
    height: 300px;
  }
  .chart--calendar {
    /* 日历是宽扁的，PC 上不需要跟折线图一样高 */
    height: 220px;
  }
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  margin: 4px 4px 8px;
}
.chart {
  width: 100%;
  height: 260px;
}
.chart--calendar {
  height: 190px;
}
.heat-legend {
  margin: 0 4px 6px;
  font-size: 12px;
  color: var(--van-text-color-2, #8a8f99);
}
.insight-lines {
  margin: 0 4px 10px;
  padding-left: 18px;
  color: var(--van-text-color, #323233);
  font-size: 14px;
  line-height: 1.7;
}
.insight-empty {
  margin: 4px;
  color: var(--van-text-color-2, #8a8f99);
  font-size: 13px;
  line-height: 1.6;
}
.dim {
  margin: 0 4px 12px;
}
.dim__title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--van-text-color-2, #8a8f99);
}
.bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}
.bar-row__key {
  width: 48px;
  flex: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-row__track {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--van-gray-2, #ebedf0);
  overflow: hidden;
}
.bar-row__fill {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #0d9488, #2dd4bf);
}
.bar-row__val {
  width: 84px;
  flex: none;
  text-align: right;
  color: var(--van-text-color-2, #8a8f99);
}
.card-title__hint {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--van-text-color-2, #8a8f99);
}
.report-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin: 4px 0 10px;
}
.report-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 11px;
  color: var(--van-text-color-2, #8a8f99);
}
.report-stat b {
  font-size: 15px;
  color: var(--van-text-color, #323233);
}
.delta-up {
  color: var(--xinyu-accent);
}
.delta-down {
  color: #f59e0b;
}
.report-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 4px 10px;
}
.report-text {
  margin: 0 4px 10px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}
.report-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 4px 8px;
}
.report-source {
  font-size: 12px;
  color: var(--van-text-color-2, #8a8f99);
}
.recap-entry {
  margin-top: 12px;
  border-radius: 18px;
}
.report-loading {
  padding: 24px 0;
  text-align: center;
}
.day-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 4px 10px;
}
.day-nav__label {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: var(--van-text-color-2, #8a8f99);
}
</style>
