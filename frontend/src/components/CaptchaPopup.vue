<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <van-popup
    :show="show"
    round
    position="center"
    :style="{ width: '300px' }"
    @update:show="(v) => emit('update:show', v)"
  >
    <div class="captcha">
      <div class="captcha__title">人机验证</div>
      <p class="captcha__tip">请输入下图中的 4 位字符，防止短信被恶意刷取</p>

      <div class="captcha__row">
        <van-field
          v-model="code"
          class="captcha__input"
          maxlength="4"
          placeholder="不区分大小写"
          @keyup.enter="onConfirm"
        />
        <img
          v-if="image"
          class="captcha__img"
          :src="image"
          alt="点击刷新"
          title="点击刷新"
          @click="emit('refresh')"
        />
        <div v-else class="captcha__img captcha__img--empty" @click="emit('refresh')">加载中</div>
      </div>

      <div v-if="devCode" class="captcha__dev">开发模式答案：{{ devCode }}</div>

      <div class="captcha__ops">
        <van-button round block size="small" plain @click="emit('refresh')">换一张</van-button>
        <van-button round block size="small" type="primary" :loading="loading" @click="onConfirm">
          确定
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  image: { type: String, default: '' },
  /** 仅开发环境后端回显，方便本地演示；生产环境为空 */
  devCode: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['update:show', 'confirm', 'refresh'])

const code = ref('')

// 每次打开都清空上一次的输入，避免用户以为已经填好了
watch(
  () => props.show,
  (v) => {
    if (v) code.value = ''
  }
)

function onConfirm() {
  const value = code.value.trim()
  if (value.length < 4) return
  emit('confirm', value)
}
</script>

<style scoped>
.captcha {
  padding: 20px 16px 18px;
}
.captcha__title {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}
.captcha__tip {
  margin: 6px 0 14px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
  text-align: center;
}
.captcha__row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.captcha__input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--van-border-color);
  border-radius: 8px;
}
.captcha__img {
  width: 108px;
  height: 38px;
  border-radius: 6px;
  object-fit: cover;
  cursor: pointer;
  background: var(--van-background-2);
}
.captcha__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.captcha__dev {
  margin-top: 8px;
  font-size: 12px;
  color: var(--xinyu-accent);
  text-align: center;
}
.captcha__ops {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
</style>
