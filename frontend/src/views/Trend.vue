<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="情绪趋势" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="range" @change="loadTrend">
      <van-tab title="日" name="day" />
      <van-tab title="周" name="week" />
      <van-tab title="月" name="month" />
    </van-tabs>

    <div class="card">
      <div class="card-title">情绪趋势</div>
      <div ref="trendRef" class="chart"></div>
    </div>

    <div class="card">
      <div class="card-title">情绪分布</div>
      <div ref="distRef" class="chart"></div>
    </div>

  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getTrend, getDistribution } from '../api/diary'
import { useTheme } from '../composables/useTheme'

const router = useRouter()
const range = ref('week')
const trendRef = ref(null)
const distRef = ref(null)
let trendChart = null
let distChart = null
let trendPoints = []
let distData = null

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
      backgroundColor: dark ? '#2c2c2e' : '#fff',
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
  const points = await getTrend(range.value)
  renderTrend(points)
}

async function loadDist() {
  const data = await getDistribution()
  renderDist(data)
}

function onResize() {
  trendChart?.resize()
  distChart?.resize()
}

onMounted(() => {
  loadTrend()
  loadDist()
  window.addEventListener('resize', onResize)
})

// 系统切换深浅色时，用同一份数据重画一遍（颜色是画的时候取的）
watch(isDark, () => {
  if (trendPoints.length) renderTrend(trendPoints)
  if (distData) renderDist(distData)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  distChart?.dispose()
})
</script>

<style scoped>
.card {
  margin-top: 16px;
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
</style>
