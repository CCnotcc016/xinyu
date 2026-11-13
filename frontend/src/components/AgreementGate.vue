<template>
  <van-popup
    :show="visible"
    round
    position="center"
    :style="{ width: '90%', maxWidth: '430px' }"
    :close-on-click-overlay="false"
    :lock-scroll="true"
  >
    <div class="gate">
      <div class="gate__head">
        <div class="gate__title">我们更新了政策</div>
        <div class="gate__meta">v{{ LEGAL_VERSION }} · 生效日期 {{ LEGAL_EFFECTIVE_DATE }}</div>
      </div>

      <p class="gate__lead">
        我们更新了政策条款，请充分阅读、理解后确认。下面是本次变化的要点，读完才能继续使用心语。
      </p>

      <div ref="scroller" class="gate__body" @scroll="onScroll">
        <div
          v-for="(item, i) in LEGAL_CHANGELOG"
          :key="i"
          class="gate__item"
          :class="{ 'gate__item--red': item.highlight }"
        >
          <span class="gate__dot">·</span>
          <span>{{ item.text }}</span>
        </div>

        <div class="gate__links">
          完整文本：
          <a :href="legalHref('terms')" target="_blank" rel="noopener">《用户协议》</a>
          <a :href="legalHref('privacy')" target="_blank" rel="noopener">《隐私政策》</a>
          <a :href="legalHref('disclaimer')" target="_blank" rel="noopener">《免责声明》</a>
        </div>
        <div class="gate__end">— 以上是本次全部变更 —</div>
      </div>

      <div class="gate__foot">
        <van-button round block type="primary" :disabled="!readDone" :loading="saving" @click="confirm">
          {{ readDone ? '我已阅读并同意' : '请先读完上面的内容' }}
        </van-button>
        <div class="gate__quit" @click="onQuit">不同意，先退出登录</div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { LEGAL_CHANGELOG, LEGAL_EFFECTIVE_DATE, LEGAL_VERSION } from '../config/legal'
import { acceptAgreement } from '../api/user'
import { useUserStore } from '../stores/user'
import { logoutAndClear } from '../utils/session'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const scroller = ref(null)
const readDone = ref(false)
const saving = ref(false)

// 严格判断 === false：字段还没从 /user/me 回来时是 undefined，这时不该闪一下弹窗
const visible = computed(
  () =>
    !!userStore.token &&
    !route.meta.public &&
    userStore.user?.agreementAccepted === false
)

watch(visible, async (open) => {
  if (!open) return
  readDone.value = false
  await nextTick()
  // 大屏幕上内容未必撑得满，撑不满就不该逼用户去滑一个滑不动的框
  const el = scroller.value
  if (el && el.scrollHeight <= el.clientHeight + 8) readDone.value = true
})

function onScroll() {
  const el = scroller.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 8) readDone.value = true
}

/** 协议页的真实地址；新开标签页读，免得把弹窗状态冲掉 */
function legalHref(key) {
  return router.resolve({ name: `legal-${key}` }).href
}

async function confirm() {
  saving.value = true
  try {
    // 服务端会再比对一次版本号，客户端拿旧文本点同意是不算数的
    userStore.setUser(await acceptAgreement(LEGAL_VERSION))
    showToast('已记录你的同意')
  } finally {
    saving.value = false
  }
}

async function onQuit() {
  // 不同意的唯一出口就是离开，而不是把弹窗关掉继续用
  await logoutAndClear()
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.gate {
  padding: 22px 18px 18px;
}
.gate__head {
  text-align: center;
}
.gate__title {
  font-size: 18px;
  font-weight: 600;
}
.gate__meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.gate__lead {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--van-text-color-2);
}
.gate__body {
  max-height: 44vh;
  overflow-y: auto;
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--van-background);
  font-size: 13px;
  line-height: 1.8;
}
.gate__item {
  display: flex;
  gap: 6px;
  color: var(--van-text-color-2);
}
.gate__item + .gate__item {
  margin-top: 8px;
}
.gate__dot {
  color: var(--xinyu-accent);
  font-weight: 700;
}
/* 最关键的那条：日记在什么情况下会被管理员看到 */
.gate__item--red,
.gate__item--red .gate__dot {
  color: var(--van-danger-color);
  font-weight: 600;
}
.gate__links {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--van-border-color);
  font-size: 12px;
  line-height: 2;
  color: var(--van-text-color-2);
}
.gate__links a {
  color: var(--xinyu-accent);
  text-decoration: underline;
}
.gate__end {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--van-text-color-3);
}
.gate__foot {
  margin-top: 16px;
}
.gate__quit {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--van-text-color-3);
}
</style>
