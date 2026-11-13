<template>
  <div class="page">
    <van-nav-bar title="设置" left-arrow @click-left="router.back()" />

    <div class="account xinyu-glass">
      <div class="account__avatar" @click="pickAvatar">
        <van-image round width="64" height="64" :src="avatarSrc" />
        <span class="account__edit">更换</span>
      </div>
      <div class="account__info">
        <div class="account__name">{{ user?.nickname || user?.username }}</div>
        <div class="account__sub">@{{ user?.username }}</div>
      </div>
    </div>

    <van-cell-group inset title="账号">
      <van-cell title="昵称" icon="edit" :value="user?.nickname || '未设置'" is-link @click="openNickname" />
      <van-cell
        title="手机号"
        icon="phone-o"
        :value="user?.phone || '未绑定'"
        is-link
        @click="openPhone"
      />
      <van-cell title="登录密码" icon="lock" value="修改" is-link @click="openPassword" />
    </van-cell-group>

    <van-cell-group inset title="隐私与授权">
      <van-cell title="定位与天气" icon="location-o" center>
        <template #label>
          <span class="privacy__label">
            {{ user?.locationConsent ? '已开启：坐标只留在本机，不上传服务器' : '开启后在首页显示所在地天气' }}
          </span>
        </template>
        <template #right-icon>
          <van-switch
            :model-value="!!user?.locationConsent"
            size="22"
            :loading="consentSaving"
            @update:model-value="toggleConsent"
          />
        </template>
      </van-cell>
      <van-cell title="回收站" icon="delete-o" is-link @click="router.push('/trash')" />
      <van-cell title="我的举报" icon="warning-o" is-link @click="router.push('/reports')" />
      <van-cell title="用户协议" icon="description" is-link @click="openLegal('terms')" />
      <van-cell title="隐私政策" icon="shield-o" is-link @click="openLegal('privacy')" />
      <van-cell title="免责声明" icon="warning-o" is-link @click="openLegal('disclaimer')" />
      <van-cell title="同意记录" icon="passed" :value="agreementLabel" />
    </van-cell-group>

    <van-cell-group inset title="关于">
      <van-cell title="更新说明" icon="info-o" :value="versionLabel" is-link @click="openNotice" />
      <van-cell title="显示模式" icon="eye-o" :value="modeLabel" is-link @click="showTheme = true" />
    </van-cell-group>

    <van-cell-group inset title="写日记提醒">
      <van-cell title="每天提醒我" icon="clock-o" center>
        <template #label>
          <span class="privacy__label">
            到点在打开心语时提醒一次，网页关掉后不会再叫；提醒设置只存在这台设备上
          </span>
        </template>
        <template #right-icon>
          <van-switch :model-value="reminder.enabled" size="22" @update:model-value="toggleReminder" />
        </template>
      </van-cell>
      <van-cell
        v-if="reminder.enabled"
        title="提醒时间"
        icon="underway-o"
        :value="reminder.time"
        is-link
        @click="showTimePicker = true"
      />
    </van-cell-group>

    <van-cell-group inset title="离线与同步">
      <van-cell
        title="待同步日记"
        icon="replay"
        :value="offlineStore.syncing ? '正在同步…' : offlineStore.pending ? `${offlineStore.pending} 篇` : '已全部同步'"
        :is-link="offlineStore.pending > 0"
        @click="onSyncNow"
      />
      <van-cell
        title="网络状态"
        icon="wifi-o"
        :value="offlineStore.online ? '在线' : '离线'"
      />
      <van-cell title="清空本地缓存" icon="delete-o" is-link label="不影响已同步的日记" @click="onClearCache" />
    </van-cell-group>

    <van-cell-group v-if="isApp" inset title="服务器地址">
      <van-cell
        title="后端地址"
        icon="link-o"
        :value="serverAddrLabel"
        is-link
        label="换电脑或换 Wi-Fi 后连不上，改这里就行，不用重新装包"
        @click="openServer"
      />
    </van-cell-group>

    <van-cell-group inset title="我的数据">
      <van-cell title="导出我的数据" icon="down" is-link @click="onExport" />
      <van-cell title="注销账号" icon="delete-o" is-link @click="openDelete" />
    </van-cell-group>

    <div class="logout">
      <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>

    <!-- 昵称 -->
    <van-popup v-model:show="showNickname" round position="bottom" :style="{ padding: '20px 16px 24px' }">
      <div class="pop-title">修改昵称</div>
      <van-field
        v-model="nickname"
        maxlength="20"
        show-word-limit
        clearable
        placeholder="1-20 个字，不能冒用他人姓名"
      />
      <div class="pop-tip">@{{ user?.username }} 是登录账号，不能修改</div>
      <van-button round block type="primary" :loading="saving" @click="saveNickname">保存</van-button>
    </van-popup>

    <!-- 密码 -->
    <van-popup v-model:show="showPassword" round position="bottom" :style="{ padding: '20px 16px 24px' }">
      <div class="pop-title">修改密码</div>
      <van-field v-model="pwd.oldPassword" type="password" label="原密码" placeholder="请输入当前密码" />
      <van-field v-model="pwd.newPassword" type="password" label="新密码" placeholder="8-32 位，含字母和数字" />
      <van-field v-model="pwd.confirm" type="password" label="确认新密码" placeholder="再次输入新密码" />
      <div class="pop-tip">改完密码后，其它设备上的登录状态仍然有效，建议一并退出。</div>
      <van-button round block type="primary" :loading="saving" @click="savePassword">确认修改</van-button>
    </van-popup>

    <!-- 服务器地址（只有 App 里需要：网页版走同源 /api） -->
    <van-popup v-model:show="showServer" round position="bottom" :style="{ padding: '20px 16px 24px' }">
      <div class="pop-title">服务器地址</div>
      <div class="pop-tip">
        填电脑的局域网地址就行（例如 192.168.1.14:8080），会自动补上 http:// 和 /api。
        手机和电脑要连同一个 Wi-Fi，电脑那边的后端得开着。
      </div>
      <van-field v-model="serverDraft" clearable placeholder="192.168.1.14:8080" />
      <div class="pop-tip">当前：{{ serverAddrLabel }}</div>
      <van-button round block type="primary" :loading="testing" @click="saveServer">测试并保存</van-button>
      <van-button class="server-reset" round block plain @click="restoreServer">恢复默认地址</van-button>
    </van-popup>

    <!-- 手机号 -->
    <van-popup v-model:show="showPhone" round position="bottom" :style="{ padding: '20px 16px 24px' }">
      <div class="pop-title">{{ phoneBound ? '更换手机号' : '绑定手机号' }}</div>
      <van-field
        v-model="phoneForm.phone"
        type="tel"
        label="手机号"
        maxlength="11"
        placeholder="请输入新手机号"
      />
      <van-field v-model="phoneForm.smsCode" label="验证码" maxlength="6" placeholder="6 位数字">
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
        v-if="phoneBound"
        v-model="phoneForm.password"
        type="password"
        label="当前密码"
        placeholder="换号需要验证身份"
      />
      <div class="pop-tip">
        手机号只用于危机情况下的联系，不会公开显示；其他用户看到的都是打码号码。
      </div>
      <van-button round block type="primary" :loading="saving" @click="savePhone">保存</van-button>
    </van-popup>

    <!-- 注销账号：密码二次确认，销毁不可恢复 -->
    <van-dialog
      v-model:show="showDelete"
      title="注销账号"
      show-cancel-button
      confirm-button-text="确认注销"
      confirm-button-color="#ee0a24"
      :before-close="confirmDelete"
    >
      <div class="delete-box">
        <p class="delete-box__tip">
          注销后，你的日记、树洞、评论、好友与私聊记录都会被删除且无法恢复。
          如果只是想暂时离开，直接退出登录就好。
        </p>
        <van-field v-model="deletePassword" type="password" placeholder="输入登录密码以确认" />
      </div>
    </van-dialog>

    <van-popup v-model:show="showTimePicker" round position="bottom">
      <van-time-picker
        v-model="timeValue"
        title="提醒时间"
        :columns-type="['hour', 'minute']"
        @confirm="saveTime"
        @cancel="showTimePicker = false"
      />
    </van-popup>

    <CaptchaPopup v-bind="popupProps" @confirm="confirm" @refresh="refreshCaptcha" />

    <van-action-sheet
      v-model:show="showTheme"
      :actions="themeActions"
      cancel-text="取消"
      close-on-click-action
      description="深色 / 浅色可手动指定，也可以跟随手机系统自动切换"
      @select="onSelectTheme"
    />

    <input
      ref="fileInput"
      class="hidden-file"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      @change="onFileChange"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '../stores/user'
