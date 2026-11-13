<template>
  <div class="wx xinyu-glass" :class="{ 'wx--compact': compact }">
    <!--
      未授权 / 拿不到天气：统一成一张有标题、有说明、有真按钮的卡。
      以前这两个分支是单行小字，手机上只有 58px，看起来像分隔条，用户不知道能点。
    -->
    <div v-if="promo" class="wx__promo" @click="onPromoClick">
      <div class="wx__promo-head">
        <span class="wx__emoji wx__emoji--big">{{ promo.icon }}</span>
        <div class="wx__cta-text">
          <div class="wx__title">{{ promo.title }}</div>
          <div class="wx__sub">{{ promo.sub }}</div>
        </div>
      </div>
      <div class="wx__promo-actions">
        <van-button
          v-for="act in promo.actions"
          :key="act.label"
          size="small"
          round
          block
          :type="act.type === 'primary' ? 'primary' : 'default'"
          :plain="act.type !== 'primary'"
          :loading="!!act.loading && locating"
          @click.stop="act.run()"
        >
          {{ act.label }}
        </van-button>
      </div>
      <div v-if="promo.https" class="wx__https" @click.stop="openHttps">
        <van-icon name="lock" />
        <span class="wx__https-text">手机想用定位？改用 https 打开</span>
        <van-icon name="arrow" class="wx__https-arrow" />
      </div>
    </div>

    <van-skeleton v-else-if="status === 'loading'" title :row="2" class="wx__skeleton" />

    <template v-else>
      <div class="wx__main">
        <span class="wx__emoji wx__emoji--big">{{ info.icon }}</span>
        <div class="wx__body">
          <div class="wx__temp">
            {{ weather.temp }}<small>°C</small>
            <span class="wx__desc">{{ info.text }}</span>
          </div>
          <div class="wx__place">
            <van-icon name="location-o" />
            {{ shortPlace }}
            <span class="wx__range">{{ weather.min }}° ~ {{ weather.max }}° · 体感 {{ weather.apparent }}°</span>
          </div>
        </div>
        <van-icon name="replay" class="wx__refresh" @click="reload" />
      </div>
      <div class="wx__tip">{{ info.tip }}</div>
    </template>

    <!-- 授权说明：先讲清用途，再决定要不要给 -->
    <van-popup v-model:show="showConsent" position="bottom" round class="wx-sheet">
      <div class="wx-sheet__body">
        <div class="wx-sheet__title">开启今日情绪天气</div>
        <ul class="wx-sheet__list">
          <li><b>用途</b>：在首页显示你所在地的实时天气，并写进日记时帮你填好天气。</li>
          <li>
            <b>位置去向</b>：坐标只存在这台设备上，<em>不会上传到心语服务器</em>，
            只用于向 Open-Meteo 查询天气、向 BigDataCloud 查询城市名。
          </li>
          <li><b>随时可关</b>：在「我的 - 隐私与授权」里关闭后，本地坐标与天气缓存会立刻删除。</li>
        </ul>
        <div v-if="!securePage" class="wx-sheet__note">
          当前是 http 打开，手机自带浏览器（华为、小米等）会禁止网页定位。
          可以先手动选城市，或改用 https 地址访问。
        </div>
        <div v-if="!securePage" class="wx-sheet__https" @click="openHttps">
          改用 {{ httpsUrl }} 打开可解锁定位（自签证书，选择继续访问即可）<van-icon name="arrow" />
        </div>
        <van-button v-if="securePage" type="primary" block round :loading="locating" @click="useLocation">
          使用我的位置
        </van-button>
        <van-button v-if="securePage" plain block round class="wx-sheet__ghost" @click="openCityPicker">
          手动选择城市（不用定位）
        </van-button>
        <!-- http 页面点定位必定失败，所以这里把手选城市放在主位 -->
        <van-button v-if="!securePage" type="primary" block round @click="openCityPicker">
          手动选择城市（不用定位）
        </van-button>
        <van-button
          v-if="!securePage"
          plain
          block
          round
          class="wx-sheet__ghost"
          :loading="locating"
          @click="useLocation"
        >
          仍要尝试使用我的位置
        </van-button>
        <div class="wx-sheet__foot">
          继续即表示同意
          <a @click="openLegal('privacy')">《隐私政策》</a>
          中关于位置信息的部分
        </div>
      </div>
    </van-popup>

    <!-- 定位失败：说清楚是哪一步卡住了，再给一条走得通的路 -->
    <van-popup v-model:show="showGeoFail" position="bottom" round class="wx-sheet">
      <div class="wx-sheet__body">
        <div class="wx-sheet__title">{{ geoHint.title }}</div>
        <p class="wx-sheet__desc">{{ geoHint.tip }}</p>
        <van-button type="primary" block round :loading="locating" @click="useLocation">
          再试一次
        </van-button>
        <van-button plain block round class="wx-sheet__ghost" @click="openCityPicker">
          手动选择城市
        </van-button>
        <van-button
          v-if="!securePage && geoReason === 'insecure'"
          plain
          block
          round
          class="wx-sheet__ghost"
          @click="openHttps"
        >
          改用 https 地址（解锁定位）
        </van-button>
      </div>
    </van-popup>

    <!-- 手选城市：给不方便开定位的人留的路 -->
    <van-popup v-model:show="showCity" position="bottom" round class="wx-sheet">
      <div class="wx-sheet__body">
        <div class="wx-sheet__title">选择城市</div>
        <van-search v-model="keyword" placeholder="输入城市名，如 杭州" @search="doSearch" />
        <div class="wx-sheet__hint">定位用不了也没关系，手选城市一样能看天气。</div>
        <van-loading v-if="searching" class="wx-sheet__loading" size="20">查询中</van-loading>
        <van-empty v-else-if="searched && !results.length" description="没找到这个城市" image-size="70" />
        <div v-else class="wx-sheet__cities">
          <van-cell v-for="item in results" :key="item.label" :title="item.label" is-link @click="chooseCity(item)" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { geoHintFor, useWeather, weatherInfo } from '../composables/useWeather'

