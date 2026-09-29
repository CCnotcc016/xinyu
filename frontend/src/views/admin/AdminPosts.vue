<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div>
    <van-dropdown-menu>
      <van-dropdown-item v-model="status" :options="statusOptions" @change="onFilter" />
    </van-dropdown-menu>
    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-cell
        v-for="p in posts"
        :key="p.id"
        :title="preview(p.content)"
        :label="`用户${p.userId} · ${p.createdAt} · 赞${p.likeCount} · 评${p.commentCount}`"
      >
        <template #value>
          <van-tag :type="p.status === 1 ? 'success' : 'danger'">
            {{ p.status === 1 ? '正常' : '已下架' }}
          </van-tag>
        </template>
        <template #right-icon>
          <div class="ops">
            <van-button
              size="small"
              :type="p.status === 1 ? 'danger' : 'success'"
              @click="toggle(p)"
            >
              {{ p.status === 1 ? '下架' : '上架' }}
            </van-button>
            <van-button size="small" type="danger" plain @click="onDelete(p.id)">删除</van-button>
          </div>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { listPosts, updatePostStatus, deletePost } from '../../api/admin'

const posts = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const status = ref(null)
const statusOptions = [
  { text: '全部', value: null },
  { text: '正常', value: 1 },
  { text: '已下架', value: 0 }
]

function preview(text) {
  return text && text.length > 40 ? text.slice(0, 40) + '…' : text
}

async function onLoad() {
  try {
    const data = await listPosts({ page: page.value, size: 10, status: status.value })
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

function onFilter() {
  posts.value = []
  page.value = 1
  finished.value = false
  error.value = false
  // 同 AdminUsers：占住 van-list，避免第一页请求发两遍产生重复行
  loading.value = true
  onLoad()
}

async function toggle(p) {
  const next = p.status === 1 ? 0 : 1
  await updatePostStatus(p.id, next)
  p.status = next
  showToast('已更新')
}

async function onDelete(id) {
  try {
    await showConfirmDialog({ title: '提示', message: '删除后帖子的评论和点赞也会一起清空，确定删除？' })
  } catch {
    return
  }
  await deletePost(id)
  posts.value = posts.value.filter((p) => p.id !== id)
  showToast('已删除')
}
</script>

<style scoped>
.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
