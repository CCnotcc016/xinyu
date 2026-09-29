<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="树洞" left-arrow @click-left="router.back()" />

    <div v-if="post" class="post">
      <div class="post-head">
        <span class="nick">{{ post.nickname }}</span>
        <span class="time">{{ dateLabel(post.createdAt) }}</span>
      </div>
      <p class="content">{{ post.content }}</p>
      <div class="post-foot">
        <span :class="{ liked: post.liked }" @click="toggleLike">
          {{ post.liked ? '❤️' : '🤍' }} {{ post.likeCount }}
        </span>
        <span>💬 {{ post.commentCount }}</span>
        <span class="report" @click="showReport = true">举报</span>
      </div>
    </div>

    <div class="section-title">评论</div>
    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多评论" error-text="加载失败，点击重新加载" @load="onLoad">
      <div v-for="c in comments" :key="c.id" class="comment">
        <div class="comment-head">
          <span class="nick">{{ c.nickname }}</span>
          <span class="comment-right">
            <span class="time">{{ dateLabel(c.createdAt) }}</span>
            <span class="reply-btn" @click="startReply(c)">回复</span>
          </span>
        </div>
        <p class="comment-content">
          <span v-if="c.replyToNickname" class="reply-to">回复 @{{ c.replyToNickname }}：</span>{{ c.content }}
        </p>
      </div>
    </van-list>

    <div class="input-bar">
      <div v-if="replyTo" class="reply-banner">
        <span class="reply-banner__text">回复 @{{ replyTo.nickname }}</span>
        <van-icon name="cross" @click="cancelReply" />
      </div>
      <div class="input-row">
        <van-field
          ref="commentField"
          v-model="commentText"
          :placeholder="replyTo ? `回复 @${replyTo.nickname}…` : '写下你的陪伴…'"
        />
        <van-button size="small" type="primary" @click="onAddComment">发送</van-button>
      </div>
    </div>

    <van-popup v-model:show="showReport" position="bottom" round>
      <div class="report-box">
        <h3>举报</h3>
        <van-field v-model="reportReason" type="textarea" rows="3" placeholder="请描述举报原因" />
        <div class="report-btns">
          <van-button round block type="danger" :loading="reporting" @click="onReport">提交举报</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import {
  getPost, likePost, unlikePost,
  listComments, addComment, createReport
} from '../api/treehole'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const comments = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const commentText = ref('')
// 当前正在回复的评论，null 表示直接评论帖子
const replyTo = ref(null)
const commentField = ref(null)
const showReport = ref(false)
const reportReason = ref('')
const reporting = ref(false)

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  post.value = await getPost(route.params.id)
})

async function onLoad() {
  try {
    const data = await listComments(route.params.id, { page: page.value, size: 10 })
    comments.value.push(...data.records)
    if (data.records.length < 10 || comments.value.length >= data.total) {
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

async function toggleLike() {
  if (post.value.liked) {
    const res = await unlikePost(post.value.id)
    post.value.liked = res.liked
    post.value.likeCount = res.likeCount
  } else {
    const res = await likePost(post.value.id)
    post.value.liked = res.liked
    post.value.likeCount = res.likeCount
  }
}

function startReply(comment) {
  replyTo.value = { id: comment.id, nickname: comment.nickname }
  commentField.value?.focus()
}

function cancelReply() {
  replyTo.value = null
}

async function onAddComment() {
  if (!commentText.value.trim()) {
    showToast('请输入评论内容')
    return
  }
  const c = await addComment(route.params.id, {
    content: commentText.value,
    parentId: replyTo.value?.id ?? null
  })
  comments.value.push(c)
  post.value.commentCount = (post.value.commentCount || 0) + 1
  commentText.value = ''
  cancelReply()
}

async function onReport() {
  reporting.value = true
  try {
    await createReport({
      targetType: 'POST',
      targetId: Number(route.params.id),
      reason: reportReason.value
    })
    showToast('举报已提交')
    showReport.value = false
    reportReason.value = ''
  } finally {
    reporting.value = false
  }
}
</script>

<style scoped>
.post {
  background: var(--van-background-2);
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.post-head,
.comment-head {
  display: flex;
  justify-content: space-between;
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
  white-space: pre-wrap;
}
.post-foot {
  display: flex;
  gap: 20px;
  color: var(--van-text-color-2);
}
.liked {
  color: #ee0a24;
}
.report {
  margin-left: auto;
  color: var(--van-text-color-2);
  font-size: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 4px 8px;
}
.comment {
  padding: 12px 0;
  border-bottom: 1px solid var(--van-border-color);
}
.comment-content {
  margin-top: 6px;
  line-height: 1.5;
}
.comment-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.reply-btn {
  color: var(--xinyu-accent);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
}
.reply-to {
  color: var(--xinyu-accent);
}
.input-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: var(--xinyu-shell);
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: var(--xinyu-glass-bg);
  backdrop-filter: blur(18px) saturate(180%);
  -webkit-backdrop-filter: blur(18px) saturate(180%);
  padding: 8px 12px;
  border-top: 1px solid var(--van-border-color);
}
.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.reply-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--xinyu-accent);
  padding: 0 4px;
}
.reply-banner__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-box {
  padding: 20px 16px 24px;
}
.report-box h3 {
  margin-bottom: 12px;
}
.report-btns {
  margin-top: 16px;
}
</style>
