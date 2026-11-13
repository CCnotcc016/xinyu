<template>
  <div class="page">
    <!-- 首次进入的过场：全屏黑底上先快速闪过几种语言的问候，最后「心语」两个字像墨一样化开。
         放在 body 下是因为 .page 会参与路由切换动画（带 transform），fixed 元素会被它困住。 -->
    <Teleport to="body">
      <transition name="intro">
        <div v-if="introVisible" class="intro" @click="endIntro">
          <span class="intro__glow intro__glow--teal" aria-hidden="true"></span>
          <span class="intro__glow intro__glow--blue" aria-hidden="true"></span>
          <div class="intro__stage">
            <div class="intro__word">
              <transition name="greet" mode="out-in">
                <p v-if="!introSettled" :key="greetIndex" class="intro__greet">{{ greeting }}</p>
              </transition>
              <h1 v-if="introSettled" class="intro__title" aria-label="心语">
                <span>心</span><span>语</span>
              </h1>
            </div>
            <p class="intro__tagline" :class="{ 'intro__tagline--in': introSettled }">
              写下情绪，被温柔接住
            </p>
          </div>
          <span class="intro__skip">轻触跳过</span>
        </div>
      </transition>
    </Teleport>

    <div class="brand">
      <h1>心语</h1>
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
          {{ backendDown ? '离线登录' : '登录' }}
        </van-button>
      </div>
    </van-form>

    <!-- 后端连不上时不再让人干等超时：说清楚状况，并告诉用户还能怎么进去 -->
    <div v-if="backendDown" class="offline">
      <van-icon name="warning-o" class="offline__icon" />
      <div class="offline__body">
        <p class="offline__title">连不上服务器</p>
        <template v-if="offlineAccounts.length">
          <p class="offline__desc">
            后端好像没有启动。用这台设备上登录过的账号直接登录，会以离线方式进入，写下的日记等后端起来后自动同步。
          </p>
          <div class="offline__accounts">
            <button
              v-for="account in offlineAccounts"
              :key="account.username"
              type="button"
              class="offline__account"
              :class="{ 'offline__account--on': form.username === account.username }"
              @click="form.username = account.username"
            >
              {{ account.nickname }}
            </button>
          </div>
        </template>
        <p v-else class="offline__desc">
          后端好像没有启动，这台设备上也没有离线登录记录。首次登录（或换了新手机）需要先启动后端。
        </p>
        <button type="button" class="offline__retry" @click="checkBackend">重试连接</button>
      </div>
    </div>
    <div class="links">
      <router-link to="/register">还没有账号？去注册</router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { login } from '../api/auth'
import { useUserStore } from '../stores/user'
import { useOfflineStore } from '../stores/offline'
import { probeBackend } from '../utils/backend'
import { hasAccount, listAccounts, rememberAccount, verifyAccount } from '../offline/credential'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const offlineStore = useOfflineStore()
const loading = ref(false)
const form = ref({ username: '', password: '' })

/* ---------- 后端可达性 / 离线登录 ---------- */
const backendDown = ref(false)
const offlineAccounts = ref([])
// 挂载时探一次 /auth/ping：3 秒没回应就当后端没开，省下 30 秒的「请求超时」

async function checkBackend() {
  const alive = await probeBackend()
  backendDown.value = !alive
  if (alive) return
  offlineAccounts.value = listAccounts()
  // 本机只有一个离线账号时直接填进输入框，省得用户去回忆当时用的是哪个
  if (!form.value.username && offlineAccounts.value.length === 1) {
    form.value.username = offlineAccounts.value[0].username
  }
}

/* ---------- 首次进入的过场动画 ---------- */
const INTRO_KEY = 'xinyu:intro:v1'
const GREETINGS = ['hello', '你好', 'こんにちは', '안녕하세요', 'bonjour', 'hola', 'ciao']

function shouldPlayIntro() {
  // 带上 ?intro=1 可以强制重看一遍，演示的时候不用去清 localStorage
  if (route.query.intro === '1') return true
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return false
  try {
    return !localStorage.getItem(INTRO_KEY)
  } catch {
    // 隐私模式下 localStorage 会抛错，那就干脆不播，别把登录卡住
    return false
  }
}

const introVisible = ref(shouldPlayIntro())
const introSettled = ref(false)
const greetIndex = ref(0)
const greeting = computed(() => GREETINGS[greetIndex.value])
let introTimers = []

function later(fn, delay) {
  introTimers.push(setTimeout(fn, delay))
}

