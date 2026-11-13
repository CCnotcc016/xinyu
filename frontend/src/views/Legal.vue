<template>
  <div class="page legal">
    <van-nav-bar :title="doc.title" left-arrow @click-left="onBack" />

    <div class="legal__meta">
      <span class="legal__badge">v{{ LEGAL_VERSION }}</span>
      <span>生效日期 {{ LEGAL_EFFECTIVE_DATE }}</span>
    </div>

    <p class="legal__intro xinyu-glass" v-html="render(doc.intro)"></p>

    <section v-for="(section, i) in doc.sections" :key="i" class="legal__section xinyu-glass">
      <h3 class="legal__heading">{{ section.heading }}</h3>
      <template v-for="(block, j) in section.blocks" :key="j">
        <p v-if="block.p" class="legal__p" v-html="render(block.p)"></p>
        <ul v-else-if="block.ul" class="legal__ul">
          <li v-for="(item, k) in block.ul" :key="k" v-html="render(item)"></li>
        </ul>
      </template>
    </section>

    <div class="legal__foot">
      <van-button round block plain type="primary" @click="onBack">返回</van-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { LEGAL_DOCS, LEGAL_EFFECTIVE_DATE, LEGAL_VERSION } from '../config/legal'
import { useUserStore } from '../stores/user'

const props = defineProps({
  docKey: { type: String, required: true }
})

const router = useRouter()
const userStore = useUserStore()

const doc = computed(() => LEGAL_DOCS[props.docKey] ?? LEGAL_DOCS.terms)

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }

/** 文本是我们自己写的，但仍然先转义再拼，避免以后有人往文案里塞富文本时出问题 */
function render(text) {
  if (!text) return ''
  return String(text)
    .replace(/[&<>"]/g, (c) => ESCAPES[c])
    .replace(/\*\*(.+?)\*\*/g, '<span class="legal__red">$1</span>')
}

// 协议可能是从注册页或弹窗里用新标签页打开的，那种情况下没有上一页可回
function onBack() {
  if (window.history.length > 1) router.back()
  else router.replace(userStore.token ? '/' : '/login')
}
</script>

<style scoped>
.legal {
  padding-bottom: 96px;
}
.legal__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 4px 10px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.legal__badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--xinyu-accent-soft);
  color: var(--xinyu-accent);
  font-weight: 600;
}
.legal__intro {
  padding: 14px 16px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.85;
  color: var(--van-text-color);
}
.legal__section {
  margin-top: 12px;
  padding: 16px;
  border-radius: 16px;
}
.legal__heading {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}
.legal__p {
  font-size: 13px;
  line-height: 1.9;
  color: var(--van-text-color-2);
}
.legal__p + .legal__p,
.legal__p + .legal__ul,
.legal__ul + .legal__p {
  margin-top: 8px;
}
.legal__ul {
  margin: 0;
  padding-left: 18px;
}
.legal__ul li {
  font-size: 13px;
  line-height: 1.9;
  color: var(--van-text-color-2);
  list-style: disc;
}
.legal__ul li + li {
  margin-top: 4px;
}
/* 关键条款：只标红真正需要用户停一下看的地方，多了就等于没标 */
.legal__section :deep(.legal__red),
.legal__intro :deep(.legal__red) {
  color: var(--van-danger-color);
  font-weight: 600;
}
.legal__foot {
  margin: 20px 4px 0;
}
</style>
