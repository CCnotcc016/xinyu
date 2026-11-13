<template>
  <div class="page support-page">
    <van-nav-bar :title="title" left-arrow @click-left="router.back()">
      <template #right>
        <span v-if="canClose" class="close-btn" @click="onClose">结单</span>
        <span v-else-if="isClosed" class="close-btn close-btn--done">已结单</span>
      </template>
    </van-nav-bar>

    <div v-if="!loading && assigneeName && !isMineAssignee" class="tip xinyu-glass">
      该会话已由「{{ assigneeName }}」接手，回复会同时出现在用户那边。
    </div>

    <div v-if="isClosed" class="tip xinyu-glass tip--closed">
      本次会话已结单{{ closedByName ? `（${closedByName}）` : '' }}。
      用户再次咨询时会重新分配接手人，并且看不到结单之前的记录。
    </div>

    <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="3" />

    <div v-else class="chat">
      <van-empty v-if="denied" description="该会话已由其他客服接手，无法查看" />
      <van-empty v-else-if="!messages.length" description="还没有消息" />

      <div v-for="m in messages" :key="m.id">
        <div v-if="m.senderRole === 'SYSTEM'" class="sys-line">{{ m.content }}</div>
        <div v-else class="bubble-row" :class="{ 'bubble-row--mine': isMine(m) }">
          <div class="bubble" :class="isMine(m) ? 'bubble--mine' : 'bubble--other'">
            <div class="bubble__who">{{ isMine(m) ? '我' : m.senderName }}（{{ roleLabel(m.senderRole) }}）</div>
            <div class="bubble__text">{{ m.content }}</div>
            <div class="bubble__time">{{ shortTime(m.createdAt) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!denied" class="input-bar">
      <div v-if="isClosed" class="input-closed">会话已结单，等用户再次咨询后即可回复</div>
      <div v-else class="input-row">
        <van-field
          ref="field"
          v-model="text"
          type="textarea"
          autosize
          rows="1"
          maxlength="2000"
          placeholder="回复用户…"
          @keydown="onKeydown"
        />
        <van-button size="small" type="primary" :loading="sending" @click="onSend">发送</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '../../stores/user'
import { closeConversation, getConversation, staffSendMessage } from '../../api/support'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const messages = ref([])
// 会话归属的客服（没人接手时为空）
const assigneeName = ref('')
const assigneeId = ref(null)
// 发起咨询的用户
const userName = ref('')
// 会话状态：OPEN 进行中 / CLOSED 已结单
const status = ref('OPEN')
const closedByName = ref('')
const canClose = ref(false)
const text = ref('')
const loading = ref(true)
const sending = ref(false)
// 会话被别人接手时接口返回 403，这里记下来换成说得清的占位
const denied = ref(false)
const field = ref(null)

const title = computed(() => userName.value || '用户咨询')
// 自己接手的会话不用提示「已由某人接手」
const isMineAssignee = computed(() => !!assigneeId.value && assigneeId.value === userStore.user?.id)
const isClosed = computed(() => status.value === 'CLOSED')

// 自己发的消息用右气泡：管理员也算「我们这边」的回复
function isMine(m) {
  return m.senderId === userStore.user?.id
}

function roleLabel(role) {
  return { ADMIN: '管理员', STAFF: '客服' }[role] || '用户'
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : ''
}

function applyThread(thread) {
  messages.value = thread?.messages ?? []
  userName.value = thread?.nickname || thread?.username || `用户#${thread?.userId ?? ''}`
  assigneeName.value = thread?.assigneeName || ''
  assigneeId.value = thread?.assigneeId ?? null
  status.value = thread?.status || 'OPEN'
  closedByName.value = thread?.closedByName || ''
  canClose.value = thread?.canClose === true
}

async function load() {
  applyThread(await getConversation(route.params.id))
}

async function onClose() {
  try {
    await showConfirmDialog({
      title: '结单',
      message: '确认本次咨询已经处理完？结单后用户再发消息会重新分配给客服，接手人看不到之前的记录。'
    })
  } catch {
    return
  }
  try {
    applyThread(await closeConversation(route.params.id))
    showToast('已结单')
  } catch {
    // 失败提示由 request.js 统一给出（例如别人刚结过单）
  }
}

async function onSend() {
  const content = text.value.trim()
  if (!content) {
    showToast('请输入回复内容')
    return
  }
  sending.value = true
  try {
    const m = await staffSendMessage(route.params.id, content)
    messages.value.push(m)
    text.value = ''
    // 首条回复即接手，会话归属要跟着变（管理员回复不改变归属，这里只在空的时候补上）
    if (!assigneeName.value && m.senderRole !== 'USER') {
      assigneeName.value = m.senderName
      assigneeId.value = m.senderId
    }
    // 回复过就一定能结单：STAFF 此刻已是接手人，ADMIN 本来就可以
    canClose.value = !isClosed.value
    if (isDesktop) {
      field.value?.focus()
    }
  } finally {
    sending.value = false
  }
}

/*
 * 和树洞评论用同一套键盘约定：有鼠标又不是触屏才算电脑。
 * 电脑上回车发送，想换行得按住 Command（macOS）/ Ctrl（Windows）再回车。
 */
const isDesktop =
  typeof window !== 'undefined' &&
  window.matchMedia('(hover: hover) and (pointer: fine)').matches

function insertNewline(el) {
  const start = el.selectionStart ?? el.value.length
  const end = el.selectionEnd ?? start
  text.value = el.value.slice(0, start) + '\n' + el.value.slice(end)
  // v-model 要等 DOM 更新完才能把光标放回去
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + 1
  })
}