function clearIntroTimers() {
  introTimers.forEach(clearTimeout)
  introTimers = []
}

function stepGreeting() {
  if (greetIndex.value >= GREETINGS.length - 1) {
    // 问候闪完，换成正式的「心语」，标题自己会播放墨水化开的动画
    introSettled.value = true
    later(endIntro, 1900)
    return
  }
  greetIndex.value += 1
  // 每种语言停 1.2s：切换过程本身要 0.18s（淡出 + 淡入），
  // 所以每种语言完整可读的时间在 1s 以上，不会一闪而过。
  later(stepGreeting, 1200)
}

function endIntro() {
  clearIntroTimers()
  introVisible.value = false
}

watch(introVisible, (visible) => {
  // 过场期间锁住滚动，否则手机上滑动会把后面的登录表单带着走
  document.body.style.overflow = visible ? 'hidden' : ''
}, { immediate: true })

onMounted(() => {
  // 探测与开场动画并行，动画放完时提示已经就绪
  checkBackend()
  if (!introVisible.value) return
  try {
    localStorage.setItem(INTRO_KEY, '1')
  } catch {
    /* 写不进去就算了，下次还能再看一遍 */
  }
  later(stepGreeting, 1200)
})

onBeforeUnmount(() => {
  clearIntroTimers()
  document.body.style.overflow = ''
})

function enterApp(user, redirect) {
  // 首个管理员由环境变量引导创建，带 must_change_password：先把初始密码改掉再进应用
  if (user?.mustChangePassword) {
    showToast('请先修改初始密码')
    router.replace({ name: 'settings', query: { forcePassword: '1' } })
    return
  }
  // 客服账号没有日记和树洞，直接落到工作台；管理员照旧进首页（首页有后台入口）
  if (user?.role === 'STAFF') {
    router.replace('/staff')
    return
  }
  router.replace(redirect || '/')
}

async function onSubmit() {
  loading.value = true
  try {
    if (backendDown.value) {
      await offlineLogin()
      return
    }
    const data = await login(form.value)
    userStore.setLogin(data.token, data.user)
    // 记一份本机凭证：下次后端没开也能用同样的账号密码进来
    rememberAccount(form.value.username, form.value.password, data.token, data.user)
    showToast('登录成功')
    enterApp(data.user, route.query.redirect || '/')
  } catch (error) {
    // 探测时后端还活着、真登录时却连不上了（刚好被关掉）：给它一次离线登录的机会
    if (error?.offline) {
      backendDown.value = true
      offlineAccounts.value = listAccounts()
      await offlineLogin()
      return
    }
    // 剩下的都是业务错误（密码错、锁定、验证码…），request.js 已经提示过了
  } finally {
    loading.value = false
  }
}

/**
 * 离线登录：用本机保存过的账号进入，不访问服务器。
 * 界面照常可用（日记先存本地队列），联网后 OfflineBanner 会自动补传。
 */
async function offlineLogin() {
  const { username, password } = form.value
  if (!hasAccount(username)) {
    showToast('这台设备上没有这个账号的登录记录，先启动后端登录一次')
    return
  }
  const record = await verifyAccount(username, password)
  if (!record) {
    showToast('密码不对，离线登录失败')
    return
  }
  if (!record.token) {
    showToast('本机的登录状态已失效，需要先启动后端重新登录一次')
    return
  }
  userStore.setLogin(record.token, record.user)
  // 已经确认连不上，别等第一个请求失败才切到离线态
  offlineStore.online = false
  showToast('已离线登录，写下的日记会在联网后自动同步')
  enterApp(record.user, route.query.redirect || '/')
}
</script>

<style scoped>
/* ---------- 首次进入的过场 ----------
 * 参考 iPhone 第一次开机时的 hello：黑底、几乎没有装饰，全靠字自己动。
 * 这里把「hello」换成几种语言的问候快速闪过，最后落在「心语」两个字上。
 */
.intro {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #f2fbfa;
  background: radial-gradient(140% 120% at 50% -10%, #123038 0%, #0a1618 44%, #050b0c 100%);
  overscroll-behavior: contain;
  -webkit-tap-highlight-color: transparent;
}

/* 两块很大的柔光，慢慢呼吸；它们只负责给黑底一点深度，不能抢字 */
.intro__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.intro__glow--teal {
  width: 78vmax;
  height: 78vmax;
  top: -30vmax;
  left: -26vmax;
  background: radial-gradient(closest-side, rgba(13, 148, 136, 0.55), transparent 70%);
  animation: intro-breathe 7s ease-in-out infinite;
}

.intro__glow--blue {
  width: 66vmax;
  height: 66vmax;
  right: -24vmax;
  bottom: -26vmax;
  background: radial-gradient(closest-side, rgba(59, 130, 246, 0.32), transparent 70%);
  animation: intro-breathe 9s ease-in-out infinite reverse;
}

@keyframes intro-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.8;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}

