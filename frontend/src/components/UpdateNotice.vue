<template>
  <van-popup
    :show="visible"
    round
    position="center"
    :style="{ width: '84%', maxWidth: '400px' }"
    :close-on-click-overlay="false"
    @update:show="onUpdateShow"
  >
    <div v-if="showing" class="notice">
      <div class="notice-head">
        <div class="notice-title">{{ historyMode ? '📜 历史更新' : '🎉 更新说明' }}</div>
        <div class="notice-meta">
          {{
            historyMode
              ? `共 ${history.length} 条历史记录`
              : `v${showing.version} · ${showing.date}`
          }}
        </div>
      </div>

      <div class="notice-body" :class="{ 'notice-body--history': historyMode }">
        <template v-if="!historyMode">
          <div
            v-for="(item, i) in items"
            :key="i"
            class="notice-item"
            :class="{ 'notice-item--highlight': item.highlight }"
          >
            <span class="dot">·</span>
            <span>{{ item.text }}</span>
          </div>
        </template>

        <!-- 历史模式：只读往下翻，最新版本在最上面 -->
        <template v-else>
          <div v-for="h in history" :key="h.version" class="history-block">
            <div class="history-head">
              <span class="history-version">v{{ h.version }}</span>
              <span class="history-date">{{ h.date }}</span>
            </div>
            <div
              v-for="(item, i) in normalize(h.items)"
              :key="i"
              class="notice-item"
              :class="{ 'notice-item--highlight': item.highlight }"
            >
              <span class="dot">·</span>
              <span>{{ item.text }}</span>
            </div>
          </div>
        </template>
      </div>

      <div class="notice-foot">
        <van-button v-if="historyMode" round block plain @click="historyMode = false">返回</van-button>
        <van-button v-else round block type="primary" @click="close">我知道了</van-button>
        <div v-if="!historyMode && history.length" class="notice-history" @click="historyMode = true">
          查看历史更新（共 {{ history.length }} 条）&rsaquo;
        </div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { changelog, currentNotice } from '../config/changelog'
import { useUpdateNotice } from '../composables/useUpdateNotice'

const { visible, close } = useUpdateNotice()
const historyMode = ref(false)

const history = computed(() => changelog.entries)
// 当前版本还没写更新说明时不硬造一条，直接落到历史列表
const showing = computed(() => (historyMode.value ? history.value[0] : currentNotice()))
const current = computed(() => currentNotice())

// 条目既可以是字符串，也可以是 { text, highlight: true }，这里统一成对象，模板不用再判断类型
function normalize(list) {
  return (list ?? []).map((item) =>
    typeof item === 'string'
      ? { text: item, highlight: false }
      : { text: item.text, highlight: !!item.highlight }
  )
}
const items = computed(() => normalize(current.value?.items))

// 每次打开都回到「当前版本」，翻到过历史页的残留状态不能带过来
watch(visible, (v) => {
  if (v) historyMode.value = !current.value
})

// 点遮罩不许关，只能点「我知道了」，否则用户会以为是弹窗故障
function onUpdateShow(value) {
  if (value) visible.value = true
  else close()
}
</script>

<style scoped>
.notice {
  padding: 20px 18px 16px;
}
.notice-head {
  text-align: center;
  margin-bottom: 14px;
}
.notice-title {
  font-size: 18px;
  font-weight: 600;
}
.notice-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.notice-body {
  max-height: 46vh;
  overflow-y: auto;
  background: var(--van-background);
  border-radius: 10px;
  padding: 12px 14px;
}
.notice-body--history {
  max-height: 56vh;
}
.notice-item {
  display: flex;
  gap: 6px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--van-text-color);
}
/* 标红条目：本次更新最要紧的那条 */
.notice-item--highlight,
.notice-item--highlight .dot {
  color: var(--van-danger-color);
}
.notice-item--highlight {
  font-weight: 600;
}
.dot {
  color: #1989fa;
  font-weight: 700;
}
.history-block + .history-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--van-border-color, rgba(0, 0, 0, 0.1));
}
.history-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}
.history-version {
  font-weight: 600;
  color: var(--xinyu-accent, #1989fa);
}
.history-date {
  font-size: 12px;
  color: var(--van-text-color-2);
}
.notice-foot {
  margin-top: 16px;
}
.notice-history {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  color: var(--van-primary-color);
  cursor: pointer;
}
</style>
