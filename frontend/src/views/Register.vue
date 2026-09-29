<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page register">
    <van-nav-bar title="注册" left-arrow @click-left="router.back()" />

    <div class="register__hero">
      <div class="register__brand">心语</div>
      <p class="register__slogan">先把心里的事写下来，剩下的交给时间和 AI</p>
    </div>

    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="3-20 位字母、数字、下划线"
          :rules="[
            { required: true, message: '请输入用户名' },
            { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '格式不正确' }
          ]"
        />
        <van-field
          v-model="form.nickname"
          name="nickname"
          label="昵称"
          placeholder="可选，不能冒用他人姓名"
        />
        <van-field
          v-model="form.phone"
          type="tel"
          name="phone"
          label="手机号"
          maxlength="11"
          placeholder="必填，仅用于紧急情况联系"
          :rules="[
            { required: true, message: '请输入手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }
          ]"
        />
        <van-field
          v-model="form.smsCode"
          name="smsCode"
          label="验证码"
          maxlength="6"
          placeholder="6 位数字"
          :rules="[
            { required: true, message: '请输入验证码' },
            { pattern: /^\d{6}$/, message: '验证码为 6 位数字' }
          ]"
        >
          <template #button>
            <van-button
              size="small"
              round
              plain
              type="primary"
              :disabled="countdown > 0"
              :loading="sending"
              @click.prevent="onSendCode"
            >
              {{ buttonText }}
            </van-button>
          </template>
        </van-field>
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="6-32 位"
          :rules="[
            { required: true, message: '请输入密码' },
            { pattern: /^\S{6,32}$/, message: '密码为 6-32 位且不含空格' }
          ]"
        />
        <van-field
          v-model="form.confirm"
          type="password"
          name="confirm"
          label="确认密码"
          placeholder="再次输入密码"
          :rules="[{ validator: checkConfirm, message: '两次密码不一致' }]"
        />
      </van-cell-group>

      <p class="register__hint">
        手机号用于「树洞出现危机表达时管理员能联系到你」，不会公开展示，
        其他用户只能看到打码后的号码。
      </p>

      <div class="submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          注册
        </van-button>
        <div class="register__login" @click="router.replace('/login')">
          已有账号？<span class="accent">直接登录</span>
        </div>
      </div>
    </van-form>

    <CaptchaPopup
      v-bind="popupProps"
      @confirm="confirm"
      @refresh="refreshCaptcha"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { register } from '../api/auth'
import { useSmsCode } from '../composables/useSmsCode'
import CaptchaPopup from '../components/CaptchaPopup.vue'

const router = useRouter()
const loading = ref(false)
const form = ref({ username: '', nickname: '', phone: '', smsCode: '', password: '', confirm: '' })

const { popupProps, sending, countdown, buttonText, requestCode, refreshCaptcha, confirm } = useSmsCode()

function checkConfirm() {
  return form.value.confirm === form.value.password
}

async function onSendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) {
    showToast('请先填写正确的手机号')
    return
  }
  await requestCode(form.value.phone)
}

async function onSubmit() {
  loading.value = true
  try {
    await register({
      username: form.value.username,
      nickname: form.value.nickname,
      phone: form.value.phone,
      smsCode: form.value.smsCode,
      password: form.value.password
    })
    showToast('注册成功，请登录')
    router.replace('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register__hero {
  margin: 18px 4px 22px;
}
.register__brand {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--xinyu-accent);
}
.register__slogan {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.register__hint {
  margin: 12px 20px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.submit {
  margin: 24px 16px 0;
}
.register__login {
  margin-top: 16px;
  font-size: 13px;
  text-align: center;
  color: var(--van-text-color-2);
}
</style>
