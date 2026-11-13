<template>
  <div class="disclaimer" :class="{ 'disclaimer--compact': compact }">
    <van-icon name="info-o" class="disclaimer__icon" />
    <span class="disclaimer__text">
      <slot>
        本量表为国际通用的自评筛查工具，结果仅供参考，不构成医学诊断，也不能替代面诊。<b>如有不适请前往线下医院就医。</b>
      </slot>
    </span>
  </div>
</template>

<script setup>
defineProps({
  /** 单行窄条：塞在卡片底部或标题下面时用 */
  compact: { type: Boolean, default: false }
})
</script>

<style scoped>
.disclaimer {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 12px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 149, 0, 0.1);
  color: var(--van-text-color-2);
  font-size: 12px;
  line-height: 1.6;
}
.disclaimer--compact {
  margin: 8px 0;
  padding: 8px 10px;
  font-size: 11px;
}
.disclaimer__icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 13px;
  color: #ff9500;
}
.disclaimer__text b {
  color: #ff6b35;
  font-weight: 600;
}
</style>
