<template>
  <Transition name="alert-banner">
    <div v-if="visible" class="support-banner" role="alert" @click="open">
      <LiquidGlass class="support-banner__glass">
        <div class="support-banner__row">
          <van-icon :name="isStaffView ? 'service-o' : 'chat-o'" class="support-banner__icon" />

          <div class="support-banner__body">
            <div class="support-banner__title">{{ title }}</div>
            <div class="support-banner__meta">
              <span v-if="!isStaffView && data.latestSenderName">
                客服 · {{ data.latestSenderName }} 回复
              </span>
              <span v-else-if="data.latestSenderName">{{ data.latestSenderName }}</span>
              <span>{{ shortTime(data.latestAt) }}</span>
            </div>
            <div class="support-banner__preview">{{ preview(data.latestMessage) }}</div>
          </div>

          <van-icon name="cross" class="support-banner__close" @click.stop="dismiss" />
        </div>

        <div class="support-banner__foot">{{ isStaffView ? '点击进入客服工作台' : '点击查看回复' }} →</div>
      </LiquidGlass>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getUnreadCount, getStaffUnreadCount } from '../api/support'
import LiquidGlass from './LiquidGlass.vue'

const DISMISS_KEY = 'xinyu_support_banner_dismissed_at'
// 客服回复 / 用户咨询都是异步来的，光在挂载时拉一次会一直看不到新消息
const POLL_MS = 60000

const router = useRouter()
const userStore = useUserStore()

const data = ref({})
const dismissedAt = ref(sessionStorage.getItem(DISMISS_KEY) || '')

// 客服视角（管理员 / 客服）看的是「用户来问」，普通用户看的是「客服回复」
const isStaffView = computed(() => userStore.isAdminOrStaff)

const count = computed(() => data.value?.count ?? 0)

const title = computed(() =>
  isStaffView.value ? `有 ${count.value} 条用户咨询待回复` : `客服回复了你 ${count.value} 条消息`
)

/*
 * 按「最新一条消息的时间」判断是否已忽略：
 * 客服会话里没有稳定的「通知 id」可用，时间戳变化就代表来了新消息，
 * 忽略过的旧消息不会重复出现。
 */
const visible = computed(() => count.value > 0 && !!data.value?.latestAt && data.value.latestAt !== dismissedAt.value)

function dismiss() {
  dismissedAt.value = data.value?.latestAt || ''
  sessionStorage.setItem(DISMISS_KEY, dismissedAt.value)
}

function open() {
  router.push(isStaffView.value ? '/staff' : '/support')
}

function preview(text) {
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 30 ? `${s.slice(0, 30)}…` : s
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    data.value = (isStaffView.value ? await getStaffUnreadCount() : await getUnreadCount()) || {}
  } catch {
    // 拉不到就当没有新消息，别把首页拖垮（错误提示 request.js 已经统一给过了）
    data.value = {}
  }
}

let timer = 0

onMounted(() => {
  load()
  timer = window.setInterval(load, POLL_MS)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.support-banner {
  margin-bottom: 12px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.support-banner__glass {
  border-radius: 16px;
  box-shadow: 0 8px 22px rgba(25, 137, 250, 0.16);
}

.support-banner__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 12px 8px;
}

.support-banner__icon {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--van-primary-color);
  color: #fff;
  font-size: 14px;
  line-height: 24px;
  text-align: center;
}

.support-banner__body {
  flex: 1;
  min-width: 0;
}

.support-banner__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--van-primary-color);
}

.support-banner__meta {
  display: flex;
  gap: 8px;
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
}

.support-banner__preview {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color);
  word-break: break-all;
}

.support-banner__close {
  flex: none;
  padding: 3px;
  color: var(--van-text-color-3);
  font-size: 14px;
}

.support-banner__foot {
  padding: 0 12px 10px;
  font-size: 12px;
  color: var(--xinyu-accent);
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
</style>
