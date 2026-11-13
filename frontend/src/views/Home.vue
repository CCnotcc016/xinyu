<template>
  <div class="page">
    <van-nav-bar title="心语" />

    <CrisisAlertBanner />
    <ReportAlertBanner />
    <SupportAlertBanner />

    <!-- 宽屏上左右分栏：左边问候语 + 快捷入口，右边最近日记；手机上自动上下堆叠 -->
    <div class="xinyu-cols">
      <div class="home-left">
        <div class="hello xinyu-glass">
          <h2>你好，{{ userStore.user?.nickname || userStore.user?.username }}</h2>
          <p>今天感觉怎么样？</p>
        </div>

        <WeatherCard class="home-weather" compact />

        <van-grid :column-num="4" :border="false" class="quick xinyu-glass">
          <van-grid-item icon="edit" text="写日记" @click="router.push('/diaries/write')" />
          <van-grid-item icon="chart-trending-o" text="情绪趋势" @click="router.push('/trend')" />
          <van-grid-item icon="friends-o" text="匿名树洞" @click="router.push('/treehole')" />
          <van-grid-item icon="notes-o" text="心理自评" @click="router.push('/assessment')" />
        </van-grid>
      </div>

      <div>
        <div class="section-title">最近日记</div>
        <van-skeleton v-if="loading" class="loading-card xinyu-glass" title :row="1" />
        <van-empty
          v-else-if="diaries.length === 0"
          :description="offlineEmpty ? '离线中，这台设备还没缓存到日记' : '还没有日记，去写第一篇吧'"
        />
        <div v-else class="xinyu-cards">
          <van-cell
            v-for="d in diaries"
            :key="d.id"
            :title="preview(d.content)"
            :label="dateLabel(d.createdAt)"
            is-link
            @click="router.push(`/diaries/${d.id}`)"
          >
            <template #value>
              <div class="tags">
                <van-tag v-if="d.pending" plain type="warning">待同步</van-tag>
                <van-tag v-if="d.emotionLabel" type="primary">{{ d.emotionLabel }}</van-tag>
              </div>
            </template>
          </van-cell>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { listDiariesMerged } from '../offline/diary'
import CrisisAlertBanner from '../components/CrisisAlertBanner.vue'
import ReportAlertBanner from '../components/ReportAlertBanner.vue'
import SupportAlertBanner from '../components/SupportAlertBanner.vue'
import WeatherCard from '../components/WeatherCard.vue'

const router = useRouter()
const userStore = useUserStore()
const diaries = ref([])
// 有加载态才不会在数据回来之前先闪一下「还没有日记」
const loading = ref(true)
/** 断网又没有本地副本：空状态要说清楚是没缓存，而不是没写过 */
const offlineEmpty = ref(false)

function preview(text) {
  return text.length > 30 ? text.slice(0, 30) + '…' : text
}
function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : ''
}

async function loadRecent() {
  try {
    // PC 上卡片会排成两列，多取几条才填得满；待同步的本地日记会并到最前面
    const data = await listDiariesMerged({ page: 1, size: 6 }, userStore.user?.id)
    offlineEmpty.value = Boolean(data.offline)
    diaries.value = data.records ?? []
  } catch {
    // 失败就保持空列表，提示由 request.js 统一给出，这里不再叠一层
  } finally {
    loading.value = false
  }
}

onMounted(loadRecent)

// 补传成功后刷新一次：首页那几条「待同步」要换成正式的记录
function onSynced() {
  loadRecent()
}
/** 网络回来了：之前空着是因为断网，重拉一次才看得到内容 */
function onOnline() {
  if (offlineEmpty.value) loadRecent()
}
onMounted(() => {
  window.addEventListener('xinyu:synced', onSynced)
  window.addEventListener('online', onOnline)
})
onUnmounted(() => {
  window.removeEventListener('xinyu:synced', onSynced)
  window.removeEventListener('online', onOnline)
})
</script>

<style scoped>
.hello {
  padding: 18px 16px;
  border-radius: 18px;
}
.hello h2 {
  font-size: 22px;
}
.hello p {
  color: var(--van-text-color-2);
  margin-top: 4px;
}
.quick {
  margin-top: 12px;
  border-radius: 18px;
  overflow: hidden;
}
/* 天气卡插在问候语和快捷入口之间，间距跟快捷入口保持一致 */
.home-weather {
  margin-top: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 4px 8px;
}
/* 宽屏上右边那列的第一行要对齐左列卡片顶部 */
@media (min-width: 1024px) {
  .home-weather,
  .quick {
    margin-top: 16px;
  }
  .section-title {
    margin-top: 0;
  }
}
.loading-card {
  padding: 14px;
  border-radius: 16px;
}

/* 手机上问候语 + 天气 + 快捷入口是连着三块，间距和字号都收一档，
   第一屏才留得住「最近日记」 */
@media (max-width: 767px) {
  .hello {
    padding: 14px;
    border-radius: 16px;
  }
  .hello h2 {
    font-size: 19px;
  }
  .hello p {
    margin-top: 2px;
    font-size: 13px;
  }
  .home-weather,
  .quick {
    margin-top: 9px;
    border-radius: 16px;
  }
  .section-title {
    margin: 14px 4px 6px;
    font-size: 15px;
  }
  .loading-card {
    padding: 12px;
  }
}
.tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
</style>
