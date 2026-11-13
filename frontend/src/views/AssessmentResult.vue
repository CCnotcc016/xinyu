<template>
  <div class="page">
    <van-nav-bar title="自评结果" left-arrow @click-left="router.push('/assessment')" />

    <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="6" />

    <template v-else-if="data">
      <!-- 风险阳性：先给求助路径，再谈分数 -->
      <div v-if="data.riskFlag" class="crisis">
        <div class="crisis__title">
          <van-icon name="warning-o" /> 先说一件重要的事
        </div>
        <p>
          你在「有不如死掉或伤害自己的念头」这一题上选了有。这种时候一个人硬扛很难，
          请先联系一个信任的人，或者打下面任意一个电话，都有人接。
        </p>
        <div class="crisis__lines">
          <span>全国心理援助热线 12356</span>
          <span>急救 120 · 报警 110</span>
        </div>
        <p class="crisis__tail">
          你现在写下的这些感受是真实的，也值得被认真对待。如果愿意，可以先把今天的心情记进日记里。
        </p>
      </div>

      <div class="score xinyu-glass">
        <div class="score__ring">
          <span class="score__num">{{ data.totalScore }}</span>
          <span class="score__max">/ {{ data.maxScore }}</span>
        </div>
        <div class="score__side">
          <div class="score__scale">{{ data.scaleName }}</div>
          <van-tag :type="tagType(data.severity)" size="medium">{{ data.severityLabel }}</van-tag>
          <div class="score__time">{{ dateLabel(data.createdAt) }}</div>
        </div>
      </div>

      <template v-if="data.dimensions && data.dimensionMax > 0">
        <div class="section-title">分维度</div>
        <div class="dims xinyu-glass">
          <div v-for="(value, name) in data.dimensions" :key="name" class="dim">
            <div class="dim__head">
              <span>{{ name }}</span>
              <span class="dim__value">{{ value }}/{{ data.dimensionMax }}</span>
            </div>
            <van-progress
              :percentage="Math.round((value / data.dimensionMax) * 100)"
              :show-pivot="false"
              stroke-width="6"
            />
          </div>
        </div>
      </template>

      <div class="section-title">
        AI 解读
        <van-tag v-if="data.aiSource === 'MOCK'" plain type="warning">本地建议</van-tag>
      </div>
      <div class="ai xinyu-glass">
        <p class="ai__text">{{ data.aiInterpretation }}</p>
        <van-button
          v-if="data.aiSource === 'MOCK'"
          size="small"
          plain
          type="primary"
          :loading="refreshing"
          @click="refresh"
        >
          重新生成解读
        </van-button>
      </div>

      <div class="section-title">这一步可以做什么</div>
      <div class="advice xinyu-glass">{{ data.advice }}</div>

      <van-collapse v-model="openPanel" class="detail">
        <van-collapse-item title="逐题明细" name="items">
          <div v-for="item in data.breakdown" :key="item.index" class="detail__row">
            <span class="detail__q">{{ item.index }}. {{ item.question }}</span>
            <span class="detail__a" :class="{ 'detail__a--on': item.score > 0 }">{{ item.answerLabel }}</span>
          </div>
        </van-collapse-item>
      </van-collapse>

      <MedicalDisclaimer />

      <div class="actions">
        <van-button block round plain type="primary" @click="router.push(`/assessment/${data.scaleKey}`)">
          再测一次
        </van-button>
        <van-button block round plain class="actions__ghost" @click="router.push('/assessment')">
          返回自评首页
        </van-button>
      </div>

      <p class="foot-note">
        量表来源：{{ scaleSource }}。分数会保存在你的账号里，只用于你自己回看趋势，管理员只能在危机预警时看到记录摘要。
      </p>
    </template>

    <van-empty v-else description="记录不存在或已删除" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getAssessment, listScales, reInterpret } from '../api/assessment'
import MedicalDisclaimer from '../components/MedicalDisclaimer.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const refreshing = ref(false)
const data = ref(null)
const openPanel = ref([])
const scaleSource = ref('国际通用自评量表')

onMounted(async () => {
  await load()
  loadSource()
})

async function load() {
  loading.value = true
  try {
    data.value = await getAssessment(route.params.id)
  } catch (e) {
    showToast(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSource() {
  try {
    const res = await listScales()
    const found = (res || []).find((s) => s.key === data.value?.scaleKey)
    if (found?.source) scaleSource.value = found.source
  } catch (e) {
    // 出处只是补充信息，拿不到就不显示具体名称
  }
}

async function refresh() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    data.value = await reInterpret(data.value.id)
    showToast('解读已更新')
  } catch (e) {
    showToast(e?.message || '暂时生成不了，请稍后再试')
  } finally {
    refreshing.value = false
  }
}

function dateLabel(value) {
  if (!value) return ''
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function tagType(severity) {
  if (severity === 'NONE') return 'success'
  if (severity === 'MILD') return 'primary'
  if (severity === 'MODERATE') return 'warning'
  return 'danger'
}
</script>

<style scoped>
.crisis {
  margin: 10px 0;
  padding: 14px 16px;
  border: 1px solid rgba(238, 10, 36, 0.35);
  border-radius: 18px;
  background: rgba(238, 10, 36, 0.08);
}
.crisis__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: #ee0a24;
  font-size: 14px;
  font-weight: 600;
}
.crisis p {
  margin: 0 0 8px;
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.7;
}
.crisis__lines {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.crisis__lines span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(238, 10, 36, 0.14);
  color: #ee0a24;
  font-size: 12px;
  font-weight: 600;
}
.crisis__tail {
  margin-bottom: 0 !important;
}
.score {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 16px;
  border-radius: 20px;
}
.score__ring {
  display: flex;
  flex-shrink: 0;
  align-items: baseline;
  justify-content: center;
  width: 92px;
  height: 92px;
  border-radius: 50%;
  border: 3px solid var(--xinyu-accent);
  padding-top: 26px;
  box-sizing: border-box;
}
.score__num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}
.score__max {
  margin-left: 2px;
  color: var(--van-text-color-3);
  font-size: 11px;
}
.score__side {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.score__scale {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}
.score__time {
  color: var(--van-text-color-3);
  font-size: 11px;
}
.dims {
  padding: 14px 16px;
  border-radius: 18px;
}
.dim + .dim {
  margin-top: 12px;
}
.dim__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 12px;
}
.dim__value {
  color: var(--van-text-color-3);
}
.ai,
.advice {
  padding: 14px 16px;
  border-radius: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--van-text-color-2);
  white-space: pre-wrap;
}
.ai__text {
  margin: 0 0 10px;
}
.detail {
  margin-top: 12px;
  border-radius: 14px;
  overflow: hidden;
}
.detail__row {
  display: flex;
  gap: 10px;
  padding: 6px 0;
  font-size: 12px;
  line-height: 1.6;
}
.detail__q {
  flex: 1;
  color: var(--van-text-color-2);
}
.detail__a {
  flex-shrink: 0;
  color: var(--van-text-color-3);
}
.detail__a--on {
  color: #ff6b35;
  font-weight: 600;
}
.actions {
  margin-top: 8px;
}
.actions__ghost {
  margin-top: 10px;
  color: var(--van-text-color-2);
}
.foot-note {
  margin: 16px 2px 28px;
  color: var(--van-text-color-3);
  font-size: 11px;
  line-height: 1.7;
}
</style>
