<template>
  <Transition name="alert-banner">
    <div v-if="visible" class="report-banner" role="alert" @click="goAdmin">
      <LiquidGlass class="report-banner__glass">
        <div class="report-banner__row">
          <span class="report-banner__icon">!</span>

          <div class="report-banner__body">
            <div class="report-banner__title">有 {{ count }} 条举报待处理</div>
            <div class="report-banner__meta">
              {{ typeLabel(report.targetType) }} · {{ preview(report.targetContent) }} ·
              {{ report.reporterName || '匿名用户' }} 举报 ·
              {{ shortTime(report.createdAt) }}
            </div>
            <div class="report-banner__reason">理由：{{ report.reason || '未填写' }}</div>
          </div>

          <van-icon name="cross" class="report-banner__close" @click.stop="dismiss" />
        </div>

        <div class="report-banner__foot">点击进入举报处理 →</div>
      </LiquidGlass>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getReportPendingCount, listReports } from '../api/admin'
import LiquidGlass from './LiquidGlass.vue'

const DISMISS_KEY = 'xinyu_report_banner_dismissed_id'

const router = useRouter()
const userStore = useUserStore()

const count = ref(0)
const report = ref(null)
const dismissedId = ref(Number(sessionStorage.getItem(DISMISS_KEY)) || 0)

/*
 * 和危机预警条同一套逻辑：按「最新一条的 id」判断，而不是按条数 ——
 * 用条数的话，处理掉一条又来一条新的、条数刚好相同，通知条就再也不会出现了。
 */
const visible = computed(
  () => userStore.isAdmin && count.value > 0 && !!report.value && report.value.id !== dismissedId.value
)

function dismiss() {
  if (!report.value) return
  dismissedId.value = report.value.id
  sessionStorage.setItem(DISMISS_KEY, String(report.value.id))
}

function goAdmin() {
  router.push('/admin/reports')
}

function typeLabel(t) {
  return { POST: '举报帖子', COMMENT: '举报评论', USER: '举报用户' }[t] || '举报'
}

function preview(text) {
  const s = String(text || '(内容已删除)').replace(/\s+/g, ' ').trim()
  return s.length > 24 ? `${s.slice(0, 24)}…` : s
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  if (!userStore.isAdmin) return
  try {
    // 计数和最新一条并发取，只等一次网络往返
    const [countRes, pageRes] = await Promise.all([
      getReportPendingCount(),
      listReports({ page: 1, size: 1, status: 'PENDING' })
    ])
    count.value = countRes?.count ?? 0
    report.value = pageRes?.records?.[0] ?? null
  } catch {
    // 拉不到就当没有举报，别把首页拖垮（错误提示 request.js 已经统一给过了）
    count.value = 0
    report.value = null
  }
})
</script>

<style scoped>
.report-banner {
  margin-bottom: 12px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

/* 比危机预警（红）轻一档：举报用橙色，视觉上不抢危机干预的注意力 */
.report-banner__glass {
  border-radius: 16px;
  box-shadow: 0 8px 22px rgba(255, 151, 106, 0.18);
}

.report-banner__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 12px 8px;
}

.report-banner__icon {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #ff8f1f;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  line-height: 24px;
  text-align: center;
  animation: report-pulse 1.8s ease-out infinite;
}

.report-banner__body {
  flex: 1;
  min-width: 0;
}

.report-banner__title {
  font-size: 14px;
  font-weight: 600;
  color: #ff8f1f;
}

.report-banner__meta,
.report-banner__reason {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
  word-break: break-all;
}

.report-banner__close {
  flex: none;
  padding: 3px;
  color: var(--van-text-color-3);
  font-size: 14px;
}

.report-banner__foot {
  padding: 0 12px 10px;
  font-size: 12px;
  color: var(--xinyu-accent);
}

@keyframes report-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 143, 31, 0.5);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(255, 143, 31, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 143, 31, 0);
  }
}

/* 首页顶部滑入滑出 */
.alert-banner-enter-active,
.alert-banner-leave-active {
  transition: opacity 0.26s ease, transform 0.26s ease;
}

.alert-banner-enter-from,
.alert-banner-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

@media (prefers-reduced-motion: reduce) {
  .report-banner__icon {
    animation: none;
  }
}
</style>
