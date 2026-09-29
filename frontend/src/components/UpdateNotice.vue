<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <van-popup
    :show="visible"
    round
    position="center"
    :style="{ width: '84%', maxWidth: '400px' }"
    :close-on-click-overlay="false"
    @update:show="onUpdateShow"
  >
    <div v-if="notice" class="notice">
      <div class="notice-head">
        <div class="notice-title">🎉 更新说明</div>
        <div class="notice-meta">v{{ notice.version }} · {{ notice.date }}</div>
      </div>

      <div class="notice-body">
        <div v-for="(item, i) in notice.items" :key="i" class="notice-item">
          <span class="dot">·</span>
          <span>{{ item }}</span>
        </div>
      </div>

      <div class="notice-foot">
        <van-button round block type="primary" @click="close">我知道了</van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed } from 'vue'
import { currentNotice } from '../config/changelog'
import { useUpdateNotice } from '../composables/useUpdateNotice'

const notice = computed(() => currentNotice())
const { visible, close } = useUpdateNotice()

// 点遮罩不许关，只能点「我知道了」，否则用户会以为是弹窗故障
function onUpdateShow(value) {
  if (value) visible.value = true
  else close()
}
</script>

<style scoped>
.notice {
  padding: 20px 18px 16px;
}
.notice-head {
  text-align: center;
  margin-bottom: 14px;
}
.notice-title {
  font-size: 18px;
  font-weight: 600;
}
.notice-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.notice-body {
  max-height: 46vh;
  overflow-y: auto;
  background: var(--van-background);
  border-radius: 10px;
  padding: 12px 14px;
}
.notice-item {
  display: flex;
  gap: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--van-text-color);
}
.dot {
  color: #1989fa;
  font-weight: 700;
}
.notice-foot {
  margin-top: 16px;
}
</style>
