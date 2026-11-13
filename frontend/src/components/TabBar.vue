<template>
  <nav class="xinyu-tabbar">
    <div class="xinyu-tabbar__inner" :class="{ 'xinyu-tabbar__inner--staff': userStore.isStaff }">
      <!-- 药丸指示器跟着选中项滑动，配合图标缩放做切换动画 -->
      <span
        class="xinyu-tabbar__pill"
        :style="{ width: `${100 / items.length}%`, transform: `translateX(${activeIndex * 100}%)` }"
      />
      <router-link
        v-for="(item, index) in items"
        :key="item.to"
        class="xinyu-tabbar__item"
        :class="{ active: index === activeIndex }"
        :to="item.to"
        replace
      >
        <van-badge
          v-if="item.badge && chatStore.unread > 0"
          :content="chatStore.unread > 99 ? '99+' : chatStore.unread"
          class="xinyu-tabbar__badge"
        >
          <van-icon :name="index === activeIndex ? item.activeIcon : item.icon" />
        </van-badge>
        <van-icon v-else :name="index === activeIndex ? item.activeIcon : item.icon" />
        <span>{{ item.label }}</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useChatStore } from '../stores/chat'

// 用 van-tabbar 时做不到「指示器滑动 + 图标回弹」，所以自己实现一个：
// 结构简单，样式在 assets/main.css 的 .xinyu-tabbar* 里（毛玻璃 + 动画）。
const userItems = [
  { to: '/', label: '首页', icon: 'home-o', activeIcon: 'wap-home' },
  { to: '/diaries', label: '日记', icon: 'notes-o', activeIcon: 'notes' },
  { to: '/treehole', label: '树洞', icon: 'friends-o', activeIcon: 'friends' },
  { to: '/chat', label: '聊天', icon: 'chat-o', activeIcon: 'chat', badge: true },
  { to: '/profile', label: '我的', icon: 'user-o', activeIcon: 'user-circle-o' }
]
// 客服账号不能写日记、也不能发树洞，底部只留工作台和「我的」
const staffItems = [
  { to: '/staff', label: '工作台', icon: 'service-o', activeIcon: 'service' },
  { to: '/profile', label: '我的', icon: 'user-o', activeIcon: 'user-circle-o' }
]

const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const items = computed(() => (userStore.isStaff ? staffItems : userItems))

// 树洞详情、日记详情等子页面也算在所属 tab 上，导航栏选中状态才不会跳
const activeIndex = computed(() => {
  const path = route.path
  if (userStore.isStaff) {
    return path.startsWith('/profile') || path.startsWith('/settings') ? 1 : 0
  }
  if (path === '/') return 0
  // 注意别把 '/diaries' 简写成 '/dai'：路径前四个字母是 /dia，写错就永远匹配不上，
  // 选中态会掉回首页（内容已经是日记列表，底部还是「首页」亮着）
  if (path.startsWith('/diaries') || path.startsWith('/trend')) return 1
  if (path.startsWith('/treehole')) return 2
  // 好友申请页是从「我的」进去的，返回时高亮也该留在「我的」
  if (path.startsWith('/chat')) return 3
  if (path.startsWith('/profile') || path.startsWith('/admin') || path.startsWith('/settings')
    || path.startsWith('/support') || path.startsWith('/staff') || path.startsWith('/friends')) return 4
  return 0
})
</script>
