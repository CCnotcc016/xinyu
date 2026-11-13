<template>
  <div class="crisis-admin">
    <div class="crisis-admin__tip">
      这里只用于危机干预：出现自残 / 自杀等表达时，请优先联系本人或通知其紧急联系人，
      不要外传内容或手机号。
    </div>

    <van-tabs v-model:active="tab" @change="onTabChange">
      <van-tab name="PENDING">
        <template #title>
          <van-badge :content="pendingCount || ''" :show-zero="false" class="tab-badge">待跟进</van-badge>
        </template>
      </van-tab>
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
            {{ targetLabel(a.targetType) }} ·
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
              @click="openHandle(a, 'HANDLED')"
            >
              标记已跟进
            </van-button>
            <van-button
              v-if="a.status === 'PENDING'"
              size="small"
              plain
              @click="openHandle(a, 'DISMISSED')"
            >
              忽略
            </van-button>
            <span v-else class="item__done">
              <template v-if="a.remark">{{ a.remark }}</template>
              <template v-else>已处理</template>
              <span v-if="a.handledByName" class="item__by">
                · {{ a.handledByName }} {{ (a.handledAt || '').replace('T', ' ').slice(5, 16) }}
              </span>
            </span>
            <van-button
              v-if="a.status === 'HANDLED'"
              size="small"
              plain
              type="primary"
              @click="openFollowUp(a)"
            >
              回访
            </van-button>
          </div>
          <div v-if="a.followUpAt" class="item__follow">
            <van-icon name="phone-circle-o" />
            <span>
              回访 {{ (a.followUpAt || '').replace('T', ' ').slice(5, 16) }}
              <template v-if="a.followUpByName">· {{ a.followUpByName }}</template>
              ：{{ a.followUpNote }}
            </span>
          </div>
        </template>
      </van-cell>
    </van-list>

    <!-- 处理结果要点一下确认才落库：备注是可选的，但默认文案给个起点，管理员改起来更快 -->
    <van-dialog
      v-model:show="showHandle"
      :title="handleForm.status === 'HANDLED' ? '记录跟进结果' : '确认忽略这条预警？'"
      show-cancel-button
      :before-close="submitHandle"
    >
      <div class="handle-box">
        <p class="handle-box__tip">
          {{
            handleForm.status === 'HANDLED'
              ? `请确认已经联系或通知到「${handleForm.nickname}」。`
              : '误报可以忽略，记录会保留但不再出现在待跟进列表。'
          }}
        </p>
        <van-field
          v-model="handleForm.remark"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="处理备注（例如：已电话联系本人）"
        />
      </div>
    </van-dialog>

    <!-- 回访：处理完之后过段时间再看一眼，记录是否联系上、状态如何 -->
    <van-dialog
      v-model:show="showFollow"
      title="记录回访情况"
      show-cancel-button
      :before-close="submitFollow"
    >
      <div class="handle-box">
        <p class="handle-box__tip">只记录和干预有关的情况，不要写下与本次跟进无关的隐私。</p>
        <van-field
          v-model="followNote"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="例如：今天电话联系上，情绪稳定，已告知援助热线"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import {
  listCrisisAlerts,
  handleCrisisAlert,
  followUpCrisisAlert,
  getCrisisPendingCount
} from '../../api/admin'
import { crisisTargetLabel as targetLabel } from '../../utils/crisis'

const tab = ref('PENDING')
const items = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const pendingCount = ref(0)

// 处理弹窗的表单：status 决定是「已跟进」还是「已忽略」
const showHandle = ref(false)
const handleForm = ref({ id: null, status: 'HANDLED', remark: '', nickname: '' })

// 回访弹窗：只对已跟进的预警开放
const showFollow = ref(false)
const followNote = ref('')
const followId = ref(null)

function openFollowUp(a) {
  followId.value = a.id
  followNote.value = ''
  showFollow.value = true
}

async function submitFollow(action) {
  if (action !== 'confirm') return true
  const note = followNote.value.trim()
  if (!note) {
    showToast('请写下这次回访的情况')
    return false
  }
  try {
    await followUpCrisisAlert(followId.value, note)
  } catch {
    return false
  }
  const row = items.value.find((x) => x.id === followId.value)
  if (row) {
    row.followUpAt = new Date().toISOString().slice(0, 19)
    row.followUpNote = note
  }
  showToast('已记录回访')
  return true
}

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

function openHandle(a, status) {
  handleForm.value = {
    id: a.id,
    status,
    nickname: a.nickname || a.username || `用户#${a.userId}`,
    remark: status === 'HANDLED' ? '已联系本人' : '误报'
  }
  showHandle.value = true
}

/**
 * 提交处理结果。
 *
 * van-dialog 的 before-close 只有返回 true 才会关闭，返回 false 时留在弹窗里，
 * 所以失败（例如备注超长）不会把管理员的输入清掉。
 */
async function submitHandle(action) {
  if (action !== 'confirm') return true
  const form = handleForm.value
  try {
    await handleCrisisAlert(form.id, {
      status: form.status,
      remark: form.remark?.trim() || undefined
    })
  } catch {
    // 请求失败时 request.js 已弹过提示，这里保留弹窗让管理员改完再提交
    return false
  }
  if (form.status === 'PENDING' || tab.value === 'PENDING') {
    items.value = items.value.filter((x) => x.id !== form.id)
    if (!items.value.length) finished.value = true
  } else {
    // 在「全部」等列表里处理时直接就地更新，不用整页刷新
    const row = items.value.find((x) => x.id === form.id)
    if (row) {
      row.status = form.status
      row.remark = form.remark
      row.handledAt = new Date().toISOString().slice(0, 19)
    }
  }
  showToast('已记录处理结果')
  loadPendingCount()
  return true
}

async function loadPendingCount() {
  const res = await getCrisisPendingCount()
  pendingCount.value = res?.count ?? 0
}

onMounted(loadPendingCount)
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
.item__follow {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.item__by {
  opacity: 0.75;
}
.tab-badge {
  margin: 0 4px;
}
.handle-box {
  padding: 12px 16px 16px;
}
.handle-box__tip {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
</style>
