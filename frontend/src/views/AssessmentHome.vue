<template>
  <div class="page">
    <van-nav-bar title="心理自评" left-arrow @click-left="router.back()" />

    <MedicalDisclaimer />

    <div class="intro xinyu-glass">
      <h3>选一份量表开始</h3>
      <p>
        按最近两周的真实感受作答，没有对错。分数只用来提示当前状态，不是诊断结论；需要确认时请去线下医院面诊。
      </p>
    </div>

    <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="3" />

    <template v-else>
      <div
        v-for="s in scales"
        :key="s.key"
        class="scale xinyu-glass"
        @click="router.push(`/assessment/${s.key}`)"
      >
        <div class="scale__head">
          <span class="scale__name">{{ s.shortName }}</span>
          <van-tag plain type="primary">{{ s.questionCount }} 题</van-tag>
        </div>
        <div class="scale__sub">{{ s.name }}</div>
        <div class="scale__meta">
          <span>{{ s.subtitle }}</span>
          <van-icon name="arrow" class="scale__arrow" />
        </div>
      </div>

      <template v-if="history.length">
        <div class="section-title">我的自评记录</div>
        <van-swipe-cell v-for="h in history" :key="h.id">
          <van-cell
            :title="h.scaleName"
            :label="dateLabel(h.createdAt)"
            is-link
            @click="router.push(`/assessment/result/${h.id}`)"
          >
            <template #value>
              <div class="record__value">
                <span class="record__score">{{ h.totalScore }}/{{ h.maxScore }}</span>
                <van-tag :type="tagType(h.severity)">{{ h.severityLabel }}</van-tag>
              </div>
            </template>
          </van-cell>
          <template #right>
            <van-button square type="danger" text="删除" class="record__del" @click="remove(h)" />
          </template>
        </van-swipe-cell>
      </template>

      <van-empty v-else-if="!loading" description="还没有自评记录" />

      <div class="foot">
        <span @click="router.push('/legal/disclaimer')">查看免责声明</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { deleteAssessment, listAssessments, listScales } from '../api/assessment'
import MedicalDisclaimer from '../components/MedicalDisclaimer.vue'

const router = useRouter()
const scales = ref([])
const history = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [scaleRes, historyRes] = await Promise.all([listScales(), listAssessments(10)])
    scales.value = scaleRes || []
    history.value = historyRes || []
  } catch (e) {
    showToast('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
})

function dateLabel(value) {
  if (!value) return ''
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 左滑删除一条历史记录，删了就没了，先确认一次 */
async function remove(record) {
  try {
    await showConfirmDialog({
      title: '删除这条记录？',
      message: '删除后无法恢复，自评分数也不再计入历史。',
      confirmButtonText: '删除'
    })
  } catch (e) {
    return
  }
  try {
    await deleteAssessment(record.id)
    history.value = history.value.filter((h) => h.id !== record.id)
    showToast('已删除')
  } catch (e) {
    showToast(e?.message || '删除失败，请稍后再试')
  }
}

/** 严重程度配色：越重越暖，避免用绿色让人误以为「没事」 */
function tagType(severity) {
  if (severity === 'NONE') return 'success'
  if (severity === 'MILD') return 'primary'
  if (severity === 'MODERATE') return 'warning'
  return 'danger'
}
</script>

<style scoped>
.intro {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 18px;
}
.intro h3 {
  margin: 0 0 6px;
  font-size: 15px;
}
.intro p {
  margin: 0;
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.7;
}
.scale {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 18px;
  cursor: pointer;
}
.scale__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.scale__name {
  font-size: 16px;
  font-weight: 600;
}
.scale__sub {
  margin-top: 4px;
  color: var(--van-text-color-2);
  font-size: 12px;
}
.scale__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  color: var(--van-text-color-3);
  font-size: 12px;
}
.scale__arrow {
  color: var(--xinyu-accent);
}
.record__value {
  display: flex;
  align-items: center;
  gap: 6px;
}
.record__score {
  font-size: 13px;
  font-weight: 600;
}
.record__del {
  height: 100%;
}
.foot {
  margin: 16px 0 24px;
  text-align: center;
  color: var(--van-text-color-3);
  font-size: 12px;
  text-decoration: underline;
}
</style>