const props = defineProps({
  /** 窄卡片（PC 左栏）时收掉次要信息 */
  compact: { type: Boolean, default: false }
})

const router = useRouter()
const {
  consent,
  status,
  error,
  weather,
  place,
  hasCoords,
  securePage,
  load,
  setCoords,
  requestLocation,
  guessCity,
  searchCity,
  setConsent,
  notifyDaily
} = useWeather()

const showConsent = ref(false)
const showCity = ref(false)
const showGeoFail = ref(false)
const geoReason = ref('')
const locating = ref(false)
const keyword = ref('')
const results = ref([])
const searching = ref(false)
const searched = ref(false)

const info = computed(() => weatherInfo(weather.value?.code ?? 0, weather.value?.isDay ?? 1))
// 卡片上只留城市名：手选城市存的是「杭州 · 浙江 · 中国」，全塞进去太挤
const shortPlace = computed(() => place.value.split(' · ')[0] || '当前位置')
const geoHint = computed(() => geoHintFor(geoReason.value))
// 本机 nginx 的 https 入口，只在 http 页面下才提（见 nginx-8443-ssl.conf）
const httpsUrl = computed(() => (/^https:$/.test(window.location.protocol) ? '' : `https://${window.location.hostname}:8443`))

/**
 * 未授权、以及「拿不到天气」时统一走这张卡：标题 + 一句能看懂的解释 + 真按钮。
 * 手机上用 http 打开时定位必定失败（浏览器策略），那种情况下把「手动选择城市」放主位，
 * 不给用户一个点了必然失败的按钮。
 */