import { useOfflineStore } from '../stores/offline'
import { clearApiCache } from '../offline/cache'
import { clearOutbox } from '../offline/outbox'
import { forgetAccount } from '../offline/credential'
import {
  updateProfile,
  changePassword,
  changePhone,
  uploadAvatar,
  exportMyData,
  deleteMyAccount
} from '../api/user'
import { useSmsCode } from '../composables/useSmsCode'
import { useUpdateNotice } from '../composables/useUpdateNotice'
import { THEME_OPTIONS, useTheme } from '../composables/useTheme'
import {
  defaultApiBase,
  getApiBase,
  mediaUrl,
  normalizeBase,
  resetApiBase,
  setApiBase,
  testApiBase
} from '../utils/backend'
import { useWeather } from '../composables/useWeather'
import { useReminder } from '../composables/useReminder'
import { versionLabel } from '../config/changelog'
import { LEGAL_VERSION } from '../config/legal'
import CaptchaPopup from '../components/CaptchaPopup.vue'
import { checkPassword } from '../utils/password'

const router = useRouter()
const userStore = useUserStore()
const offlineStore = useOfflineStore()
const { open: openNotice } = useUpdateNotice()
const { mode, modeLabel, setMode } = useTheme()

const user = computed(() => userStore.user)
const phoneBound = computed(() => !!user.value?.phoneBound)
// 头像路径是后端返回的 /uploads/...，网页里相对路径可用，App 里要补主机名（见 utils/backend.js）
const avatarSrc = computed(() => mediaUrl(user.value?.avatar) || 'https://img.yzcdn.cn/vant/cat.jpeg')

