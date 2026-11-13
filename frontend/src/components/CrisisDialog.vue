<template>
  <van-popup
    :show="show"
    round
    position="center"
    class="crisis-popup"
    overlay-class="crisis-overlay"
    :close-on-click-overlay="true"
    @update:show="(v) => emit('update:show', v)"
  >
    <LiquidGlass>
      <div class="crisis">
        <div class="crisis__badge">❤</div>
        <div class="crisis__title">我们关心你</div>
        <p class="crisis__lead">
          你写下的这些，我们看到了。此刻的感受很重要，你不必一个人扛着。
        </p>

        <ul class="crisis__list">
          <li>先联系一个你信任的人，哪怕只说一句「我今天很难受」</li>
          <li>心理援助热线 <a class="accent" href="tel:12356">12356</a>，24 小时有人接听</li>
          <li>情况紧急，请立刻拨打 <a class="accent" href="tel:120">120</a> 或
            <a class="accent" href="tel:110">110</a></li>
        </ul>

        <p class="crisis__note">
          这里是记录情绪的地方，不能替代专业帮助。
          <template v-if="privateChat">
            这段对话只有你们两个人能看到，所以不会有人来打扰你；
            如果想让身边的人知道，主动开口说一句就好。
          </template>
          <template v-else>
            管理员已收到提示，如果有人可以联系到你，我们愿意搭把手。
          </template>
        </p>

        <div class="crisis__ops">
          <van-button round block plain @click="emit('update:show', false)">我知道了</van-button>
          <van-button round block type="primary" @click="callHotline">拨打 12356</van-button>
        </div>
      </div>
    </LiquidGlass>
  </van-popup>
</template>

<script setup>
import LiquidGlass from './LiquidGlass.vue'

defineProps({
  show: { type: Boolean, default: false },
  /** 私聊里触发：不产生管理员预警，文案也要说清楚「没有人会看到」 */
  privateChat: { type: Boolean, default: false }
})
const emit = defineEmits(['update:show'])

function callHotline() {
  window.location.href = 'tel:12356'
}
</script>

<style scoped>
/*
 * 类名写两遍是为了压过 Vant 的 .van-popup--round：
 * 两者权重相同时只看样式注入顺序，这里不赌顺序。
 */
.crisis-popup.crisis-popup {
  width: min(92vw, 380px);
  border-radius: 26px;
  /* 玻璃自己会画底，弹窗别再叠一层，也别嵌套 backdrop-filter（Safari 下会失效） */
  background: transparent;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  /* 投影挂在弹窗上：玻璃被弹窗裁切，它自己的阴影露不出来 */
  box-shadow: var(--xinyu-liquid-shadow);
}

.crisis {
  padding: 24px 18px 18px;
  text-align: left;
}
.crisis__badge {
  width: 46px;
  height: 46px;
  margin: 0 auto;
  border-radius: 50%;
  background: var(--xinyu-accent-soft);
  color: var(--xinyu-accent);
  font-size: 22px;
  line-height: 46px;
  text-align: center;
}
.crisis__title {
  margin: 12px 0 8px;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}
.crisis__lead {
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.crisis__list {
  margin: 12px 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
}
.crisis__note {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--xinyu-accent-soft);
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
.crisis__ops {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
</style>
