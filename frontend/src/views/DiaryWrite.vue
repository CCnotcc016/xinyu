<template>
  <div class="page">
    <van-nav-bar title="写日记" left-arrow @click-left="router.back()" />

    <van-field
      v-model="form.content"
      class="composer xinyu-glass"
      type="textarea"
      rows="8"
      autosize
      maxlength="5000"
      show-word-limit
      placeholder="今天发生了什么？此刻的心情如何？"
    />

    <div class="block xinyu-glass">
      <div class="block-title">情绪标签（可多选）</div>
      <van-space wrap>
        <van-tag
          v-for="t in emotionOptions"
          :key="t"
          :type="form.emotionTags.includes(t) ? 'primary' : 'default'"
          size="large"
          @click="toggleTag(t)"
        >
          {{ t }}
        </van-tag>
      </van-space>
    </div>

    <div class="block xinyu-glass">
      <van-field v-model="form.weather" is-link readonly label="天气" placeholder="选择天气" @click="weatherShow = true" />
      <van-field v-model="form.scene" is-link readonly label="场景" placeholder="选择场景" @click="sceneShow = true" />
      <van-field label="公开">
        <template #input>
          <van-switch v-model="isPublic" size="20" />
        </template>
      </van-field>
    </div>

    <div class="submit">
      <van-button type="primary" round block :loading="loading" @click="onSubmit">保存日记</van-button>
    </div>
    <div v-if="draftSavedAt" class="draft">
      <span>草稿已自动保存 · {{ draftSavedAt }}</span>
      <span class="draft__clear" @click="discardDraft">清空草稿</span>
    </div>

    <van-popup v-model:show="weatherShow" position="bottom">
      <van-picker
        :columns="weatherOptions"
        @confirm="(v) => { form.weather = v.selectedValues[0]; weatherAuto = false; weatherShow = false }"
        @cancel="weatherShow = false"
      />
    </van-popup>
    <van-popup v-model:show="sceneShow" position="bottom">
      <van-picker
        :columns="sceneOptions"
        @confirm="(v) => { form.scene = v.selectedValues[0]; sceneShow = false }"
        @cancel="sceneShow = false"
      />
    </van-popup>

    <CrisisDialog v-model:show="showCrisis" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { createDiarySmart } from '../offline/diary'
import { useUserStore } from '../stores/user'
import { useOfflineStore } from '../stores/offline'
import { useWeather, weatherOption } from '../composables/useWeather'
import CrisisDialog from '../components/CrisisDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const offlineStore = useOfflineStore()
const loading = ref(false)
const weatherShow = ref(false)
const sceneShow = ref(false)
const isPublic = ref(false)
const showCrisis = ref(false)
const savedId = ref(null)
const draftSavedAt = ref('')
// 由「今日情绪天气」自动填入的天气，不算用户输入，因此不写进草稿（下次进来重新取当天天气）
const weatherAuto = ref(false)
// 草稿按账号分开存：同一台浏览器换账号登录时不该看到别人的草稿
const draftKey = `xinyu_diary_draft_${userStore.user?.id ?? 'anon'}`

const { consent, status, weather, load } = useWeather()

const emotionOptions = ['开心', '平静', '焦虑', '低落', '愤怒', '疲惫', '孤独']
const weatherOptions = [
  { text: '晴天', value: '晴天' },
  { text: '阴天', value: '阴天' },
  { text: '雨天', value: '雨天' },
  { text: '雪天', value: '雪天' }
]
const sceneOptions = [
  { text: '学习', value: '学习' },
  { text: '工作', value: '工作' },
  { text: '家庭', value: '家庭' },
  { text: '感情', value: '感情' },
  { text: '社交', value: '社交' }
]

const form = ref({
  content: '',
  emotionTags: [],
  weather: '',
  scene: '',
  privacy: 'PRIVATE'
})

function toggleTag(t) {
  const i = form.value.emotionTags.indexOf(t)
  if (i >= 0) {
    form.value.emotionTags.splice(i, 1)
  } else {
    form.value.emotionTags.push(t)
  }
}

/** 用户什么都没填时不算草稿，避免下次进来被恢复出一个空白页 */
function isBlank(d) {
  return !(d.content || '').trim() && !(d.emotionTags || []).length
    && !d.weather && !d.scene && !d.isPublic
}

function clearDraft() {
  localStorage.removeItem(draftKey)
  draftSavedAt.value = ''
}

