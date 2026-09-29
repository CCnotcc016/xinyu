<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <Transition name="alert-banner">
    <div v-if="visible" class="crisis-banner" role="alert" @click="goAdmin">
      <LiquidGlass class="crisis-banner__glass">
        <div class="crisis-banner__row">
          <span class="crisis-banner__icon">!</span>

          <div class="crisis-banner__body">
            <div class="crisis-banner__title">有 {{ count }} 条危机预警待跟进</div>
            <div class="crisis-banner__meta">
              {{ who }} · 命中「{{ alert.keyword }}」·
              {{ alert.targetType === 'DIARY' ? '私密日记' : '树洞帖子' }} ·
              {{ shortTime(alert.createdAt) }}
            </div>
            <div class="crisis-banner__phone" @click.stop>
              注册手机号：
              <a v-if="alert.phone" class="crisis-banner__tel" :href="`tel:${alert.phone}`">
                {{ alert.phone }}
              </a>
              <span v-else class="crisis-banner__nobind">未绑定</span>
            </div>
          </div>

          <van-icon name="cross" class="crisis-banner__close" @click.stop="dismiss" />
        </div>

        <div class="crisis-banner__foot">点击进入危机干预 →</div>
      </LiquidGlass>
    </div>
  </Transition>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getCrisisPendingCount, listCrisisAlerts } from '../api/admin'
import LiquidGlass from './LiquidGlass.vue'

const DISMISS_KEY = 'xinyu_crisis_banner_dismissed_id'

const router = useRouter()
const userStore = useUserStore()

const count = ref(0)
const alert = ref(null)
const dismissedId = ref(Number(sessionStorage.getItem(DISMISS_KEY)) || 0)

/*
 * 按「最新一条的 id」而不是条数来判断要不要显示：
 * 用条数的话，处理掉一条又来一条新的、条数刚好相同，通知条就再也不会出现了。
 */
const visible = computed(
  () => userStore.isAdmin && count.value > 0 && !!alert.value && alert.value.id !== dismissedId.value
)

const who = computed(
  () => alert.value?.nickname || alert.value?.username || `用户#${alert.value?.userId}`
)

function dismiss() {
  if (!alert.value) return
  dismissedId.value = alert.value.id
  sessionStorage.setItem(DISMISS_KEY, String(alert.value.id))
}

function goAdmin() {
  router.push('/admin/crisis')
}

function shortTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  if (!userStore.isAdmin) return
  try {
    // 计数和最新一条并发取，只等一次网络往返
    const [countRes, pageRes] = await Promise.all([
      getCrisisPendingCount(),
      listCrisisAlerts({ page: 1, size: 1, status: 'PENDING' })
    ])
    count.value = countRes?.count ?? 0
    alert.value = pageRes?.records?.[0] ?? null
  } catch {
    // 拉不到就当没有预警，别把首页拖垮（错误提示 request.js 已经统一给过了）
    count.value = 0
    alert.value = null
  }
})
</script>

<style scoped>
.crisis-banner {
  margin-bottom: 12px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

/* 通知条比弹窗轻一档：圆角收小、投影收紧 */
.crisis-banner__glass {
  border-radius: 16px;
  box-shadow: 0 8px 22px rgba(238, 10, 36, 0.16);
}

.crisis-banner__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 12px 8px;
}

.crisis-banner__icon {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--van-danger-color);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  line-height: 24px;
  text-align: center;
  animation: crisis-pulse 1.8s ease-out infinite;
}

.crisis-banner__body {
  flex: 1;
  min-width: 0;
}

.crisis-banner__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--van-danger-color);
}

.crisis-banner__meta {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
  word-break: break-all;
}

.crisis-banner__phone {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
}

.crisis-banner__tel {
  color: var(--xinyu-accent);
  font-weight: 600;
  text-decoration: none;
}

.crisis-banner__nobind {
  color: var(--van-text-color-3);
}

.crisis-banner__close {
  flex: none;
  padding: 3px;
  color: var(--van-text-color-3);
  font-size: 14px;
}

.crisis-banner__foot {
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

@keyframes crisis-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(238, 10, 36, 0.5);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(238, 10, 36, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(238, 10, 36, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .crisis-banner__icon {
    animation: none;
  }
}
</style>
