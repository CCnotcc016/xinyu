<template>
  <div class="page">
    <van-nav-bar title="我的" />

    <div class="profile xinyu-glass" @click="router.push('/settings')">
      <van-image round width="64" height="64" :src="mediaUrl(userStore.user?.avatar) || defaultAvatar" />
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
      <!-- 客服账号没有日记和树洞，相关入口只对普通用户和管理员显示 -->
      <van-cell v-if="!userStore.isStaff" title="我的日记" icon="notes-o" is-link @click="router.push('/diaries')" />
      <van-cell v-if="!userStore.isStaff" title="情绪趋势" icon="chart-trending-o" is-link @click="router.push('/trend')" />
      <van-cell v-if="!userStore.isStaff" title="月度回顾" icon="calendar-o" is-link @click="router.push('/recap')" />
      <!-- 心理自评：label 里带免责提示，点进去之前就知道这不是诊断 -->
      <van-cell
        v-if="!userStore.isStaff"
        title="心理自评"
        icon="records-o"
        label="PHQ-9 / GAD-7 / PHQ-4 · 仅供参考，如有不适请前往线下医院就医"
        is-link
        @click="router.push('/assessment')"
      />
      <van-cell v-if="!userStore.isStaff" title="我的聊天" icon="chat-o" is-link @click="router.push('/chat')" />
      <van-cell
        v-if="!userStore.isStaff"
        title="好友申请"
        icon="friends-o"
        is-link
        :value="chatStore.pendingRequests > 0 ? `${chatStore.pendingRequests} 条待处理` : ''"
        @click="router.push('/friends/requests')"
      />
      <van-cell
        v-if="!userStore.isStaff"
        title="消息通知"
        icon="bell"
        is-link
        :value="chatStore.notifications > 0 ? `${chatStore.notifications} 条未读` : ''"
        @click="router.push('/notifications')"
      />
      <van-cell
        v-if="!userStore.isStaff"
        title="联系管理员/客服"
        icon="service-o"
        is-link
        @click="router.push('/support')"
      />
      <van-cell title="赞助" icon="gift-o" is-link @click="router.push('/sponsor')" />
      <van-cell
        v-if="userStore.isAdminOrStaff"
        title="客服工作台"
        icon="chat-o"
        is-link
        @click="router.push('/staff')"
      />
      <van-cell v-if="userStore.isAdmin" title="后台管理" icon="friends-o" is-link @click="router.push('/admin')" />
      <van-cell
        title="更新说明"
        icon="info-o"
        :value="versionText"
        is-link
        @click="openNotice"
      />
    </van-cell-group>

    <div class="logout">
      <van-button round block plain type="danger" @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { useUserStore } from '../stores/user'
import { useChatStore } from '../stores/chat'
import { useUpdateNotice } from '../composables/useUpdateNotice'
import { versionLabel } from '../config/changelog'
import { mediaUrl } from '../utils/backend'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const { open: openNotice } = useUpdateNotice()
const versionText = computed(() => `${versionLabel.value} · 含历史记录`)

// 好友申请 / 未读通知的角标：进「我的」时拉一次最新数据
onMounted(() => chatStore.refresh())

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
  padding: 20px 16px;
  border-radius: 18px;
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
