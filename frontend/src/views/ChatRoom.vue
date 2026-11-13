<template>
  <div class="room">
    <van-nav-bar :title="peerName" left-arrow @click-left="router.back()">
      <template #right>
        <div class="room__nav-actions">
          <van-icon name="user-o" @click="router.push(`/u/${peerId}`)" />
          <van-icon name="ellipsis" @click="showActions = true" />
        </div>
      </template>
    </van-nav-bar>

    <div ref="scroller" class="room__list" @scroll="onScroll">
      <div v-if="hasMore && !loadingMore" class="room__more" @click="loadMore">查看更早的消息</div>
      <div v-if="loadingMore" class="room__more">加载中…</div>

      <template v-for="m in messages" :key="m.id">
        <div v-if="m.systemHint" class="room__hint">{{ m.content }}</div>
        <div v-else class="bubble" :class="{ 'bubble--mine': m.mine }">
          <van-image v-if="!m.mine" round width="32" height="32" :src="mediaUrl(m.avatar) || mediaUrl(peerAvatar) || defaultAvatar" />
          <div class="bubble__body">
            <p class="bubble__text">{{ m.content }}</p>
            <span class="bubble__time">{{ timeLabel(m.createdAt) }}</span>
          </div>
        </div>
      </template>
    </div>

    <div class="room__input">
      <van-field
        v-model="text"
        rows="1"
        autosize
        type="textarea"
        :placeholder="notFriend ? '你们已经不是好友，只能看历史消息' : '想说点什么…'"
        :disabled="notFriend"
        @keydown="onKeydown"
      />
      <van-button size="small" type="primary" :loading="sending" :disabled="notFriend" @click="onSend">
        发送
      </van-button>
    </div>

    <!-- 长按/点「更多」都能到举报：私聊内容管理员不会看，只处理骚扰这类举报 -->
    <van-action-sheet
      v-model:show="showActions"
      :actions="peerActions"
      cancel-text="取消"
      close-on-click-action
      @select="onSelectAction"
    />

    <van-popup v-model:show="showReport" position="bottom" round>
      <div class="report-box">
        <h3>举报 {{ peerName }}</h3>
        <p class="report-box__tip">举报会交给管理员审核，TA 不会收到是谁举报的。</p>
        <div class="report-box__reasons">
          <span
            v-for="r in reportReasons"
            :key="r"
            class="reason"
            :class="{ 'reason--active': reportReason === r }"
            @click="reportReason = r"
          >{{ r }}</span>
        </div>
        <van-field
          v-model="reportDetail"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="补充说明（选填），比如发生了什么"
        />
        <div class="report-box__btns">
          <van-button round block type="danger" :loading="reporting" @click="onReport">提交举报</van-button>
        </div>
      </div>
    </van-popup>

    <CrisisDialog v-model:show="showCrisis" private-chat />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listMessages, sendMessage, markChatRead } from '../api/chat'
import { getUserProfile } from '../api/user'
import { createReport } from '../api/report'
import { useChatStore } from '../stores/chat'
import CrisisDialog from '../components/CrisisDialog.vue'
import { mediaUrl } from '../utils/backend'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const peerId = Number(route.params.id)
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

const messages = ref([])
const text = ref('')
const sending = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const showCrisis = ref(false)
const notFriend = ref(false)
const peerName = ref('聊天')
const peerAvatar = ref(null)
const scroller = ref(null)
let timer = null

const showActions = ref(false)
const peerActions = [
  { name: '查看 TA 的主页', value: 'profile' },
  { name: '举报 TA', value: 'report', color: '#ee0a24' }
]
const showReport = ref(false)
const reportReasons = ['骚扰辱骂', '色情低俗', '诈骗广告', '违法违规', '其他']
const reportReason = ref('')
const reportDetail = ref('')
const reporting = ref(false)

const oldestId = computed(() => (messages.value.length ? messages.value[0].id : null))
const lastId = computed(() => (messages.value.length ? messages.value[messages.value.length - 1].id : null))

function timeLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(5, 16) : ''
}

function merge(rows) {
  const known = new Set(messages.value.map((m) => m.id))
  const fresh = rows.filter((m) => !known.has(m.id))
  if (!fresh.length) return false
  messages.value = [...messages.value, ...fresh].sort((a, b) => a.id - b.id)
  return true
}

