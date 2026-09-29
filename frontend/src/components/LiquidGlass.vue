<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<script setup>
/*
 * 液态玻璃容器：危机弹窗、管理员通知条都用它。
 * 样式写在 assets/main.css 的 .xinyu-liquid* 里（全局），
 * 因为同一套光斑/高光要在两个组件复用，放进 scoped 会各存一份。
 */
</script>

<template>
  <div class="xinyu-liquid">
    <div class="xinyu-liquid__stage" aria-hidden="true">
      <span class="xinyu-liquid__blob xinyu-liquid__blob--a" />
      <span class="xinyu-liquid__blob xinyu-liquid__blob--b" />
      <span class="xinyu-liquid__blob xinyu-liquid__blob--c" />
      <span class="xinyu-liquid__sheen" />
    </div>
    <div class="xinyu-liquid__glass">
      <slot />
    </div>
  </div>
</template>
