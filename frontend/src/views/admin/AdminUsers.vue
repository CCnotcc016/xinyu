<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div>
    <van-search v-model="keyword" placeholder="按用户名搜索" @search="onSearch" />
    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-cell v-for="u in users" :key="u.id" :title="u.nickname || u.username" :label="`@${u.username} · ${u.role}`">
        <template #value>
          <van-tag :type="u.status === 1 ? 'success' : 'danger'">
            {{ u.status === 1 ? '正常' : '禁用' }}
          </van-tag>
        </template>
        <template #right-icon>
          <div class="ops">
            <van-button
              size="small"
              :type="u.status === 1 ? 'danger' : 'success'"
              @click="toggle(u)"
            >
              {{ u.status === 1 ? '禁用' : '启用' }}
            </van-button>
            <van-button size="small" type="default" @click="remove(u)">删除</van-button>
          </div>
        </template>
      </van-cell>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { listUsers, updateUserStatus, deleteUser } from '../../api/admin'

const users = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const keyword = ref('')

async function onLoad() {
  try {
    const data = await listUsers({ page: page.value, size: 10, keyword: keyword.value })
    users.value.push(...data.records)
    if (data.records.length < 10 || users.value.length >= data.total) {
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

function onSearch() {
  users.value = []
  page.value = 1
  finished.value = false
  error.value = false
  // 列表清空后 van-list 会自己再触发一次 load，先用 loading 占住它，
  // 否则第一页会被请求两遍、列表里出现重复行
  loading.value = true
  onLoad()
}

async function toggle(u) {
  const next = u.status === 1 ? 0 : 1
  await updateUserStatus(u.id, next)
  u.status = next
  showToast('已更新')
}

async function remove(u) {
  await showConfirmDialog({
    title: '删除账号',
    message: `确定删除 ${u.nickname || u.username} 吗？\n该账号的日记、树洞帖子、评论等数据会一并删除，且无法恢复。`
  })
  await deleteUser(u.id)
  users.value = users.value.filter((item) => item.id !== u.id)
  showToast('已删除')
}
</script>

<style scoped>
.ops {
  display: flex;
  gap: 6px;
}
</style>
