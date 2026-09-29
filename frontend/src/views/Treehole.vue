<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="匿名树洞" />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        finished-text="没有更多了"
        error-text="加载失败，点击重新加载"
        @load="onLoad"
      >
        <div class="xinyu-cards">
          <div v-for="p in posts" :key="p.id" class="post" @click="router.push(`/treehole/${p.id}`)">
            <div class="post-head">
              <span class="nick">{{ p.nickname }}</span>
              <span class="time">{{ dateLabel(p.createdAt) }}</span>
            </div>
            <p class="content">{{ p.content }}</p>
            <div class="post-foot">
              <span :class="{ liked: p.liked }" @click.stop="toggleLike(p)">
                {{ p.liked ? '❤️' : '🤍' }} {{ p.likeCount }}
              </span>
              <span>💬 {{ p.commentCount }}</span>
            </div>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>

    <CrisisDialog v-model:show="showCrisis" />

    <div class="fab">
      <van-button type="primary" round icon="plus" @click="showCreate = true">发帖</van-button>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round>
      <div class="create">
        <h3>向树洞说点什么</h3>
        <van-field
          v-model="form.content"
          type="textarea"
          rows="5"
          autosize
          maxlength="2000"
          show-word-limit
          placeholder="这里没有人知道你是谁…"
        />
        <van-cell title="匿名发布">
          <template #right-icon>
            <van-switch v-model="form.isAnonymous" size="20" />
          </template>
        </van-cell>
        <div class="create-btns">
          <van-button round block type="primary" :loading="posting" @click="onCreate">发布</van-button>
        </div>
      </div>
    </van-popup>

  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { listPosts, createPost, likePost, unlikePost } from '../api/treehole'
import CrisisDialog from '../components/CrisisDialog.vue'

const router = useRouter()
const posts = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const refreshing = ref(false)
const showCreate = ref(false)
const posting = ref(false)
const form = ref({ content: '', isAnonymous: true })
const showCrisis = ref(false)

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(5, 16) : ''
}

async function onLoad() {
  try {
    const data = await listPosts({ page: page.value, size: 10 })
    posts.value.push(...data.records)
    if (data.records.length < 10 || posts.value.length >= data.total) {
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

async function onRefresh() {
  page.value = 1
  const data = await listPosts({ page: 1, size: 10 })
  posts.value = data.records
  finished.value = data.records.length < 10
  error.value = false
  refreshing.value = false
}

async function toggleLike(p) {
  if (p.liked) {
    const res = await unlikePost(p.id)
    p.liked = res.liked
    p.likeCount = res.likeCount
  } else {
    const res = await likePost(p.id)
    p.liked = res.liked
    p.likeCount = res.likeCount
  }
}

async function onCreate() {
  if (!form.value.content.trim()) {
    showToast('说点什么吧')
    return
  }
  posting.value = true
  try {
    const res = await createPost(form.value)
    showCreate.value = false
    form.value.content = ''
    onRefresh()
    if (res && res.crisisSupport) {
      showCrisis.value = true
    } else {
      showToast('发布成功')
    }
  } finally {
    posting.value = false
  }
}
</script>

<style scoped>
.post {
  background: var(--van-background-2);
  border-radius: 8px;
  padding: 14px 16px;
  margin: 8px 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.post-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nick {
  font-weight: 600;
  color: var(--xinyu-accent);
}
.time {
  color: var(--van-text-color-2);
  font-size: 12px;
}
.content {
  margin: 10px 0;
  line-height: 1.6;
}
.post-foot {
  display: flex;
  gap: 20px;
  color: var(--van-text-color-2);
  font-size: 14px;
}
.liked {
  color: #ee0a24;
}
@media (min-width: 1024px) {
  /* 网格本身已经有 gap，卡片自带的外边距会叠成双倍间距 */
  .xinyu-cards .post {
    margin: 0;
  }
}
.fab {
  position: fixed;
  /* 宽屏时外壳居中，按钮要贴着外壳内侧，不能贴着浏览器右边 */
  right: max(20px, calc((100vw - var(--xinyu-shell)) / 2 + 20px));
  bottom: 80px;
  z-index: 9;
}
.create {
  padding: 20px 16px 24px;
}
.create h3 {
  margin-bottom: 12px;
}
.create-btns {
  margin-top: 16px;
}
</style>
