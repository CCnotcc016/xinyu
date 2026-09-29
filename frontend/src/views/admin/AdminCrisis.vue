<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="crisis-admin">
    <div class="crisis-admin__tip">
      这里只用于危机干预：出现自残 / 自杀等表达时，请优先联系本人或通知其紧急联系人，
      不要外传内容或手机号。
    </div>

    <van-tabs v-model:active="tab" @change="onTabChange">
      <van-tab title="待跟进" name="PENDING" />
      <van-tab title="已处理" name="HANDLED" />
      <van-tab title="已忽略" name="DISMISSED" />
      <van-tab title="全部" name="" />
    </van-tabs>

    <van-list
      v-model:loading="loading"
      v-model:error="error"
      :finished="finished"
      finished-text="没有更多了"
      error-text="加载失败，点击重新加载"
      @load="onLoad"
    >
      <van-empty v-if="finished && !items.length" description="暂无预警记录" />
      <van-cell v-for="a in items" :key="a.id" class="item">
        <template #title>
          <div class="item__head">
            <span class="item__user">{{ a.nickname || a.username || `用户#${a.userId}` }}</span>
            <van-tag :type="statusType(a.status)">{{ statusLabel(a.status) }}</van-tag>
          </div>
        </template>
        <template #label>
          <div class="item__meta">
            命中关键词「<span class="accent">{{ a.keyword }}</span>」 ·
            {{ a.targetType === 'DIARY' ? '私密日记' : '树洞帖子' }} ·
            {{ a.createdAt }}
          </div>
          <div class="item__content">{{ a.content }}</div>
          <div class="item__phone">
            注册手机号：
            <a v-if="a.phone" class="accent" :href="`tel:${a.phone}`">{{ a.phone }}</a>
            <span v-else class="item__nobind">未绑定</span>
          </div>
          <div class="item__actions">
            <van-button
              v-if="a.status === 'PENDING'"
              size="small"
              type="primary"
              @click="handle(a, 'HANDLED')"
            >
              标记已跟进
            </van-button>
            <van-button
              v-if="a.status === 'PENDING'"
              size="small"
              plain
              @click="handle(a, 'DISMISSED')"
            >
              忽略
            </van-button>
            <span v-else class="item__done">{{ a.remark || '已处理' }}</span>
          </div>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { listCrisisAlerts, handleCrisisAlert } from '../../api/admin'

const tab = ref('PENDING')
const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)

function statusLabel(s) {
  return { PENDING: '待跟进', HANDLED: '已跟进', DISMISSED: '已忽略' }[s] || s
}
function statusType(s) {
  return s === 'PENDING' ? 'danger' : s === 'HANDLED' ? 'success' : 'default'
}

function onTabChange() {
  items.value = []
  page.value = 1
  finished.value = false
  error.value = false
  // 让 van-list 重新触发一次 load
  loading.value = true
  onLoad()
}

async function onLoad() {
  try {
    const data = await listCrisisAlerts({ page: page.value, size: 10, status: tab.value || undefined })
    items.value.push(...data.records)
    if (data.records.length < 10 || items.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    // 进入 error 态，van-list 会显示「点击重新加载」并暂停自动加载
    error.value = true
  } finally {
    loading.value = false
  }
}

async function handle(a, status) {
  try {
    await showConfirmDialog({
      title: status === 'HANDLED' ? '确认已跟进？' : '确认忽略这条预警？',
      message:
        status === 'HANDLED'
          ? `请确认已经联系或通知到「${a.nickname || a.username}」。`
          : '误报可以忽略，记录会保留但不再出现在待跟进列表。'
    })
  } catch {
    return
  }
  await handleCrisisAlert(a.id, { status, remark: status === 'HANDLED' ? '已联系本人' : '误报' })
  items.value = items.value.filter((x) => x.id !== a.id)
  if (!items.value.length) finished.value = true
  showToast('已记录处理结果')
}
</script>

<style scoped>
.crisis-admin__tip {
  margin: 10px 0 6px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.item__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.item__user {
  font-weight: 600;
}
.item__meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.item__content {
  /* 保留换行，照原文看更利于判断严重程度 */
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--van-background-2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.item__phone {
  margin-top: 8px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.item__nobind {
  color: var(--van-text-color-3);
}
.item__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.item__done {
  font-size: 12px;
  color: var(--van-text-color-2);
}
</style>