const promo = computed(() => {
  // 已授权、而且正在加载或已经拿到天气时，走正常的天气卡
  if (consent.value && (status.value === 'loading' || (status.value === 'ready' && weather.value))) return null
  if (!consent.value) {
    return {
      icon: '🌤️',
      title: '今日情绪天气',
      sub: securePage.value
        ? '按所在地显示天气，位置不会上传到心语'
        : '当前是 http 打开，手机浏览器禁止网页定位，手选城市一样能看天气',
      actions: securePage.value
        ? [
            { label: '使用我的位置', type: 'primary', loading: true, run: useLocation },
            { label: '手动选择城市', type: 'plain', run: openCityPicker }
          ]
        : [{ label: '手动选择城市（不用定位）', type: 'primary', run: openCityPicker }],
      https: !securePage.value
    }
  }
  if (!hasCoords.value) {
    return {
      icon: '🧭',
      title: '选个城市看天气',
      sub: securePage.value
        ? '还没拿到位置，手动选一次城市就好，位置同样不会上传'
        : '定位没成功：http 页面手机浏览器不给定位权限，手选城市一样能看天气',
      actions: securePage.value
        ? [
            { label: '手动选择城市', type: 'primary', run: openCityPicker },
            { label: '重新定位', type: 'plain', loading: true, run: useLocation }
          ]
        : [{ label: '手动选择城市', type: 'primary', run: openCityPicker }],
      https: !securePage.value
    }
  }
  return {
    icon: '🌫️',
    title: '暂时拿不到天气',
    sub: error.value || '天气接口刚才没响应，稍等一下再试',
    actions: [
      { label: '重试', type: 'primary', run: reload },
      { label: '换个城市', type: 'plain', run: openCityPicker }
    ],
    https: false
  }
})

onMounted(async () => {
  if (!consent.value) return
  await load()
  notifyDaily()
})

function onPromoClick() {
  // 没授权：先讲清用途再让用户点头，不靠点一下卡就默认同意
  if (!consent.value) {
    showConsent.value = true
    return
  }
  // 已授权只是没坐标：直接给手选城市，不再多弹一次「定位失败」
  if (!hasCoords.value) {
    openCityPicker()
    return
  }
  reload()
}

function openHttps() {
  window.open(httpsUrl.value, '_blank', 'noopener')
}

async function reload() {
  await load({ force: true })
}

async function useLocation() {
  locating.value = true
  try {
    await setConsent(true)
    const res = await requestLocation()
    showConsent.value = false
    if (res.ok) {
      showGeoFail.value = false
      showToast({ message: `${info.value.icon} ${weather.value?.temp ?? '--'}°C　${info.value.tip}`, duration: 3000 })
    } else {
      // 不说「定位失败」这种没信息量的话：告诉用户卡在哪一步、下一步怎么做
      geoReason.value = res.reason
      showCity.value = false
      showGeoFail.value = true
    }
  } finally {
    locating.value = false
  }
}

function openCityPicker() {
  showGeoFail.value = false
  showConsent.value = false
  // 定位用不了时预填一个按手机时区猜的城市名，省一次在手机上打字
  if (!keyword.value) keyword.value = guessCity()
  showCity.value = true
}

async function doSearch() {
  searching.value = true
  searched.value = true
  try {
    results.value = await searchCity(keyword.value)
  } finally {
    searching.value = false
  }
}

async function chooseCity(item) {
  if (!consent.value) await setConsent(true)
  await setCoords(item.lat, item.lon, item.label, true)
  showCity.value = false
  showConsent.value = false
  showToast('天气已更新')
}

function openLegal(key) {
  router.push({ name: `legal-${key}` })
}
</script>

<style scoped>
.wx {
  padding: 12px 14px;
  border-radius: 18px;
}

.wx__promo {
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
}

.wx__promo-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wx__promo-actions {
  display: flex;
  gap: 10px;
  cursor: default;
}

.wx__promo-actions .van-button {
  flex: 1;
  min-width: 0;
}

