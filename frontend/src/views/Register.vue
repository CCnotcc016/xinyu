<template>
  <div class="page register">
    <van-nav-bar title="注册" left-arrow @click-left="router.back()" />

    <div class="register__hero">
      <div class="register__brand">心语</div>
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
          placeholder="8-32 位，含字母和数字"
          :rules="[
            { required: true, message: '请输入密码' },
            { validator: checkPasswordRule }
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

      <!-- 用 a + target="_blank" 而不是 router.push：注册表单填了一半，
           跳走再回来会全清空，这是必须保住用户输入的地方 -->
      <van-checkbox v-model="agreed" class="register__agree" icon-size="16px">
        <span class="register__agree-text">
          我已阅读并同意
          <a :href="legalHref('terms')" target="_blank" rel="noopener" @click.stop>《用户协议》</a>
          <a :href="legalHref('privacy')" target="_blank" rel="noopener" @click.stop>《隐私政策》</a>
          <a :href="legalHref('disclaimer')" target="_blank" rel="noopener" @click.stop>《免责声明》</a>
        </span>
      </van-checkbox>

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
import { checkPassword } from '../utils/password'

const router = useRouter()
const loading = ref(false)
// 同意是注册的前置条件，后端也会校验（RegisterRequest.agreementAccepted），这里只是提前拦一下
const agreed = ref(false)
const form = ref({ username: '', nickname: '', phone: '', smsCode: '', password: '', confirm: '' })

const { popupProps, sending, countdown, buttonText, requestCode, refreshCaptcha, confirm } = useSmsCode()

/** 协议页的真实地址，交给 router 拼，免得改了路由或 base 之后链接失效 */
function legalHref(key) {
  return router.resolve({ name: `legal-${key}` }).href
}

// Vant 4 的 validator 返回字符串时，会把该字符串直接显示在字段下方
function checkPasswordRule() {
  return checkPassword(form.value.password, form.value.username) || true
}

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
  if (!agreed.value) {
    showToast('请先阅读并同意用户协议与隐私政策')
    return
  }
  loading.value = true
  try {
    await register({
      username: form.value.username,
      nickname: form.value.nickname,
      phone: form.value.phone,
      smsCode: form.value.smsCode,
      password: form.value.password,
      agreementAccepted: true
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
.register__agree {
  margin: 16px 20px 0;
  align-items: flex-start;
}
.register__agree-text {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.register__agree-text a {
  color: var(--xinyu-accent);
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
