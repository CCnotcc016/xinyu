<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="写日记" left-arrow @click-left="router.back()" />

    <van-field
      v-model="form.content"
      type="textarea"
      rows="8"
      autosize
      maxlength="5000"
      show-word-limit
      placeholder="今天发生了什么？此刻的心情如何？"
    />

    <div class="block">
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

    <div class="block">
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

    <van-popup v-model:show="weatherShow" position="bottom">
      <van-picker
        :columns="weatherOptions"
        @confirm="(v) => { form.weather = v.selectedValues[0]; weatherShow = false }"
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
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { createDiary } from '../api/diary'
import CrisisDialog from '../components/CrisisDialog.vue'

const router = useRouter()
const loading = ref(false)
const weatherShow = ref(false)
const sceneShow = ref(false)
const isPublic = ref(false)
const showCrisis = ref(false)
const savedId = ref(null)

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

async function onSubmit() {
  if (!form.value.content.trim()) {
    showToast('请先写下内容')
    return
  }
  loading.value = true
  try {
    const diary = await createDiary({
      ...form.value,
      privacy: isPublic.value ? 'PUBLIC' : 'PRIVATE'
    })
    if (diary.crisisSupport) {
      // 内容里有危机表达：先给关怀提示，用户关掉弹窗再进详情页
      savedId.value = diary.id
      showCrisis.value = true
    } else {
      showToast('保存成功')
      router.replace(`/diaries/${diary.id}`)
    }
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
.block {
  margin: 16px 16px 0;
}
.block-title {
  font-size: 14px;
  color: var(--van-text-color-2);
  margin-bottom: 8px;
}
.submit {
  margin: 24px 16px 0;
}
</style>
