<template>
  <div>
    <van-cell title="查看账号" icon="friends-o" :value="ownerLabel" is-link @click="openPicker" />

    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <!-- 列表不再直接铺正文：私密日记的正文属于用户，后台默认只看元数据 -->
      <van-cell
        v-for="d in diaries"
        :key="d.id"
        :title="`${emotionText(d)} · ${d.privacy === 'PUBLIC' ? '公开' : '私密'}`"
        :label="`${d.nickname}（@${d.username}）· ${formatTime(d.createdAt)}${d.crisisSupport ? ' · ⚠️ 命中危机词' : ''}`"
      >
        <template #right-icon>
          <div class="ops">
            <van-button size="small" plain type="primary" @click="openReveal(d)">查看原文</van-button>
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

    <!-- 查看正文必须先写理由：后端会记审计日志（谁、何时、看了哪条、为什么） -->
    <van-popup v-model:show="showReveal" round position="bottom" class="reveal">
      <div class="reveal__title">查看日记原文</div>
      <p class="reveal__tip">
        日记正文属于用户隐私。仅在危机跟进、举报核实等确有必要时查看，理由会被记录审计日志。
      </p>
      <van-field
        v-model="revealReason"
        rows="2"
        autosize
        type="textarea"
        maxlength="120"
        show-word-limit
        placeholder="请填写查看理由（至少 5 个字），例如：危机预警跟进"
      />
      <div class="reveal__ops">
        <van-button round block :loading="revealing" @click="confirmReveal">确认查看</van-button>
      </div>
    </van-popup>

    <!-- 日记详情：关闭时销毁内容，否则弹窗会留着上一篇的滚动位置，
         命中危机词的提示条会被滚出屏幕，看起来像「提示没显示」 -->
    <van-popup
      v-model:show="showDetail"
      round
      position="bottom"
      class="detail"
      destroy-on-close
    >
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
import { listDiaries, revealDiary, deleteDiary, listUsers } from '../../api/admin'

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
// 查看正文的申请弹窗
const showReveal = ref(false)
const revealTarget = ref(null)
const revealReason = ref('')
const revealing = ref(false)

const users = ref([])
const keyword = ref('')
const userPage = ref(1)
const userLoading = ref(false)
const userFinished = ref(false)

const ownerLabel = computed(() =>
  owner.value ? `${owner.value.nickname || owner.value.username}（@${owner.value.username}）` : '全部用户'
)

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

function openReveal(diary) {
  revealTarget.value = diary
  revealReason.value = ''
  showReveal.value = true
}

async function confirmReveal() {
  if (!revealTarget.value) return
  const reason = revealReason.value.trim()
  if (reason.length < 5) {
    showToast('请填写查看理由（至少 5 个字）')
    return
  }
  revealing.value = true
  try {
    current.value = await revealDiary(revealTarget.value.id, reason)
    showReveal.value = false
    showDetail.value = true
  } finally {
    revealing.value = false
  }
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
/* 提示条跟随滚动吸顶：弹窗内容长的时候滚下去也还看得见 */
.detail :deep(.van-notice-bar) {
  position: sticky;
  top: 0;
  z-index: 1;
}
.reveal {
  padding: 16px 16px 24px;
}
.reveal__title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.reveal__tip {
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
  margin: 0 0 12px;
}
.reveal__ops {
  padding: 16px 4px 0;
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
