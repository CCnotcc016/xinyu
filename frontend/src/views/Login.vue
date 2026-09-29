<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <div class="brand">
      <h1>心语</h1>
      <p>写下情绪，被温柔接住</p>
    </div>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          :rules="[{ required: true, message: '请输入用户名' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>
      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登录
        </van-button>
      </div>
    </van-form>
    <div class="links">
      <router-link to="/register">还没有账号？去注册</router-link>
    </div>
    <p class="copyright">心语 Xinyu · © 2026 CCnotcc016<br />仅供交流学习使用，禁止商业售卖</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { login } from '../api/auth'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = ref({ username: '', password: '' })

async function onSubmit() {
  loading.value = true
  try {
    const data = await login(form.value)
    userStore.setLogin(data.token, data.user)
    showToast('登录成功')
    router.replace(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.brand {
  text-align: center;
  padding: 48px 0 32px;
}
.brand h1 {
  font-size: 34px;
  color: var(--xinyu-accent);
}
.brand p {
  margin-top: 8px;
  color: var(--van-text-color-2);
}
.submit {
  margin: 24px 16px 0;
}
.links {
  text-align: center;
  margin-top: 16px;
}
.links a {
  color: var(--xinyu-accent);
  font-size: 14px;
}
.copyright {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 16px;
  text-align: center;
  font-size: 11px;
  line-height: 1.6;
  color: var(--van-text-color-3, #969799);
  opacity: 0.75;
}
</style>
