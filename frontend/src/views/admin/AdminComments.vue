<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div>
    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-cell
        v-for="c in comments"
        :key="c.id"
        :title="preview(c.content)"
        :label="`帖子${c.postId} · 用户${c.userId} · ${c.createdAt}`"
      >
        <template #value>
          <van-tag :type="c.status === 1 ? 'success' : 'danger'">
            {{ c.status === 1 ? '正常' : '已删除' }}
          </van-tag>
        </template>
        <template #right-icon>
          <van-button
            size="small"
            :type="c.status === 1 ? 'danger' : 'success'"
            @click="toggle(c)"
          >
            {{ c.status === 1 ? '删除' : '恢复' }}
          </van-button>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { listComments, updateCommentStatus } from '../../api/admin'

const comments = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

function preview(text) {
  return text && text.length > 40 ? text.slice(0, 40) + '…' : text
}

async function onLoad() {
  try {
    const data = await listComments({ page: page.value, size: 10 })
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

async function toggle(c) {
  const next = c.status === 1 ? 0 : 1
  await updateCommentStatus(c.id, next)
  c.status = next
  showToast('已更新')
}
</script>