function onKeydown(e) {
  // 中文输入法选词时的回车不是发送
  if (e.key !== 'Enter' || e.isComposing || e.keyCode === 229) {
    return
  }
  if (e.target?.tagName !== 'TEXTAREA' || !isDesktop) {
    return
  }
  e.preventDefault()
  if (e.metaKey || e.ctrlKey) {
    insertNewline(e.target)
    return
  }
  if (text.value.trim()) {
    onSend()
  }
}

onMounted(async () => {
  try {
    // 打开会话会把工作台未读清零
    await load()
  } catch {
    denied.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* 输入框固定在底部，聊天区留出等高的内边距，最后一条消息才不会被挡住 */
.support-page {
  padding-bottom: 96px;
}
.tip {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
/* 已结单的说明比普通提示重一档，一眼能看出这轮咨询已经收尾 */
.tip--closed {
  border-left: 3px solid var(--van-primary-color);
  color: var(--van-text-color);
}
.close-btn {
  font-size: 14px;
  color: var(--van-primary-color);
}
.close-btn--done {
  color: var(--van-text-color-3);
}
/* 系统提示（结单说明）居中的一条分隔线，不当成谁发的消息 */
.sys-line {
  margin: 4px auto;
  padding: 6px 12px;
  max-width: 88%;
  border-radius: 10px;
  background: var(--van-background-2, rgba(127, 127, 127, 0.12));
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}
.input-closed {
  padding: 8px 4px;
  text-align: center;
  font-size: 13px;
  color: var(--van-text-color-2);
}
.chat {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.bubble-row {
  display: flex;
}
.bubble-row--mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  padding: 9px 12px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
}
.bubble--other {
  background: var(--van-background-2, rgba(127, 127, 127, 0.12));
  color: var(--van-text-color);
  border-top-left-radius: 4px;
}
.bubble--mine {
  background: var(--van-primary-color);
  color: #fff;
  border-top-right-radius: 4px;
}
.bubble__who {
  margin-bottom: 3px;
  font-size: 12px;
  font-weight: 600;
  opacity: 0.85;
}
.bubble__text {
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble__time {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.7;
}
.input-bar {
  position: fixed;
  bottom: 0;
  /* 宽屏时外壳居中，输入条也要跟着居中，不能贴着浏览器左右两边 */
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: var(--xinyu-shell);
  z-index: 9;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: var(--xinyu-glass-bg);
  backdrop-filter: blur(18px) saturate(180%);
  -webkit-backdrop-filter: blur(18px) saturate(180%);
  border-top: 1px solid var(--van-border-color);
}
/* 宽屏上输入条不用跟着内容列铺满，读起来太散 */
@media (min-width: 1024px) {
  .input-bar {
    max-width: 760px;
  }
}
.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.input-row :deep(.van-field) {
  padding: 6px 10px;
  border-radius: 12px;
  background: var(--van-background);
}
</style>
