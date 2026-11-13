<template>
  <div class="page">
    <van-nav-bar :title="scale?.shortName || '心理自评'" left-arrow @click-left="onBack" />

    <MedicalDisclaimer compact />

    <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="6" />

    <template v-else-if="scale">
      <div class="intro xinyu-glass">
        <div class="intro__name">{{ scale.name }}</div>
        <p class="intro__text">{{ scale.instruction }}</p>
        <div class="intro__meta">{{ scale.subtitle }}</div>
      </div>

      <div class="progress">
        <van-progress :percentage="percent" :show-pivot="false" stroke-width="6" />
        <span class="progress__text">已答 {{ answeredCount }} / {{ scale.questionCount }}</span>
      </div>

      <div v-for="(item, index) in scale.items" :key="index" class="q xinyu-glass">
        <div class="q__title">
          <span class="q__index">{{ index + 1 }}</span>
          <span>{{ item }}</span>
        </div>
        <div class="q__options">
          <div
            v-for="opt in scale.options"
            :key="opt.value"
            class="q__option"
            :class="{ 'q__option--on': answers[index] === opt.value }"
            @click="pick(index, opt.value)"
          >
            {{ opt.label }}
          </div>
        </div>
      </div>

      <div class="submit">
        <van-button type="primary" block round :loading="submitting" @click="submit">
          提交并查看结果
        </van-button>
        <p class="submit__hint">提交即表示你了解：结果仅供参考，如有不适请前往线下医院就医。</p>
      </div>
    </template>

    <van-empty v-else description="量表不存在，可能已更新" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listScales, submitAssessment } from '../api/assessment'
import MedicalDisclaimer from '../components/MedicalDisclaimer.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const scale = ref(null)
const answers = ref([])

const answeredCount = computed(() => answers.value.filter((v) => v !== null && v !== undefined).length)
const percent = computed(() =>
  scale.value ? Math.round((answeredCount.value / scale.value.questionCount) * 100) : 0
)

onMounted(async () => {
  try {
    const res = await listScales()
    const found = (res || []).find((s) => s.key === route.params.key)
    scale.value = found || null
    if (found) {
      answers.value = new Array(found.questionCount).fill(null)
    }
  } catch (e) {
    showToast('加载量表失败，请检查网络')
  } finally {
    loading.value = false
  }
})

function pick(index, value) {
  answers.value[index] = value
}

async function submit() {
  if (!scale.value || submitting.value) return
  if (answeredCount.value < scale.value.questionCount) {
    const firstMissing = answers.value.findIndex((v) => v === null || v === undefined)
    showToast(`第 ${firstMissing + 1} 题还没选`)
    return
  }
  submitting.value = true
  try {
    const res = await submitAssessment({
      scaleKey: scale.value.key,
      answers: answers.value.map((v) => Number(v))
    })
    // replace：结果页按返回键直接回到自评首页，不用再看一遍答题页
    router.replace(`/assessment/result/${res.id}`)
  } catch (e) {
    showToast(e?.message || '提交失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

function onBack() {
  if (answeredCount.value > 0) {
    showToast('已答的题目不会保存')
  }
  router.back()
}
</script>

<style scoped>
.intro {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 18px;
}
.intro__name {
  font-size: 15px;
  font-weight: 600;
}
.intro__text {
  margin: 8px 0 6px;
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.7;
}
.intro__meta {
  color: var(--van-text-color-3);
  font-size: 11px;
}
.progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 2px 10px;
}
.progress .van-progress {
  flex: 1;
}
.progress__text {
  flex-shrink: 0;
  color: var(--van-text-color-3);
  font-size: 11px;
}
.q {
  margin: 10px 0;
  padding: 14px 16px;
  border-radius: 18px;
}
.q__title {
  display: flex;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
}
.q__index {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--xinyu-accent);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.q__options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-top: 12px;
}
.q__option {
  padding: 9px 6px;
  border: 1px solid var(--van-border-color);
  border-radius: 10px;
  color: var(--van-text-color-2);
  font-size: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}
.q__option--on {
  border-color: var(--xinyu-accent);
  background: rgba(92, 124, 250, 0.12);
  color: var(--xinyu-accent);
  font-weight: 600;
}
.submit {
  margin: 18px 0 28px;
}
.submit__hint {
  margin: 10px 0 0;
  color: var(--van-text-color-3);
  font-size: 11px;
  line-height: 1.6;
  text-align: center;
}
</style>