function saveDraft() {
  // 自动填入的天气不落盘：它是「今天的天气」，不是用户填的内容
  const draft = {
    ...form.value,
    weather: weatherAuto.value ? '' : form.value.weather,
    isPublic: isPublic.value
  }
  if (isBlank(draft)) {
    clearDraft()
    return
  }
  const at = new Date().toTimeString().slice(0, 5)
  try {
    localStorage.setItem(draftKey, JSON.stringify({ ...draft, at }))
  } catch {
    // 无痕模式下 localStorage 可能写不进去，草稿功能静默降级
    return
  }
  draftSavedAt.value = at
}

function resetForm() {
  form.value = { content: '', emotionTags: [], weather: '', scene: '', privacy: 'PRIVATE' }
  isPublic.value = false
  weatherAuto.value = false
}

async function discardDraft() {
  try {
    await showConfirmDialog({ title: '提示', message: '确定清空当前草稿吗？清空后无法恢复。' })
  } catch {
    return
  }
  resetForm()
  clearDraft()
  showToast('草稿已清空')
}

let draftTimer = null
watch(form, () => {
  // 输入防抖：每敲一个字就写一次 localStorage 太浪费
  clearTimeout(draftTimer)
  draftTimer = setTimeout(saveDraft, 500)
}, { deep: true })
watch(isPublic, saveDraft)

onMounted(async () => {
  let draft = null
  try {
    draft = JSON.parse(localStorage.getItem(draftKey) || 'null')
  } catch {
    draft = null
  }
  if (draft && !isBlank(draft)) {
    form.value = {
      content: draft.content || '',
      emotionTags: Array.isArray(draft.emotionTags) ? draft.emotionTags : [],
      weather: draft.weather || '',
      scene: draft.scene || '',
      privacy: draft.isPublic ? 'PUBLIC' : 'PRIVATE'
    }
    isPublic.value = !!draft.isPublic
    draftSavedAt.value = draft.at || ''
    showToast('已恢复上次未保存的草稿')
  }
  await applyWeather()
})

/** 授权过天气的话，顺手把今天这份填进「天气」，用户可以随时改成别的 */
async function applyWeather() {
  if (form.value.weather || !consent.value) return
  if (status.value !== 'ready') await load()
  if (status.value === 'ready' && weather.value) {
    form.value.weather = weatherOption(weather.value.code)
    weatherAuto.value = true
  }
}

// 离开页面前再补存一次：防抖还没到点就点了返回时，靠这一次兜住
onBeforeUnmount(saveDraft)

async function onSubmit() {
  if (!form.value.content.trim()) {
    showToast('请先写下内容')
    return
  }
  loading.value = true
  try {
    const { diary, offline } = await createDiarySmart(
      {
        ...form.value,
        privacy: isPublic.value ? 'PUBLIC' : 'PRIVATE'
      },
      userStore.user?.id
    )
    // 存进去了草稿就没用了；顺手清空表单，否则离开页面时 onBeforeUnmount 又会把草稿写回来
    resetForm()
    clearDraft()
    if (offline) {
      // 本地已经存好了，告诉用户「写下来了」比说「失败了」重要
      offlineStore.refreshPending()
      showToast('已离线保存，联网后自动同步')
    }
    if (diary.crisisSupport) {
      // 内容里有危机表达：先给关怀提示，用户关掉弹窗再进详情页
      savedId.value = diary.id
      showCrisis.value = true
    } else {
      if (!offline) showToast('保存成功')
      router.replace(`/diaries/${diary.id}`)
    }
  } catch (error) {
    // 业务报错（内容校验不通过等）已由 request.js 弹过提示；
    // 剩下的基本是本机存储写不进去（空间满 / 无痕模式），说清楚比静默失败好
    if (!error?.handled) showToast(error?.message || '保存失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

watch(showCrisis, (v) => {
  if (!v && savedId.value) {
    router.replace(`/diaries/${savedId.value}`)
  }
})
</script>

<style scoped>
/* 玻璃容器 + Vant 单元格自带的 16px 内边距，两侧各留 4px 就好，
 * 否则表单会比页面里其它卡片缩进一大截 */
.composer,
.block {
  border-radius: 18px;
  padding: 6px 4px 2px;
}
.block {
  margin: 16px 0 0;
}
.block-title {
  font-size: 14px;
  color: var(--van-text-color-2);
  margin-bottom: 8px;
  padding-left: 12px;
}
.submit {
  margin: 24px 0 0;
}
.draft {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 4px 24px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.draft__clear {
  color: var(--xinyu-accent);
}
</style>
