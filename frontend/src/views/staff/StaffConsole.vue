<template>
  <div class="page">
    <van-nav-bar title="客服工作台" left-arrow @click-left="router.back()" />

    <div class="tip xinyu-glass">
      <template v-if="userStore.isAdmin">
        你是管理员，可以看到全部会话。回复后会话会归属到回复人，其他客服不再看到，避免多人重复回复。
      </template>
      <template v-else>
        这里只显示「待接手」和你自己接手的会话。回复一条消息就会接手该会话。
      </template>
      <div class="tip__sub">处理完的会话点右上角「结单」收尾；用户再来问会重新分配接手人，接手人看不到结单之前的记录。</div>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        finished-text="没有更多了"
        error-text="加载失败，点击重新加载"
        @load="onLoad"
      >
        <van-empty v-if="finished && !items.length" description="暂无用户咨询" />
        <van-cell
          v-for="c in items"
          :key="c.id"
          :title="c.nickname || c.username || `用户#${c.userId}`"
          :label="`${preview(c.lastMessage)} · ${shortTime(c.lastMessageAt)}`"
          is-link
          @click="router.push(`/staff/${c.id}`)"
        >
          <template #value>
            <van-badge v-if="c.unread > 0" :content="c.unread" max="99" />
          </template>
          <template #right-icon>
            <van-tag v-if="c.status === 'CLOSED'" type="default" plain class="assignee">已结单</van-tag>
            <van-tag v-else :type="c.mine ? 'primary' : 'default'" class="assignee">
              {{ c.mine ? '我接手' : c.assigneeName || '待接手' }}
            </van-tag>
          </template>
        </van-cell>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { listConversations } from '../../api/support'

const router = useRouter()
const userStore = useUserStore()

const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const refreshing = ref(false)

function preview(text) {
  const s = String(text || '暂无消息').replace(/\s+/g, ' ').trim()
  return s.length > 20 ? `${s.slice(0, 20)}…` : s
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : ''
}

async function onLoad() {
  try {
    const data = await listConversations({ page: page.value, size: 10 })
    items.value.push(...data.records)
    if (data.records.length < 10 || items.value.length >= data.total) {
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
  const data = await listConversations({ page: 1, size: 10 })
  items.value = data.records
  finished.value = data.records.length < 10
  error.value = false
  refreshing.value = false
}
</script>

<style scoped>
.tip {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.assignee {
  margin-left: 6px;
}
.tip__sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-3);
}
</style>
