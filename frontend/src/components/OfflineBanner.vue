<template>
  <transition name="offline-slide">
    <div v-if="store.showBanner" class="offline">
      <div
        class="offline__pill"
        :class="{ 'offline__pill--busy': store.syncing, 'offline__pill--down': !store.online }"
        role="button"
        @click="onTap"
      >
        <van-loading v-if="store.syncing" size="14" color="currentColor" />
        <van-icon v-else :name="store.online ? 'replay' : 'warning-o'" class="offline__icon" />
        <span>{{ text }}</span>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { computed } from 'vue'
import { showToast } from 'vant'
import { useOfflineStore } from '../stores/offline'

const store = useOfflineStore()

// 文案按「能不能传」和「有没有东西要传」两种维度组合，正常使用时就三句话轮不到别的
const text = computed(() => {
  const { online, pending, syncing } = store
  if (syncing) return pending ? `正在同步 ${pending} 篇…` : '正在同步…'
  if (!online) return pending ? `离线中 · ${pending} 篇待同步` : '离线中 · 仍可继续写日记'
  return `${pending} 篇待同步`
})

async function onTap() {
  if (store.syncing) return
  if (!store.pending) return
  // 明确离线时不要空跑一次请求，不然用户要等超时才看到提示
  if (!navigator.onLine) {
    showToast('当前没有网络，连上后会自动同步')
    return
  }
  const result = await store.sync()
  if (!result || result.synced === 0) {
    showToast(store.online ? '同步失败，稍后会自动重试' : '仍然连不上服务器，稍后再试')
    return
  }
  showToast(
    result.remaining > 0 ? `已同步 ${result.synced} 篇，还有 ${result.remaining} 篇` : `已同步 ${result.synced} 篇`
  )
}
</script>

<style scoped>
.offline {
  position: fixed;
  left: 0;
  right: 0;
  /* 贴在底部导航上方；没有导航的页面（详情、写日记）也停在同一高度，位置稳定不跳 */
  bottom: calc(58px + env(safe-area-inset-bottom));
  z-index: 19;
  display: flex;
  justify-content: center;
  /* 只让药丸本身可点，左右留白不挡下面的内容 */
  pointer-events: none;
}

.offline__pill {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: calc(100% - 32px);
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--xinyu-glass-strong);
  border: 1px solid var(--xinyu-glass-border);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(12px) saturate(160%);
  -webkit-backdrop-filter: blur(12px) saturate(160%);
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  animation: offline-in 0.24s ease-out;
}

.offline__pill--down {
  color: #b45309;
}

.offline__pill--busy {
  color: var(--van-primary-color);
}

.offline__icon {
  font-size: 14px;
}

@keyframes offline-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.offline-slide-enter-active,
.offline-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.offline-slide-enter-from,
.offline-slide-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
