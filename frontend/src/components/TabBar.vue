<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <nav class="xinyu-tabbar">
    <div class="xinyu-tabbar__inner">
      <!-- 药丸指示器跟着选中项滑动，配合图标缩放做切换动画 -->
      <span class="xinyu-tabbar__pill" :style="{ transform: `translateX(${activeIndex * 100}%)` }" />
      <router-link
        v-for="(item, index) in items"
        :key="item.to"
        class="xinyu-tabbar__item"
        :class="{ active: index === activeIndex }"
        :to="item.to"
        replace
      >
        <van-icon :name="index === activeIndex ? item.activeIcon : item.icon" />
        <span>{{ item.label }}</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

// 用 van-tabbar 时做不到「指示器滑动 + 图标回弹」，所以自己实现一个：
// 结构简单，样式在 assets/main.css 的 .xinyu-tabbar* 里（毛玻璃 + 动画）。
const items = [
  { to: '/', label: '首页', icon: 'home-o', activeIcon: 'wap-home' },
  { to: '/diaries', label: '日记', icon: 'notes-o', activeIcon: 'notes' },
  { to: '/treehole', label: '树洞', icon: 'friends-o', activeIcon: 'friends' },
  { to: '/profile', label: '我的', icon: 'user-o', activeIcon: 'user-circle-o' }
]

const route = useRoute()

// 树洞详情、日记详情等子页面也算在所属 tab 上，导航栏选中状态才不会跳
const activeIndex = computed(() => {
  const path = route.path
  if (path === '/') return 0
  if (path.startsWith('/dai') || path.startsWith('/trend')) return 1
  if (path.startsWith('/treehole')) return 2
  if (path.startsWith('/profile') || path.startsWith('/admin') || path.startsWith('/settings')) return 3
  return 0
})
</script>
