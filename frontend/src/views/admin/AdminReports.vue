<template>
  <div>
    <van-tabs v-model:active="tab" @change="onTabChange">
      <van-tab title="待处理" name="PENDING" />
      <van-tab title="已处理" name="HANDLED" />
      <van-tab title="已忽略" name="DISMISSED" />
      <van-tab title="全部" name="" />
    </van-tabs>

    <van-list v-model:loading="loading" v-model:error="error" :finished="finished" finished-text="没有更多了" error-text="加载失败，点击重新加载" @load="onLoad">
      <van-empty v-if="finished && !reports.length" description="暂无举报记录" />
      <van-cell
        v-for="r in reports"
        :key="r.id"
        :title="`${typeLabel(r.targetType)} · ${r.targetContent || '(已删除)'}`"
        :label="labelText(r)"
      >
        <template #value>
          <van-tag :type="statusType(r.status)">{{ statusLabel(r.status) }}</van-tag>
        </template>
        <template #right-icon>
          <span v-if="r.status === 'PENDING'" class="actions">
            <van-button size="small" type="danger" @click="openHandle(r, 'HANDLED')">处理</van-button>
            <van-button size="small" @click="openHandle(r, 'DISMISSED')">忽略</van-button>
          </span>
        </template>
      </van-cell>
    </van-list>

    <!-- 处理时可以写一句说明，举报人会在「我的举报」和通知里看到 -->
    <van-dialog
      v-model:show="showHandle"
      :title="form.action === 'HANDLED' ? '确认举报成立并下架内容？' : '忽略这条举报？'"
      show-cancel-button
      :before-close="submit"
    >
      <div class="handle-box">
        <p class="handle-box__tip">
          {{
            form.action === 'HANDLED'
              ? '内容下架后，被举报的用户会收到一条处理通知。'
              : '确认为误报后，内容会保持原样。'
          }}
        </p>
        <van-field
          v-model="form.remark"
          type="textarea"
          rows="3"
          maxlength="200"
          show-word-limit
          placeholder="处理说明（可选，举报人可见）"
        />
      </div>
    </van-dialog>
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
// 和「预警」页同一套 tab：默认只看待处理，翻历史要自己切
const tab = ref('PENDING')

function typeLabel(t) {
  return { POST: '帖子', COMMENT: '评论', USER: '用户' }[t] || t
}
function statusLabel(s) {
  return { PENDING: '待处理', HANDLED: '已处理', DISMISSED: '已忽略' }[s] || s
}
function statusType(s) {
  return s === 'HANDLED' ? 'success' : s === 'PENDING' ? 'warning' : 'default'
}

// 换 tab 等于换数据集：清空后交给 van-list 重新从第一页加载
function onTabChange() {
  reports.value = []
  page.value = 1
  finished.value = false
  error.value = false
  loading.value = true
  onLoad()
}

async function onLoad() {
  try {
    const data = await listReports({ page: page.value, size: 10, status: tab.value || undefined })
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

const showHandle = ref(false)
const form = ref({ id: null, action: 'HANDLED', remark: '' })

/** 已处理的举报把说明也显示出来，方便回看当时为什么这么判 */
function labelText(r) {
  const base = `举报人：${r.reporterName} · 理由：${r.reason || '无'} · ${r.createdAt}`
  return r.remark ? `${base}\n处理说明：${r.remark}` : base
}

function openHandle(r, action) {
  form.value = { id: r.id, action, remark: action === 'HANDLED' ? '内容违规，已下架' : '未发现违规' }
  showHandle.value = true
}

async function submit(action) {
  if (action !== 'confirm') return true
  const { id, remark } = form.value
  const target = reports.value.find((item) => item.id === id)
  try {
    await handleReport(id, form.value.action, remark?.trim() || undefined)
  } catch {
    // 失败时保留弹窗，管理员改完还能再提交
    return false
  }
  if (target) {
    target.status = form.value.action
    target.remark = remark?.trim() || null
  }
  showToast('已处理')
  // 在「待处理」里处理完就该从列表里消失，留在原地会让人以为没生效
  if (tab.value === 'PENDING') {
    reports.value = reports.value.filter((item) => item.id !== id)
  }
  return true
}
</script>

<style scoped>
.actions {
  display: flex;
  gap: 6px;
}
/* 处理说明换行显示 */
:deep(.van-cell__label) {
  white-space: pre-line;
}
.handle-box {
  padding: 4px 16px 14px;
}
.handle-box__tip {
  margin: 0 0 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
</style>
