<template>
  <div class="page support-page">
    <van-nav-bar title="联系管理员/客服" left-arrow @click-left="router.back()" />

    <div class="tip xinyu-glass">
      有疑问、反馈或需要帮助都可以在这里说，管理员与客服看到后会尽快回复。
      <span class="tip__warn">
        如果你现在有伤害自己的想法，请立刻联系身边信任的人；紧急情况请拨打 110 / 120。
        也可以拨打全国心理援助热线 <a href="tel:12356">12356</a>。
      </span>
    </div>

    <div v-if="isClosed" class="tip xinyu-glass tip--closed">
      本次咨询已结束。还有问题的话直接再发一条消息，会重新安排客服/管理员接手。
    </div>

    <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="3" />

    <div v-else class="chat">
      <van-empty v-if="!messages.length" description="还没有消息，直接说话吧" />

      <div v-for="m in messages" :key="m.id">
        <div v-if="m.senderRole === 'SYSTEM'" class="sys-line">{{ m.content }}</div>
        <div v-else class="bubble-row" :class="{ 'bubble-row--mine': isMine(m) }">
          <div class="bubble" :class="isMine(m) ? 'bubble--mine' : 'bubble--other'">
            <div v-if="!isMine(m)" class="bubble__who">
              {{ roleLabel(m.senderRole) }} · {{ m.senderName }}
            </div>
            <div class="bubble__text">{{ m.content }}</div>
            <div class="bubble__time">{{ shortTime(m.createdAt) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-bar">
      <div class="input-row">
        <van-field
          ref="field"
          v-model="text"
          type="textarea"
          autosize
          rows="1"
          maxlength="2000"
          placeholder="说点什么…"
          @keydown="onKeydown"
        />
        <van-button size="small" type="primary" :loading="sending" @click="onSend">发送</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getThread, sendMessage } from '../api/support'

const router = useRouter()
const messages = ref([])
const text = ref('')
const loading = ref(true)
const sending = ref(false)
const field = ref(null)
// 客服可以结单：结单后这轮咨询结束，再发消息会自动开新的一轮
const status = ref('OPEN')

const isClosed = computed(() => status.value === 'CLOSED')

function roleLabel(role) {
  return { ADMIN: '管理员', STAFF: '客服' }[role] || '用户'
}

function isMine(m) {
  return m.senderRole === 'USER'
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : ''
}

async function onSend() {
  const content = text.value.trim()
  if (!content) {
    showToast('请输入内容')
    return
  }
  sending.value = true
  try {
    const m = await sendMessage(content)
    messages.value.push(m)
    text.value = ''
    // 后端在用户结单后再次发言时会自动开启新一轮
    status.value = 'OPEN'
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
    // 打开会话会把未读清零，首页的「客服回复了你」通知条也就跟着消失
    const thread = await getThread()
    messages.value = thread?.messages ?? []
    status.value = thread?.status || 'OPEN'
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
/* 已结单的说明放在最上面，用户一进页面就知道这轮咨询已经收尾 */
.tip--closed {
  border-left: 3px solid var(--van-primary-color);
  color: var(--van-text-color);
}
/* 系统提示（客服结单说明）居中的一条分隔线，不当成谁发的消息 */
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
.tip__warn {
  display: block;
  margin-top: 6px;
  color: var(--van-danger-color);
}
.tip__warn a {
  color: var(--van-danger-color);
  font-weight: 600;
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