.wx__https {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 10px;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--van-text-color-2);
  background: rgba(127, 127, 127, 0.1);
  cursor: pointer;
}

.wx__https-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--xinyu-accent);
}

.wx__https-arrow {
  flex-shrink: 0;
  color: var(--van-text-color-3);
}

.wx__emoji {
  font-size: 22px;
  line-height: 1;
}

.wx__emoji--big {
  font-size: 38px;
}

.wx__cta-text {
  flex: 1;
  min-width: 0;
}

.wx__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--van-text-color);
}

.wx__sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--van-text-color-2);
}

.wx__main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wx__body {
  flex: 1;
  min-width: 0;
}

.wx__temp {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--van-text-color);
}

.wx__temp small {
  font-size: 13px;
  font-weight: 500;
}

.wx__desc {
  font-size: 13px;
  font-weight: 500;
  color: var(--xinyu-accent);
}

.wx__place {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 3px;
  font-size: 12px;
  color: var(--van-text-color-2);
}

.wx__range {
  margin-left: 4px;
}

.wx__refresh {
  padding: 6px;
  font-size: 16px;
  color: var(--van-text-color-3);
  cursor: pointer;
}

.wx__tip {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed rgba(127, 127, 127, 0.22);
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--van-text-color);
}

.wx__skeleton {
  padding: 2px 0;
}

.wx-sheet__https {
  margin: 0 0 14px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--xinyu-accent);
  background: rgba(127, 127, 127, 0.12);
  word-break: break-all;
}

.wx--compact .wx__temp {
  font-size: 22px;
}

.wx--compact .wx__emoji--big {
  font-size: 30px;
}

.wx-sheet__body {
  padding: 20px 18px calc(20px + env(safe-area-inset-bottom));
}

.wx-sheet__title {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: var(--van-text-color);
}

.wx-sheet__list {
  margin: 0 0 16px;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--van-text-color-2);
}

.wx-sheet__list em {
  font-style: normal;
  font-weight: 600;
  color: #e5484d;
}

.wx-sheet__note {
  margin: 0 0 14px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #a2601a;
  background: rgba(250, 190, 80, 0.16);
}

.wx-sheet__desc {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}

.wx-sheet__hint {
  margin-top: 6px;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--van-text-color-3);
}

.wx-sheet__ghost {
  margin-top: 10px;
}

.wx-sheet__foot {
  margin-top: 12px;
  font-size: 11.5px;
  text-align: center;
  color: var(--van-text-color-3);
}

.wx-sheet__foot a {
  color: var(--xinyu-accent);
}

.wx-sheet__loading {
  justify-content: center;
  padding: 20px 0;
}

.wx-sheet__cities {
  max-height: 46vh;
  overflow-y: auto;
}

/* 手机窄屏收一档：天气卡是首页第三块内容，太高会把「最近日记」顶下去 */
@media (max-width: 767px) {
  .wx {
    padding: 10px 12px;
    border-radius: 16px;
  }
  .wx__emoji {
    font-size: 20px;
  }
  .wx__title {
    font-size: 14px;
  }
  /* 说明文字别再往下压：这两个分支本来就靠它讲清楚「为什么定位用不了」 */
  .wx__sub {
    font-size: 12px;
    line-height: 1.5;
  }
  .wx__temp,
  .wx--compact .wx__temp {
    font-size: 19px;
  }
  .wx__emoji--big,
  .wx--compact .wx__emoji--big {
    font-size: 26px;
  }
  .wx__place {
    font-size: 11.5px;
  }
  .wx__tip {
    margin-top: 7px;
    padding-top: 7px;
    font-size: 12px;
    line-height: 1.5;
  }
  .wx__promo {
    gap: 10px;
  }
  .wx__promo-actions {
    gap: 8px;
  }
  .wx__https {
    font-size: 11px;
  }
}
</style>
