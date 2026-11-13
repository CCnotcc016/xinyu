<template>
  <div class="page">
    <van-nav-bar title="赞助" left-arrow @click-left="router.back()" />

    <!-- 文案直接改这里即可，不需要动样式 -->
    <div class="card xinyu-glass">
      <h2 class="card__title">感谢各位大佬的赞助</h2>
      <p class="card__text">
        感谢各位大佬的赞助，这笔赞助将用于支持项目的持续开发和维护，帮助我们提供更好的服务和体验。如有剩余资金，将用于项目的后续优化和新功能的开发。再次感谢您的支持！
      </p>
      <p class="card__text">
        感谢你们的信任与支持，让我们越来越完善，走的越来越远，做的越来越好。
      </p>
    </div>

    <div class="qr xinyu-glass">
      <div class="qr__box">
        <img class="qr__img" src="/qr/zs.jpg" alt="赞助二维码" />
      </div>
      <p class="qr__tip">长按识别 / 扫码</p>
      <p class="qr__note">扫码后随意金额，一分也是心意。</p>
    </div>

    <p class="foot">赞助完全自愿，不影响任何功能的正常使用；您的赞助将会用于本项目的升级和维护。</p>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.card {
  margin-top: 12px;
  padding: 18px 16px;
  border-radius: 18px;
}
.card__title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
}
.card__text {
  margin: 0 0 8px;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--van-text-color-2);
}
.card__text:last-child {
  margin-bottom: 0;
}
.qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 12px;
  padding: 20px 16px 16px;
  border-radius: 18px;
}
/* 二维码垫一层白底，深色主题下也能扫得出来 */
.qr__box {
  width: 220px;
  height: 220px;
  padding: 10px;
  border-radius: 16px;
  background: #fff;
}
.qr__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.qr__tip {
  margin: 14px 0 0;
  font-size: 13.5px;
  font-weight: 600;
}
.qr__note {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  text-align: center;
  color: var(--van-text-color-2);
}
.foot {
  margin: 16px 6px 8px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--van-text-color-3);
}
</style>
