<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div>
    <van-cell title="查看账号" icon="friends-o" :value="ownerLabel" is-link @click="openPicker" />

    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-cell
        v-for="d in diaries"
        :key="d.id"
        :title="preview(d.content)"
        :label="`${d.nickname}（@${d.username}）· ${formatTime(d.createdAt)}`"
      >
        <template #right-icon>
          <div class="ops">
            <van-button size="small" plain type="primary" @click="openDetail(d)">查看</van-button>
            <van-button size="small" type="danger" @click="onDelete(d.id)">删除</van-button>
          </div>
        </template>
      </van-cell>
    </van-list>
    <van-empty v-if="finished && !diaries.length" description="这个账号还没有写过日记" />

    <!-- 账号选择 -->
    <van-popup v-model:show="showPicker" round position="bottom" class="picker">
      <div class="picker__title">选择要查看的账号</div>
      <van-search v-model="keyword" placeholder="搜索用户名 / 昵称" />
      <van-cell title="全部用户" is-link @click="chooseUser(null)" />
      <van-list
        v-model:loading="userLoading"
        :finished="userFinished"
        finished-text="没有更多用户了"
        class="picker__list"
        @load="loadMoreUsers"
      >
        <van-cell
          v-for="u in users"
          :key="u.id"
          :title="u.nickname || u.username"
          :label="`@${u.username}${u.role === 'ADMIN' ? ' · 管理员' : ''}`"
          is-link
          @click="chooseUser(u)"
        />
      </van-list>
    </van-popup>

    <!-- 日记详情 -->
    <van-popup v-model:show="showDetail" round position="bottom" class="detail">
      <template v-if="current">
        <div class="detail__title">日记详情</div>
        <van-notice-bar
          v-if="current.crisisSupport"
          left-icon="warning-o"
          wrapable
          :scrollable="false"
          text="这条日记命中了危机关键词，可以到「预警」标签页查看处理进度"
        />
        <van-cell-group inset>
          <van-cell title="作者" :value="`${current.nickname}（@${current.username}）`" />
          <van-cell title="情绪" :value="emotionText(current)" />
          <van-cell title="天气 / 场景" :value="`${current.weather || '未填'} / ${current.scene || '未填'}`" />
          <van-cell title="可见性" :value="current.privacy === 'PUBLIC' ? '公开' : '私密'" />
          <van-cell title="AI 状态" :value="current.aiStatus || '未生成'" />
          <van-cell title="记录时间" :value="formatTime(current.createdAt)" />
        </van-cell-group>
        <div class="detail__block">
          <h4>内容</h4>
          <p>{{ current.content }}</p>
        </div>
        <div class="detail__block">
          <h4>AI 回复</h4>
          <p>{{ current.aiReply || '（还没有 AI 回复）' }}</p>
        </div>
        <div class="detail__block">
          <van-button round block @click="showDetail = false">关闭</van-button>
        </div>
      </template>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { listDiaries, deleteDiary, listUsers } from '../../api/admin'

const diaries = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

// 当前筛选的账号，null 表示看全部
const owner = ref(null)
const showPicker = ref(false)
const showDetail = ref(false)
const current = ref(null)

const users = ref([])
const keyword = ref('')
const userPage = ref(1)
const userLoading = ref(false)
const userFinished = ref(false)

const ownerLabel = computed(() =>
  owner.value ? `${owner.value.nickname || owner.value.username}（@${owner.value.username}）` : '全部用户'
)

function preview(text) {
  return text && text.length > 40 ? text.slice(0, 40) + '…' : text
}

function formatTime(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

function emotionText(d) {
  if (!d.emotionScore && !d.emotionLabel) {
    return '未分析'
  }
  return `${d.emotionLabel || '未标注'} ${d.emotionScore || '-'} 分`
}

/** 换账号后要从第一页重来：清空列表，并把 van-list 从 finished 态放回可加载态 */
function resetDiaries() {
  diaries.value = []
  page.value = 1
  finished.value = false
  error.value = false
  loading.value = true
  onLoad()
}

async function onLoad() {
  try {
    const data = await listDiaries({ page: page.value, size: 10, userId: owner.value?.id })
    diaries.value.push(...data.records)
    if (data.records.length < 10 || diaries.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    // 进入 error 态：van-list 显示「点击重新加载」并暂停自动加载，避免失败时无限刷接口
    error.value = true
  } finally {
    // 必须在 finally 里复位：请求失败时若停在 true，van-list 会永久卡死无法重试
    loading.value = false
  }
}

function openPicker() {
  showPicker.value = true
  if (!users.value.length) {
    resetUsers()
  }
}

function resetUsers() {
  users.value = []
  userPage.value = 1
  userFinished.value = false
  userLoading.value = true
  loadMoreUsers()
}

let keywordTimer = null
watch(keyword, () => {
  // 输入时防抖，避免每敲一个字就打一次接口
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(resetUsers, 300)
})

async function loadMoreUsers() {
  try {
    const data = await listUsers({ page: userPage.value, size: 20, keyword: keyword.value || undefined })
    users.value.push(...data.records)
    if (data.records.length < 20 || users.value.length >= data.total) {
      userFinished.value = true
    } else {
      userPage.value += 1
    }
  } catch {
    userFinished.value = true
  } finally {
    userLoading.value = false
  }
}

function chooseUser(user) {
  owner.value = user
  showPicker.value = false
  resetDiaries()
}

function openDetail(diary) {
  current.value = diary
  showDetail.value = true
}

async function onDelete(id) {
  try {
    await showConfirmDialog({ title: '提示', message: '确定删除该日记？' })
  } catch {
    return
  }
  await deleteDiary(id)
  diaries.value = diaries.value.filter((d) => d.id !== id)
  showToast('已删除')
}
</script>

<style scoped>
.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
.picker {
  padding-bottom: 12px;
}
.picker__title,
.detail__title {
  font-size: 16px;
  font-weight: 600;
  padding: 16px 16px 8px;
}
.picker__list {
  max-height: 45vh;
  overflow-y: auto;
}
.detail {
  padding-bottom: 20px;
  max-height: 86vh;
  overflow-y: auto;
}
.detail__block {
  padding: 4px 16px 8px;
}
.detail__block h4 {
  font-size: 14px;
  margin: 12px 0 6px;
  color: var(--van-text-color-2);
}
.detail__block p {
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
