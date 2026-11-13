<template>
  <div class="page">
    <van-nav-bar title="主页" left-arrow @click-left="router.back()" />

    <div v-if="profile" class="hero xinyu-glass">
      <van-image round width="64" height="64" :src="mediaUrl(profile.avatar) || defaultAvatar" />
      <div class="hero__main">
        <div class="hero__nick">{{ profile.nickname }}</div>
        <div class="hero__meta">加入于 {{ joinLabel(profile.joinedAt) }}</div>
      </div>
      <div class="hero__stats">
        <div class="hero__stat">
          <b>{{ profile.postCount ?? 0 }}</b>
          <span>树洞</span>
        </div>
        <div class="hero__stat">
          <b>{{ profile.likeReceived ?? 0 }}</b>
          <span>收到的赞</span>
        </div>
        <!-- 日记数量是隐私，只有看自己主页时才由后端返回 -->
        <div v-if="profile.diaryCount != null" class="hero__stat">
          <b>{{ profile.diaryCount }}</b>
          <span>日记</span>
        </div>
        <div v-if="profile.streak != null" class="hero__stat">
          <b>{{ profile.streak }}</b>
          <span>连续天</span>
        </div>
      </div>

      <div class="hero__ops">
        <van-button v-if="profile.relation === 'STRANGER'" round size="small" type="primary" @click="showApply = true">
          加为好友
        </van-button>
        <van-button v-else-if="profile.relation === 'REQUESTING'" round size="small" plain disabled>
          已发送申请
        </van-button>
        <van-button v-else-if="profile.relation === 'INCOMING'" round size="small" type="primary" @click="acceptRequest">
          同意好友申请
        </van-button>
        <van-button v-else-if="profile.relation === 'FRIEND'" round size="small" type="primary" @click="router.push(`/chat/${profile.id}`)">
          发消息
        </van-button>
        <van-button v-else round size="small" plain @click="router.push('/settings')">编辑资料</van-button>
        <van-button v-if="profile.relation === 'FRIEND'" round size="small" plain @click="onRemoveFriend">
          删除好友
        </van-button>
      </div>
    </div>

    <div class="section-title">
      TA 的树洞
      <span class="section-title__hint">（匿名帖不会出现在这里）</span>
    </div>
    <van-empty v-if="!loading && !posts.length" description="还没有公开的树洞帖子" />
    <van-list v-else v-model:loading="loading" :finished="true" finished-text="">
      <div v-for="p in posts" :key="p.id" class="post xinyu-glass" @click="router.push(`/treehole/${p.id}`)">
        <p class="post__content">{{ p.content }}</p>
        <div class="post__foot">
          <span>{{ dateLabel(p.createdAt) }}</span>
          <span>❤️ {{ p.likeCount }} · 💬 {{ p.commentCount }}</span>
        </div>
      </div>
    </van-list>

    <van-dialog v-model:show="showApply" title="好友申请" show-cancel-button @confirm="sendApply">
      <div class="apply">
        <van-field v-model="applyMessage" type="textarea" rows="3" maxlength="100" show-word-limit placeholder="说一句话（可以留空）" />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getUserProfile, getUserPosts } from '../api/user'
import { sendFriendRequest, handleFriendRequest, removeFriend } from '../api/friend'
import { useChatStore } from '../stores/chat'
import { mediaUrl } from '../utils/backend'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userId = Number(route.params.id)
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

const profile = ref(null)
const posts = ref([])
const loading = ref(true)
const showApply = ref(false)
const applyMessage = ref('')

function joinLabel(dt) {
  if (!dt) return '未知时间'
  const [y, m] = dt.slice(0, 10).split('-')
  return `${y} 年 ${Number(m)} 月`
}

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    const [p, page] = await Promise.all([
      getUserProfile(userId),
      getUserPosts(userId, { page: 1, size: 10 })
    ])
    profile.value = p
    posts.value = page.records
  } finally {
    loading.value = false
  }
}

async function sendApply() {
  try {
    await sendFriendRequest(userId, applyMessage.value.trim())
    showToast('申请已发送')
    applyMessage.value = ''
    load()
  } catch (e) {
    // 对方恰好也申请了我：后端会直接互相加为好友，这里重新拉一次状态就好
    load()
  }
}

async function acceptRequest() {
  await handleFriendRequest(profile.value.pendingRequestId, 'ACCEPT')
  showToast('你们已经是好友了')
  chatStore.refresh()
  load()
}

async function onRemoveFriend() {
  try {
    await showConfirmDialog({
      title: '删除好友',
      message: '删除后你们还能看到历史聊天记录，但不能再发新消息。'
    })
  } catch {
    return
  }
  await removeFriend(userId)
  showToast('已删除')
  chatStore.refresh()
  load()
}

onMounted(load)
</script>

<style scoped>
.hero {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border-radius: 16px;
}

.hero__main {
  flex: 1;
  min-width: 0;
}

.hero__nick {
  font-size: 17px;
  font-weight: 600;
}

.hero__meta {
  font-size: 12px;
  opacity: 0.6;
}

.hero__stats {
  display: flex;
  gap: 16px;
  width: 100%;
}

.hero__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 12px;
  opacity: 0.85;
}

.hero__stat b {
  font-size: 16px;
}

.hero__ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.section-title {
  margin: 18px 0 10px;
  font-size: 15px;
  font-weight: 600;
}

.section-title__hint {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.55;
}

.post {
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 14px;
  cursor: pointer;
}

.post__content {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}

.post__foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.6;
}

.apply {
  padding: 12px 16px 18px;
}
</style>
