<template>
  <div class="page">
    <van-nav-bar title="好友申请" left-arrow @click-left="router.back()" />

    <van-tabs v-model:active="tab" sticky @change="load">
      <van-tab title="收到的" name="RECEIVED" />
      <van-tab title="我发出的" name="SENT" />
    </van-tabs>

    <van-empty v-if="!loading && !list.length" description="这里还什么都没有" />

    <div v-else class="xinyu-cards">
      <div v-for="r in list" :key="r.id" class="req xinyu-glass">
        <van-image round width="42" height="42" :src="mediaUrl(r.avatar) || defaultAvatar" @click="openProfile(r.userId)" />
        <div class="req__main">
          <div class="req__top">
            <span class="req__nick" @click="openProfile(r.userId)">{{ r.nickname }}</span>
            <span class="req__time">{{ dateLabel(r.createdAt) }}</span>
          </div>
          <p class="req__msg">{{ r.message || '想和你成为好友' }}</p>
          <div class="req__foot">
            <template v-if="tab === 'RECEIVED' && r.status === 'PENDING'">
              <van-button size="mini" round type="primary" @click="handle(r, 'ACCEPT')">同意</van-button>
              <van-button size="mini" round plain @click="handle(r, 'REJECT')">拒绝</van-button>
            </template>
            <span v-else class="req__status">{{ statusLabel(r) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listFriendRequests, handleFriendRequest } from '../api/friend'
import { useChatStore } from '../stores/chat'
import { mediaUrl } from '../utils/backend'

const router = useRouter()
const chatStore = useChatStore()
const tab = ref('RECEIVED')
const list = ref([])
const loading = ref(true)
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(5, 16) : ''
}

function statusLabel(r) {
  if (r.status === 'PENDING') return '等待对方确认'
  if (r.status === 'REJECTED') return '已拒绝'
  return '已成为好友'
}

async function load() {
  try {
    list.value = await listFriendRequests(tab.value)
  } finally {
    loading.value = false
  }
}

async function handle(r, action) {
  await handleFriendRequest(r.id, action)
  showToast(action === 'ACCEPT' ? '你们已经是好友了' : '已拒绝')
  // 角标要跟着变：同意之后「我的」上的红点应立刻消失
  chatStore.refresh()
  load()
}

function openProfile(userId) {
  router.push(`/u/${userId}`)
}

onMounted(load)
</script>

<style scoped>
.req {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
}

.req__main {
  flex: 1;
  min-width: 0;
}

.req__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.req__nick {
  font-weight: 600;
  cursor: pointer;
}

.req__time {
  font-size: 12px;
  opacity: 0.55;
}

.req__msg {
  margin: 4px 0 6px;
  font-size: 13px;
  opacity: 0.75;
}

.req__foot {
  display: flex;
  gap: 8px;
  align-items: center;
}

.req__status {
  font-size: 12px;
  opacity: 0.6;
}
</style>
