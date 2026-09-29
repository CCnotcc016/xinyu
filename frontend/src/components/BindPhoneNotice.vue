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
    :close-on-click-overlay="false"
    :style="{ width: '310px' }"
    @update:show="onClose"
  >
    <div class="bind">
      <div class="bind__title">补绑手机号</div>
      <p class="bind__text">
        你是在「注册需要手机号」之前建号的账号。绑定后，万一你在树洞里写下很难受的话，
        管理员才有办法联系到你，而不是只能看着。
      </p>
      <p class="bind__text bind__text--sub">
        手机号不会公开显示，其他用户看到的都是打码后的号码。
      </p>
      <div class="bind__ops">
        <van-button round block plain @click="later">稍后再说</van-button>
        <van-button round block type="primary" @click="goBind">去绑定</van-button>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const KEY = 'xinyu_bind_phone_dismissed'

const router = useRouter()
const userStore = useUserStore()
// 「稍后再说」只在本标签页会话内生效：关掉浏览器再回来会再提醒一次，不打扰得太频繁
const dismissed = ref(sessionStorage.getItem(KEY) === '1')

const show = computed(() => {
  const user = userStore.user
  // 管理员自己不需要（也不该）被提醒；没登录、已绑定、已被忽略的都不弹
  if (!userStore.token || !user || user.role === 'ADMIN') return false
  if (dismissed.value) return false
  return user.phoneBound === false
})

function onClose(v) {
  if (!v) later()
}

function later() {
  dismissed.value = true
  sessionStorage.setItem(KEY, '1')
}

function goBind() {
  later()
  router.push('/settings')
}
</script>

<style scoped>
.bind {
  padding: 22px 18px 18px;
}
.bind__title {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}
.bind__text {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.bind__text--sub {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12px;
}
.bind__ops {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
</style>
