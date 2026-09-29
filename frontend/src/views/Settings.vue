<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="设置" left-arrow @click-left="router.back()" />

    <div class="account">
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

    <van-cell-group inset title="关于">
      <van-cell title="更新说明" icon="info-o" :value="`v${APP_VERSION}`" is-link @click="openNotice" />
      <van-cell title="显示模式" icon="eye-o" :value="modeLabel" is-link @click="showTheme = true" />
    </van-cell-group>

    <p class="copyright">
      心语 Xinyu · © 2026 CCnotcc016<br />
      仅供交流学习使用，未经授权禁止商业售卖
    </p>

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
      <van-field v-model="pwd.newPassword" type="password" label="新密码" placeholder="6-32 位，不含空格" />
      <van-field v-model="pwd.confirm" type="password" label="确认新密码" placeholder="再次输入新密码" />
      <div class="pop-tip">改完密码后，其它设备上的登录状态仍然有效，建议一并退出。</div>
      <van-button round block type="primary" :loading="saving" @click="savePassword">确认修改</van-button>
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
import { updateProfile, changePassword, changePhone, uploadAvatar } from '../api/user'
import { useSmsCode } from '../composables/useSmsCode'
import { useUpdateNotice } from '../composables/useUpdateNotice'
import { THEME_OPTIONS, useTheme } from '../composables/useTheme'
import { APP_VERSION } from '../config/changelog'
import CaptchaPopup from '../components/CaptchaPopup.vue'

const router = useRouter()
const userStore = useUserStore()
const { open: openNotice } = useUpdateNotice()
const { mode, modeLabel, setMode } = useTheme()

const user = computed(() => userStore.user)
const phoneBound = computed(() => !!user.value?.phoneBound)
// 头像路径是后端的相对地址，开发环境走 Vite 代理，所以直接用即可
const avatarSrc = computed(() => user.value?.avatar || 'https://img.yzcdn.cn/vant/cat.jpeg')

const saving = ref(false)
const fileInput = ref(null)

const showNickname = ref(false)
const nickname = ref('')
const showPassword = ref(false)
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const showPhone = ref(false)
const phoneForm = ref({ phone: '', smsCode: '', password: '' })

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

async function onLogout() {
  await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.account {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 4px 0 18px;
  padding: 16px;
  border-radius: 14px;
  background: var(--xinyu-glass-bg);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  box-shadow: var(--xinyu-shadow);
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
.pop-tip {
  margin: 10px 0 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--van-text-color-2);
}
.logout {
  margin: 24px 0 0;
}
.copyright {
  margin-top: 32px;
  text-align: center;
  font-size: 11px;
  line-height: 1.6;
  color: var(--van-text-color-3, #969799);
  opacity: 0.75;
}
.hidden-file {
  display: none;
}
</style>
