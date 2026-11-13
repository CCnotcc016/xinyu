<template>
  <div class="page">
    <van-nav-bar title="我的举报" left-arrow @click-left="router.back()" />

    <p class="tip">举报后我们会尽快核实，处理结果会在这里和「消息通知」里同步给你。</p>

    <van-list
      v-model:loading="loading"
      v-model:error="error"
      :finished="finished"
      finished-text="没有更多了"
      error-text="加载失败，点击重新加载"
      @load="onLoad"
    >
      <van-empty v-if="finished && !items.length" description="还没有提交过举报" />

      <div v-for="r in items" :key="r.id" class="item xinyu-glass">
        <div class="item__head">
          <span class="item__type">{{ typeLabel(r.targetType) }}</span>
          <van-tag :type="statusType(r.status)">{{ statusLabel(r.status) }}</van-tag>
        </div>
        <p v-if="r.targetContent" class="item__content">{{ r.targetContent }}</p>
        <p class="item__reason">举报原因：{{ r.reason || '未填写' }}</p>
        <div v-if="r.remark" class="item__remark">
          <van-icon name="chat-o" />
          <span>{{ r.remark }}</span>
        </div>
        <div class="item__foot">
          <span>{{ timeLabel(r.createdAt) }}</span>
          <span v-if="link(r)" class="item__link" @click="router.push(link(r))">查看内容</span>
        </div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMyReports } from '../api/report'

const router = useRouter()
const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

const TYPE_LABELS = { POST: '树洞帖子', COMMENT: '树洞评论', USER: '用户' }
const STATUS_LABELS = { PENDING: '待处理', HANDLED: '已处理', DISMISSED: '未通过' }

function typeLabel(t) {
  return TYPE_LABELS[t] || t
}
function statusLabel(s) {
  return STATUS_LABELS[s] || s
}
function statusType(s) {
  return s === 'HANDLED' ? 'success' : s === 'PENDING' ? 'warning' : 'default'
}
function timeLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}
/** 只有帖子和用户还有页面可看；评论跟着帖子走，但帖子可能已下架，所以不给链接 */
function link(r) {
  if (r.targetType === 'POST' && r.targetId) return `/treehole/${r.targetId}`
  if (r.targetType === 'USER' && r.targetId) return `/u/${r.targetId}`
  return null
}

async function onLoad() {
  try {
    const data = await listMyReports({ page: page.value, size: 20 })
    items.value.push(...data.records)
    if (data.records.length < 20 || items.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tip {
  margin: 12px 4px 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.item {
  margin-top: 12px;
  padding: 14px 16px;
  border-radius: 16px;
}
.item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.item__type {
  font-size: 14px;
  font-weight: 600;
}
.item__content {
  margin: 10px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--van-background-2);
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.item__reason {
  margin: 10px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  word-break: break-word;
}
.item__remark {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--van-text-color-3);
}
.item__link {
  color: var(--xinyu-accent);
  cursor: pointer;
}
</style>
