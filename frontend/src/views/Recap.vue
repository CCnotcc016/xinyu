<template>
  <div class="page">
    <van-nav-bar title="回顾" left-arrow @click-left="router.back()" />

    <div class="switch">
      <span
        v-for="item in PERIODS"
        :key="item.value"
        class="switch__item"
        :class="{ 'switch__item--on': period === item.value }"
        @click="changePeriod(item.value)"
      >
        {{ item.label }}
      </span>
    </div>

    <div class="period">
      <van-icon name="arrow-left" class="period__arrow" @click="shift(-1)" />
      <span class="period__label">{{ label || '—' }}</span>
      <van-icon
        name="arrow"
        class="period__arrow"
        :class="{ 'period__arrow--off': atLatest }"
        @click="shift(1)"
      />
    </div>

    <van-empty v-if="!loading && data?.empty" :description="emptyText" />

    <template v-else-if="data">
      <div class="stats xinyu-glass">
        <div class="stat">
          <b>{{ data.diaryCount }}</b>
          <span>记录篇数</span>
        </div>
        <div class="stat">
          <b>{{ data.activeDays }}</b>
          <span>记录天数</span>
        </div>
        <div class="stat">
          <b>{{ data.avgScore ?? '—' }}</b>
          <span>平均情绪分</span>
        </div>
        <div class="stat">
          <b>{{ data.maxStreak }}</b>
          <span>最长连续天数</span>
        </div>
      </div>

      <p v-if="deltaText" class="delta" :class="deltaClass">{{ deltaText }}</p>

      <div class="card xinyu-glass">
        <div class="card-title">{{ period === 'year' ? '每个月的情绪' : '每周的情绪' }}</div>
        <div class="bars">
          <div v-for="(b, i) in data.buckets" :key="i" class="bar">
            <span class="bar__value">{{ b.avgScore != null ? b.avgScore.toFixed(1) : '' }}</span>
            <span class="bar__track">
              <i
                class="bar__fill"
                :class="{ 'bar__fill--empty': b.avgScore == null }"
                :style="{ height: barHeight(b.avgScore) }"
              />
            </span>
            <span class="bar__label">{{ b.label }}</span>
          </div>
        </div>
        <p class="bars__hint">柱子越高，情绪分越高；空白表示那几天没有记录。</p>
      </div>

      <div class="card xinyu-glass">
        <div class="card-title">这段时间的你</div>
        <van-cell-group :border="false">
          <van-cell title="主要情绪" :value="data.dominantLabel || '—'" />
          <van-cell
            title="最常写的标签"
            :value="data.topTag ? `${data.topTag} · ${data.topTagCount} 次` : '—'"
          />
          <van-cell title="常出现的场景" :value="data.topScene || '—'" />
          <van-cell title="常出现的天气" :value="data.topWeather || '—'" />
          <van-cell
            title="心情最好的一天"
            :value="dayText(data.bestDay)"
          />
          <van-cell
            title="最难的一天"
            :value="dayText(data.hardestDay)"
          />
        </van-cell-group>
      </div>

      <div v-if="data.highlights?.length" class="card xinyu-glass">
        <div class="card-title">回顾一下</div>
        <p v-for="(line, i) in data.highlights" :key="i" class="highlight">
          <i class="highlight__dot" />{{ line }}
        </p>
      </div>

      <p class="foot">这些数字都来自你自己写下的日记，只给你一个人看。</p>
    </template>

    <van-loading v-else-if="loading" class="loading" size="20">正在整理…</van-loading>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRecap } from '../api/diary'

const router = useRouter()

const PERIODS = [
  { label: '月度', value: 'month' },
  { label: '年度', value: 'year' }
]

const period = ref('month')
const anchor = ref(startOf(new Date(), 'month'))
const data = ref(null)
const loading = ref(false)

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 归一到周期第一天：月度取 1 号，年度取 1 月 1 日 */
function startOf(date, kind) {
  const y = date.getFullYear()
  return kind === 'year' ? `${y}-01-01` : `${y}-${pad(date.getMonth() + 1)}-01`
}

