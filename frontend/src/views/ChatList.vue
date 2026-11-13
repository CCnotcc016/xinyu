<template>
  <div class="page">
    <div class="chat-head">
      <h1 class="page-title">聊天</h1>
      <div class="chat-head__actions">
        <van-button size="mini" round plain @click="router.push('/friends/requests')">
          好友申请
          <span v-if="chatStore.pendingRequests > 0" class="req-dot">{{ chatStore.pendingRequests }}</span>
        </van-button>
      </div>
    </div>

    <van-empty v-if="!loading && conversations.length === 0" description="还没有好友，去树洞里认识一个人吧">
      <van-button round type="primary" size="small" @click="router.push('/treehole')">逛逛树洞</van-button>
    </van-empty>

    <div v-else class="xinyu-cards">
      <div
        v-for="c in conversations"
        :key="c.userId"
        class="conv xinyu-glass"
        @click="openChat(c)"
      >
        <van-image round width="44" height="44" :src="mediaUrl(c.avatar) || defaultAvatar" />
        <div class="conv__main">
          <div class="conv__top">
            <span class="conv__nick">{{ c.nickname }}</span>
            <span class="conv__time">{{ timeLabel(c.lastMessageAt) }}</span>
          </div>
          <div class="conv__bottom">
            <span class="conv__msg">
              <template v-if="c.lastMessage">
                <span v-if="c.lastMessageMine" class="conv__me">我：</span>{{ c.lastMessage }}
              </template>
              <template v-else>还没有聊过，打个招呼吧</template>
            </span>
            <van-badge v-if="c.unread > 0" :content="c.unread > 99 ? '99+' : c.unread" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listConversations } from '../api/chat'
import { useChatStore } from '../stores/chat'
import { mediaUrl } from '../utils/backend'

const router = useRouter()
const chatStore = useChatStore()
const conversations = ref([])
const loading = ref(true)
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'
let timer = null

function timeLabel(dt) {
  if (!dt) return ''
  // 今天只显示时分，更早的显示日期，和常见 IM 一致
  const today = new Date().toISOString().slice(0, 10)
  const day = dt.slice(0, 10)
  return day === today ? dt.slice(11, 16) : dt.slice(5, 10).replace('-', '/')
}

async function load() {
  try {
    conversations.value = await listConversations()
  } finally {
    loading.value = false
  }
}

function openChat(c) {
  router.push(`/chat/${c.userId}`)
}

onMounted(() => {
  load()
  // 列表页开着的时候也要能看到新消息：20 秒拉一次足够，聊天室内是另外一套更快的轮询
  timer = setInterval(load, 20000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-head__actions {
  display: flex;
  gap: 8px;
}

.req-dot {
  display: inline-block;
  min-width: 16px;
  margin-left: 4px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
  color: #fff;
  background: var(--xinyu-danger, #ee0a24);
  border-radius: 8px;
}

.conv {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  cursor: pointer;
}

.conv:active {
  opacity: 0.75;
}

.conv__main {
  flex: 1;
  min-width: 0;
}

.conv__top,
.conv__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conv__nick {
  font-weight: 600;
}

.conv__time {
  font-size: 12px;
  opacity: 0.55;
}

.conv__msg {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.7;
}

.conv__me {
  opacity: 0.8;
}
</style>
