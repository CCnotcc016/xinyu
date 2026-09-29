<!--
心语 (Xinyu) · AI 情绪日记与匿名树洞
Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
-->
<template>
  <div class="page">
    <van-nav-bar title="我的" />

    <div class="profile" @click="router.push('/settings')">
      <van-image round width="64" height="64" :src="userStore.user?.avatar || defaultAvatar" />
      <div class="profile-info">
        <div class="name">
          {{ userStore.user?.nickname || userStore.user?.username }}
          <van-icon name="arrow" class="edit-icon" />
        </div>
        <div class="username">@{{ userStore.user?.username }}</div>
      </div>
    </div>

    <van-cell-group inset>
      <van-cell
        title="账号与安全"
        icon="setting-o"
        :value="userStore.user?.phoneBound ? userStore.user?.phone : '未绑定手机号'"
        is-link
        @click="router.push('/settings')"
      />
      <van-cell title="我的日记" icon="notes-o" is-link @click="router.push('/diaries')" />
      <van-cell title="情绪趋势" icon="chart-trending-o" is-link @click="router.push('/trend')" />
      <van-cell v-if="userStore.isAdmin" title="后台管理" icon="friends-o" is-link @click="router.push('/admin')" />
      <van-cell title="更新说明" icon="info-o" :value="`v${APP_VERSION}`" is-link @click="openNotice" />
    </van-cell-group>

    <div class="logout">
      <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '../stores/user'
import { useUpdateNotice } from '../composables/useUpdateNotice'
import { APP_VERSION } from '../config/changelog'

const router = useRouter()
const userStore = useUserStore()
const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const { open: openNotice } = useUpdateNotice()

async function onLogout() {
  await showConfirmDialog({ title: '提示', message: '确定退出登录吗？' })
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.profile {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 8px;
  cursor: pointer;
}
.profile-info .name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 20px;
  font-weight: 600;
}
.edit-icon {
  font-size: 15px;
  color: var(--van-text-color-2);
}
.profile-info .username {
  color: var(--van-text-color-2);
  margin-top: 4px;
}
.logout {
  margin: 24px 16px;
}
</style>
