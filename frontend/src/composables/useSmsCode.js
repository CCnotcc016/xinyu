// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { computed, onUnmounted, ref } from 'vue'
import { showToast } from 'vant'
import { getCaptcha, sendSmsCode } from '../api/auth'

/**
 * 「图形验证码 -> 短信验证码」的完整流程，注册页和更换手机号页共用。
 *
 * 顺序不能再省：短信有成本、也会被人拿去骚扰别人，所以先过一次图形验证码。
 * 图形验证码只放内存（无 Redis 时也照样能用），消费一次即失效。
 *
 * 用法：
 *   const sms = useSmsCode()
 *   sms.requestCode(phone)                  // 打开图形验证码弹窗
 *   <CaptchaPopup v-bind="sms.popupProps" @confirm="sms.confirm" @refresh="sms.refreshCaptcha" />
 *   sms.countdown / sms.buttonText          // 按钮倒计时
 */
export function useSmsCode() {
  const showCaptcha = ref(false)
  const captcha = ref(null)
  const captchaLoading = ref(false)
  const sending = ref(false)
  const countdown = ref(0)
  const pendingPhone = ref('')

  let timer = null

  const popupProps = computed(() => ({
    show: showCaptcha.value,
    image: captcha.value?.image || '',
    devCode: captcha.value?.code || '',
    loading: sending.value,
    'onUpdate:show': (v) => {
      showCaptcha.value = v
    }
  }))

  const buttonText = computed(() =>
    countdown.value > 0 ? `${countdown.value}s 后重发` : '获取验证码'
  )

  async function loadCaptcha() {
    captchaLoading.value = true
    try {
      captcha.value = await getCaptcha()
    } finally {
      captchaLoading.value = false
    }
  }

  /** 点「获取验证码」：先拉一张图形验证码并弹窗 */
  async function requestCode(phone) {
    if (countdown.value > 0) return
    pendingPhone.value = phone
    await loadCaptcha()
    showCaptcha.value = true
  }

  async function refreshCaptcha() {
    await loadCaptcha()
  }

  /** 用户在弹窗里填完图形验证码后确认 */
  async function confirm(captchaCode) {
    sending.value = true
    try {
      const res = await sendSmsCode({
        phone: pendingPhone.value,
        captchaId: captcha.value?.captchaId,
        captchaCode
      })
      showCaptcha.value = false
      startCountdown(res?.resendAfter || 60)
      // 本地开发没有真实短信通道，后端会把验证码回显回来，这里直接提示
      if (res?.code) {
        showToast(`开发模式验证码：${res.code}`, { duration: 6000 })
      } else {
        showToast('验证码已发送，请注意查收')
      }
      return true
    } catch (e) {
      // 答案错误时换一张，防止连续猜同一张图
      if (e?.code === 1016 || !e?.handled) await refreshCaptcha()
      return false
    } finally {
      sending.value = false
    }
  }

  function startCountdown(seconds) {
    stopCountdown()
    countdown.value = seconds
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) stopCountdown()
    }, 1000)
  }

  function stopCountdown() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    countdown.value = 0
  }

  onUnmounted(stopCountdown)

  return {
    popupProps,
    captcha,
    captchaLoading,
    sending,
    countdown,
    buttonText,
    requestCode,
    refreshCaptcha,
    confirm,
    stopCountdown
  }
}