function scrollToBottom() {
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

async function loadLatest({ smooth = true } = {}) {
  const page = await listMessages(peerId, null, 30)
  const grew = merge(page.records)
  // total 是这一对的全部消息数，比已加载的多就说明上面还有
  hasMore.value = page.total > messages.value.length
  if (grew && smooth) scrollToBottom()
}

async function loadMore() {
  loadingMore.value = true
  const el = scroller.value
  const keepHeight = el ? el.scrollHeight - el.scrollTop : 0
  try {
    const page = await listMessages(peerId, oldestId.value, 30)
    merge(page.records)
    hasMore.value = page.total > messages.value.length
    // 往上面插了内容，滚动位置要补回去，否则视口会「跳」到老消息里
    nextTick(() => {
      if (el) el.scrollTop = el.scrollHeight - keepHeight
    })
  } finally {
    loadingMore.value = false
  }
}

/** 滚到顶部时自动翻上一页，不用非得点按钮 */
function onScroll() {
  const el = scroller.value
  if (!el || el.scrollTop > 4 || loadingMore.value || !hasMore.value) return
  loadMore()
}

async function onSend() {
  const content = text.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    const msg = await sendMessage(peerId, content)
    text.value = ''
    merge([msg])
    scrollToBottom()
    if (msg.crisis) showCrisis.value = true
  } catch (e) {
    // 3011 = 已经不是好友了，直接把输入禁用掉，别让用户反复点
    if (e?.code === 3011) notFriend.value = true
  } finally {
    sending.value = false
  }
}

function onKeydown(e) {
  // 桌面端回车直接发，手机上回车换行
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    onSend()
  }
}

function onSelectAction(action) {
  if (action.value === 'profile') {
    router.push(`/u/${peerId}`)
    return
  }
  reportReason.value = ''
  reportDetail.value = ''
  showReport.value = true
}

async function onReport() {
  if (reporting.value) return
  const detail = reportDetail.value.trim()
  if (!reportReason.value && !detail) {
    showToast('请选择或填写举报原因')
    return
  }
  reporting.value = true
  try {
    const reason = [reportReason.value, detail].filter(Boolean).join('：').slice(0, 500)
    await createReport({ targetType: 'USER', targetId: peerId, reason })
    showReport.value = false
    showToast('举报已提交')
  } finally {
    reporting.value = false
  }
}

async function readAll() {
  const res = await markChatRead(peerId)
  chatStore.setUnread(res?.count || 0)
}

onMounted(async () => {
  try {
    const profile = await getUserProfile(peerId)
    peerName.value = profile.nickname || '聊天'
    peerAvatar.value = profile.avatar
    notFriend.value = profile.relation !== 'FRIEND'
  } catch {
    // 主页拿不到不影响聊天，昵称就先留默认值
  }
  await loadLatest({ smooth: false })
  scrollToBottom()
  await readAll()
  // 私聊没有推送，靠轮询：4 秒一次，同时把对方新发来的消息标成已读
  timer = setInterval(async () => {
    await loadLatest()
    await readAll()
  }, 4000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.room {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: var(--xinyu-shell);
  height: 100vh;
  margin: 0 auto;
  /* 手机上 100vh 会把地址栏算进去，用 dvh 兜一下，免得输入框被顶出屏幕 */
  height: 100dvh;
}

.room__nav-actions {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 18px;
}

.room__list {
  flex: 1;
  padding: 12px 16px;
  overflow-y: auto;
}

.room__more,
.room__hint {
  padding: 6px 0;
  font-size: 12px;
  text-align: center;
  opacity: 0.55;
}

.bubble {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.bubble--mine {
  flex-direction: row-reverse;
}

.bubble__body {
  max-width: 72%;
}

.bubble__text {
  padding: 8px 12px;
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  background: var(--xinyu-glass-bg);
  border: 1px solid var(--xinyu-glass-border);
  border-radius: 14px;
}

.bubble--mine .bubble__text {
  color: #fff;
  background: var(--xinyu-accent, #3c8cff);
  border-color: transparent;
}

.bubble__time {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.5;
}

.bubble--mine .bubble__time {
  text-align: right;
}

.room__input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 8px 16px calc(8px + env(safe-area-inset-bottom));
  background: var(--xinyu-glass-bg);
  border-top: 1px solid var(--xinyu-glass-border);
}

.report-box {
  padding: 20px 16px calc(24px + env(safe-area-inset-bottom));
}

.report-box h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.report-box__tip {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.5;
  opacity: 0.6;
}

.report-box__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.reason {
  padding: 6px 14px;
  font-size: 13px;
  color: var(--van-text-color);
  background: var(--xinyu-glass-bg);
  border: 1px solid var(--xinyu-glass-border);
  border-radius: 999px;
}

.reason--active {
  color: #fff;
  background: var(--van-danger-color, #ee0a24);
  border-color: transparent;
}

.report-box__btns {
  margin-top: 16px;
}
</style>