.intro__stage {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
  text-align: center;
}

/* 问候和标题共用同一个格子，切换时不会把下面的副标题顶来顶去 */
.intro__word {
  display: grid;
  place-items: center;
  min-height: 1.15em;
}

.intro__greet,
.intro__title {
  grid-area: 1 / 1;
}

.intro__greet {
  font-size: clamp(34px, 11vw, 64px);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.greet-enter-active,
.greet-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease, filter 0.18s ease;
}

.greet-enter-from {
  opacity: 0;
  filter: blur(10px);
  transform: translateY(6px) scale(0.98);
}

.greet-leave-to {
  opacity: 0;
  filter: blur(10px);
  transform: translateY(-6px) scale(1.02);
}

.intro__title {
  display: flex;
  gap: 0.04em;
  font-size: clamp(64px, 22vw, 128px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.02em;
}

/* 每个字从一团模糊里化开并回缩到正常大小 —— 汉字笔画太碎，
 * 做逐笔描边反而会糊，模糊→清晰的「墨水晕开」更接近手写落纸的感觉。 */
.intro__title span {
  display: inline-block;
  opacity: 0;
  filter: blur(22px);
  transform: scale(1.22);
  animation: intro-ink 1.3s cubic-bezier(0.16, 0.9, 0.3, 1) forwards;
}

.intro__title span:nth-child(2) {
  animation-delay: 0.36s;
}

@keyframes intro-ink {
  60% {
    opacity: 1;
  }
  100% {
    opacity: 1;
    filter: blur(0);
    transform: scale(1);
  }
}

.intro__tagline {
  /* 字距拉开之后整行会偏右，用 text-indent 补回视觉居中 */
  margin-top: 24px;
  font-size: 14px;
  letter-spacing: 0.34em;
  text-indent: 0.34em;
  color: rgba(226, 248, 245, 0.62);
  opacity: 0;
  transform: translateY(8px);
  transition: opacity 0.85s ease 0.7s, transform 0.85s ease 0.7s;
}

.intro__tagline--in {
  opacity: 1;
  transform: none;
}

.intro__skip {
  position: absolute;
  bottom: calc(28px + env(safe-area-inset-bottom));
  font-size: 12px;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.32);
  animation: intro-skip-in 1s ease 1.4s backwards;
}

@keyframes intro-skip-in {
  from {
    opacity: 0;
  }
}

/* 退场：放大 + 模糊，像镜头拉开，比单纯淡出更有「过场结束」的意味 */
.intro-leave-active {
  transition: opacity 0.6s ease, transform 0.6s ease, filter 0.6s ease;
}

.intro-leave-to {
  opacity: 0;
  transform: scale(1.06);
  filter: blur(8px);
}

.brand {
  text-align: center;
  /* 去掉副标题之后下面会空出一截，这里把留白收紧一点，让表单往上靠 */
  padding: 52px 0 26px;
}
.brand h1 {
  font-size: 34px;
  color: var(--xinyu-accent);
}
.submit {
  margin: 24px 16px 0;
}
.offline {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 16px 16px 0;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(180, 83, 9, 0.08);
  border: 1px solid rgba(180, 83, 9, 0.22);
  color: #b45309;
  font-size: 12px;
  line-height: 1.6;
}
.offline__icon {
  font-size: 15px;
  margin-top: 2px;
}
.offline__body {
  flex: 1;
  min-width: 0;
}
.offline__title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 2px;
}
.offline__desc {
  color: var(--van-text-color-2);
}
.offline__accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.offline__account {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(180, 83, 9, 0.3);
  background: transparent;
  color: #b45309;
  font-size: 12px;
}
.offline__account--on {
  background: rgba(180, 83, 9, 0.14);
  font-weight: 600;
}
.offline__retry {
  margin-top: 8px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--xinyu-accent);
  font-size: 12px;
  text-decoration: underline;
}
.links {
  text-align: center;
  margin-top: 16px;
}
.links a {
  color: var(--xinyu-accent);
  font-size: 14px;
}
</style>