const saving = ref(false)
const fileInput = ref(null)

const showNickname = ref(false)
const nickname = ref('')
const showPassword = ref(false)
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const showPhone = ref(false)
const phoneForm = ref({ phone: '', smsCode: '', password: '' })

// ---- 服务器地址（只对 App 有意义：网页版前端和接口同源，改不了也不需要改）----
const isApp = /^https?:\/\//i.test(defaultApiBase())
const showServer = ref(false)
const testing = ref(false)
const serverDraft = ref('')
const serverAddrLabel = ref(getApiBase().replace(/^https?:\/\//i, ''))

function openServer() {
  serverAddrLabel.value = getApiBase().replace(/^https?:\/\//i, '')
  serverDraft.value = serverAddrLabel.value.replace(/\/api$/i, '')
  showServer.value = true
}

async function saveServer() {
  if (testing.value) return
  const base = normalizeBase(serverDraft.value)
  testing.value = true
  try {
    if (!(await testApiBase(base))) {
      showToast('这个地址连不上，检查电脑上的后端和是否同一个 Wi-Fi')
      return
    }
    setApiBase(base)
    showServer.value = false
    showToast('已切换，正在重新连接')
    setTimeout(() => window.location.reload(), 600)
  } finally {
    testing.value = false
  }
}

function restoreServer() {
  resetApiBase()
  showServer.value = false
  showToast('已恢复默认地址')
  setTimeout(() => window.location.reload(), 600)
}

// 显示模式：右侧文案跟着当前选择走，当前选中项在弹层里标一个「当前」
const showTheme = ref(false)
const themeActions = computed(() =>
  THEME_OPTIONS.map((item) => ({
    name: item.label,
    value: item.value,
    subname: item.value === mode.value ? '当前' : ''
  }))
)

function onSelectTheme(action) {
  setMode(action.value)
  showToast(`已切换为${action.name}`)
}

const { popupProps, sending, countdown, buttonText, requestCode, refreshCaptcha, confirm } = useSmsCode()

// 定位与天气：开关状态存后端，坐标存本机，见 composables/useWeather.js
const { setConsent } = useWeather()
const consentSaving = ref(false)

const agreementLabel = computed(() => {
  if (!user.value?.agreementAccepted) return '未确认'
  const at = user.value?.agreementAcceptedAt?.replace('T', ' ').slice(0, 16)
  return at ? `v${LEGAL_VERSION} · ${at}` : `v${LEGAL_VERSION}`
})

function openLegal(key) {
  router.push({ name: `legal-${key}` })
}

async function toggleConsent(next) {
  // 同意得让用户明明白白点头，不能靠拨一下开关就算数
  if (next) {
    try {
      await showConfirmDialog({
        title: '开启定位与天气',
        message:
          '开启后，心语会读取你的大致位置，用来查询当地天气并显示在首页。\n\n' +
          '坐标只保存在这台设备的浏览器里，不会上传到心语服务器；随时可以在这里关闭，' +
          '关闭后本地位置与天气缓存会立即删除。详情见《隐私政策》。',
        confirmButtonText: '同意并开启'
      })
    } catch {
      return
    }
  }
  consentSaving.value = true
  try {
    await setConsent(next)
    showToast(next ? '已开启，首页会显示当地天气' : '已关闭，本地位置与缓存已清除')
  } finally {
    consentSaving.value = false
  }
}

function openNickname() {
  nickname.value = user.value?.nickname || ''
  showNickname.value = true
}

async function saveNickname() {
  const name = nickname.value.trim()
  if (!name) {
    showToast('昵称不能为空')
    return
  }
  if (name === user.value?.nickname) {
    showNickname.value = false
    return
  }
  saving.value = true
  try {
    userStore.setUser(await updateProfile({ nickname: name }))
    showNickname.value = false
    showToast('昵称已更新')
  } finally {
    saving.value = false
  }
}

function openPassword() {
  pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  showPassword.value = true
}

async function savePassword() {
  if (!pwd.value.oldPassword || !pwd.value.newPassword) {
    showToast('请填写完整')
    return
  }
  if (pwd.value.newPassword !== pwd.value.confirm) {
    showToast('两次输入的新密码不一致')
    return
  }
  const weak = checkPassword(pwd.value.newPassword, user.value?.username)
  if (weak) {
    showToast(weak)
    return
  }
  saving.value = true
  try {
    await changePassword({
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword
    })
    showPassword.value = false
    showToast('密码修改成功')
  } finally {
    saving.value = false
  }
}

function openPhone() {
  phoneForm.value = { phone: '', smsCode: '', password: '' }
  showPhone.value = true
}

async function onSendCode() {
  if (!/^1[3-9]\d{9}$/.test(phoneForm.value.phone)) {
    showToast('请先填写正确的手机号')
    return
  }
  await requestCode(phoneForm.value.phone)
}

async function savePhone() {
  if (!phoneForm.value.smsCode) {
    showToast('请填写验证码')
    return
  }
  if (phoneBound.value && !phoneForm.value.password) {
    showToast('更换手机号需要输入当前密码')
    return
  }
  saving.value = true
  try {
    userStore.setUser(await changePhone({ ...phoneForm.value }))
    showPhone.value = false
    showToast('手机号已更新')
  } finally {
    saving.value = false
  }
}

function pickAvatar() {
  fileInput.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  // 选同一个文件不会再触发 change，所以拿完就清空
  event.target.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    showToast('图片不能超过 2MB')
    return
  }
  saving.value = true
  try {
    userStore.setUser(await uploadAvatar(file))
    showToast('头像已更新')
  } finally {
    saving.value = false
  }
}

// 写日记提醒：开关与时间只存本机，见 composables/useReminder.js
const { config: reminder, setEnabled, setTime } = useReminder()
const showTimePicker = ref(false)
const timeValue = ref((reminder.value.time || '21:00').split(':'))

function toggleReminder(next) {
  setEnabled(next)
  showToast(next ? `已开启，每天 ${reminder.value.time} 提醒你写日记` : '已关闭写日记提醒')
}

function saveTime({ selectedValues }) {
  setTime(selectedValues.join(':'))
  showTimePicker.value = false
  showToast(`提醒时间已设为 ${selectedValues.join(':')}`)
}

const exporting = ref(false)

/** 导出成一个 .json 文件：内容都是用户自己的，直接给出原文 */
async function onExport() {
  exporting.value = true
  try {
    const data = await exportMyData()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const stamp = new Date().toISOString().slice(0, 10)
    a.href = url
    a.download = `心语-数据导出-${stamp}.json`
    a.click()
    URL.revokeObjectURL(url)
    showToast('已导出，请妥善保管')
  } finally {
    exporting.value = false
  }
}

const showDelete = ref(false)
const deletePassword = ref('')

function openDelete() {
  deletePassword.value = ''
  showDelete.value = true
}

async function confirmDelete(action) {
  if (action !== 'confirm') return true
  if (!deletePassword.value) {
    showToast('请输入登录密码')
    return false
  }
  try {
    await deleteMyAccount(deletePassword.value)
  } catch {
    // 密码错误等失败由 request.js 提示，保留弹窗让用户重新输入
    return false
  }
  showToast('账号已注销')
  // 账号没了，本机属于这个账号的东西也不该留着（含还没传上去的草稿式日记）
  await clearOutbox()
  await clearApiCache()
  // 离线登录凭证同样不能留，否则还能拿旧密码进一个已经不存在的账号
  forgetAccount(userStore.user?.username)
  userStore.logout()
  router.replace('/login')
  return true
}

async function onLogout() {
  await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  userStore.logout()
  router.replace('/login')
}

async function onSyncNow() {
  if (!offlineStore.pending) {
    showToast('没有待同步的日记')
    return
  }
  const result = await offlineStore.sync()
  if (!result || result.synced === 0) {
    showToast(offlineStore.online ? '同步失败，稍后会自动重试' : '当前连不上服务器')
    return
  }
  showToast(result.remaining > 0 ? `已同步 ${result.synced} 篇，还有 ${result.remaining} 篇` : `已同步 ${result.synced} 篇`)
}

async function onClearCache() {
  const tip = offlineStore.pending
    ? `本机缓存会被清空，${offlineStore.pending} 篇待同步的日记不受影响。`
    : '清空后断网时将无法查看已读过的内容，已同步的日记不受影响。'
  try {
    await showConfirmDialog({ title: '清空本地缓存', message: tip })
  } catch {
    return
  }
  await clearApiCache()
  showToast('已清空缓存')
}
</script>

<style scoped>
.account {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 4px 0 18px;
  padding: 16px;
  border-radius: 18px;
}
.account__avatar {
  position: relative;
  cursor: pointer;
}
.account__edit {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 11px;
  text-align: center;
}
.account__name {
  font-size: 17px;
  font-weight: 600;
}
.account__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--van-text-color-2);
}
.pop-title {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}
.privacy__label {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--van-text-color-3);
}
.pop-tip {
  margin: 10px 0 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.logout {
  margin: 24px 0 0;
}
.server-reset {
  margin-top: 10px;
}
.hidden-file {
  display: none;
}
.delete-box {
  padding: 4px 16px 16px;
}
.delete-box__tip {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--van-text-color-2);
}
</style>