function toDate(value) {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

const label = computed(() => data.value?.label || '')

/** 已经是当前周期就不允许再往后翻，避免出现空白的「未来」 */
const atLatest = computed(() => anchor.value >= startOf(new Date(), period.value))

const emptyText = computed(() =>
  period.value === 'year' ? '这一年还没有留下记录' : '这个月还没有留下记录'
)

const deltaText = computed(() => {
  const d = data.value?.avgScoreDelta
  if (d == null) return ''
  if (Math.abs(d) < 0.05) return '情绪分和上一周期基本持平'
  return d > 0 ? `平均情绪分比上一周期高 ${d.toFixed(1)} 分` : `平均情绪分比上一周期低 ${Math.abs(d).toFixed(1)} 分`
})

const deltaClass = computed(() => {
  const d = data.value?.avgScoreDelta
  if (d == null || Math.abs(d) < 0.05) return 'delta--flat'
  return d > 0 ? 'delta--up' : 'delta--down'
})

function dayText(day) {
  if (!day) return '—'
  return `${day.label} · ${day.avgScore.toFixed(1)} 分 · ${day.count} 篇`
}

/** 情绪分 1-10，映射到 8%~100% 的高度，免得最低分的柱子完全看不见 */
function barHeight(score) {
  if (score == null) return '8%'
  return `${Math.max(8, Math.min(100, (score / 10) * 100))}%`
}

function shift(step) {
  if (step > 0 && atLatest.value) return
  const d = toDate(anchor.value)
  if (period.value === 'year') {
    d.setFullYear(d.getFullYear() + step)
  } else {
    d.setMonth(d.getMonth() + step)
  }
  anchor.value = startOf(d, period.value)
  load()
}

function changePeriod(value) {
  if (period.value === value) return
  period.value = value
  anchor.value = startOf(new Date(), value)
  load()
}

async function load() {
  loading.value = true
  try {
    data.value = await getRecap(period.value, anchor.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.switch {
  display: flex;
  gap: 6px;
  margin: 12px 0 4px;
  padding: 4px;
  border-radius: 12px;
  background: var(--van-background-2);
}
.switch__item {
  flex: 1;
  padding: 8px 0;
  border-radius: 9px;
  font-size: 14px;
  text-align: center;
  color: var(--van-text-color-2);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.switch__item--on {
  background: var(--xinyu-accent);
  color: #fff;
  font-weight: 600;
}
.period {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 14px 0 6px;
}
.period__label {
  min-width: 120px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}
.period__arrow {
  padding: 6px;
  font-size: 16px;
  color: var(--xinyu-accent);
  cursor: pointer;
}
.period__arrow--off {
  color: var(--van-text-color-3);
  opacity: 0.4;
  cursor: default;
}
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 10px 0 8px;
  padding: 16px 8px;
  border-radius: 18px;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.stat b {
  font-size: 19px;
  font-weight: 600;
}
.stat span {
  font-size: 11.5px;
  color: var(--van-text-color-2);
}
.delta {
  margin: 0 4px 6px;
  font-size: 12.5px;
  line-height: 1.6;
}
.delta--up {
  color: #16a34a;
}
.delta--down {
  color: #d97706;
}
.delta--flat {
  color: var(--van-text-color-2);
}
.card {
  margin-top: 12px;
  padding: 14px 16px 16px;
  border-radius: 18px;
}
.card-title {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
}
.bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 120px;
}
.bar {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  height: 100%;
}
.bar__value {
  font-size: 10px;
  color: var(--van-text-color-3);
}
.bar__track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 100%;
}
.bar__fill {
  width: 100%;
  border-radius: 6px 6px 3px 3px;
  background: linear-gradient(180deg, var(--xinyu-accent) 0%, var(--xinyu-accent-glow) 100%);
}
.bar__fill--empty {
  background: var(--van-background-2);
}
.bar__label {
  font-size: 10px;
  color: var(--van-text-color-3);
  white-space: nowrap;
}
.bars__hint {
  margin: 10px 0 0;
  font-size: 11.5px;
  color: var(--van-text-color-3);
}
.highlight {
  display: flex;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 13.5px;
  line-height: 1.7;
}
.highlight:last-child {
  margin-bottom: 0;
}
.highlight__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--xinyu-accent);
}
.foot {
  margin: 18px 4px 8px;
  font-size: 11.5px;
  line-height: 1.6;
  text-align: center;
  color: var(--van-text-color-3);
}
.loading {
  margin-top: 40px;
  text-align: center;
}
</style>
