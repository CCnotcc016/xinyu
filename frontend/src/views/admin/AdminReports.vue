<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div>
    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-cell
        v-for="r in reports"
        :key="r.id"
        :title="`${typeLabel(r.targetType)} · ${r.targetContent || '(已删除)'}`"
        :label="`举报人：${r.reporterName} · 理由：${r.reason || '无'} · ${r.createdAt}`"
      >
        <template #value>
          <van-tag :type="statusType(r.status)">{{ statusLabel(r.status) }}</van-tag>
        </template>
        <template #right-icon>
          <span v-if="r.status === 'PENDING'" class="actions">
            <van-button size="small" type="danger" @click="handle(r, 'HANDLED')">处理</van-button>
            <van-button size="small" @click="handle(r, 'DISMISSED')">忽略</van-button>
          </span>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { listReports, handleReport } from '../../api/admin'

const reports = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

function typeLabel(t) {
  return { POST: '帖子', COMMENT: '评论', USER: '用户' }[t] || t
}
function statusLabel(s) {
  return { PENDING: '待处理', HANDLED: '已处理', DISMISSED: '已忽略' }[s] || s
}
function statusType(s) {
  return s === 'HANDLED' ? 'success' : s === 'PENDING' ? 'warning' : 'default'
}

async function onLoad() {
  try {
    const data = await listReports({ page: page.value, size: 10 })
    reports.value.push(...data.records)
    if (data.records.length < 10 || reports.value.length >= data.total) {
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

async function handle(r, action) {
  await handleReport(r.id, action)
  r.status = action
  showToast('已处理')
}
</script>

<style scoped>
.actions {
  display: flex;
  gap: 6px;
}
</style>
