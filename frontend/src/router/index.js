import { createRouter, createWebHistory } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '../stores/user'

// 三份法律文本共用一个组件，靠 props 区分；public 是为了让未登录的人（注册页）也能打开
const legalRoutes = ['terms', 'privacy', 'disclaimer'].map((key) => ({
  path: `/legal/${key}`,
  name: `legal-${key}`,
  component: () => import('../views/Legal.vue'),
  props: { docKey: key },
  meta: { public: true }
}))

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue'), meta: { public: true } },
  ...legalRoutes,
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { tab: true } },
  { path: '/diaries', name: 'diary-list', component: () => import('../views/DiaryList.vue'), meta: { tab: true } },
  { path: '/diaries/write', name: 'diary-write', component: () => import('../views/DiaryWrite.vue') },
  { path: '/diaries/:id', name: 'diary-detail', component: () => import('../views/DiaryDetail.vue') },
  { path: '/trend', name: 'trend', component: () => import('../views/Trend.vue'), meta: { tab: true } },
  // 心理自评：量表列表 → 答题 → 结果。结果页要先于 :key 声明，否则 /assessment/result/1 会被当成量表 key
  { path: '/assessment', name: 'assessment-home', component: () => import('../views/AssessmentHome.vue') },
  { path: '/assessment/result/:id', name: 'assessment-result', component: () => import('../views/AssessmentResult.vue') },
  { path: '/assessment/:key', name: 'assessment-test', component: () => import('../views/AssessmentTest.vue') },
  // 月度 / 年度回顾：把这一段时间的情绪收成一页
  { path: '/recap', name: 'recap', component: () => import('../views/Recap.vue') },
  // 我提交的举报及处理结果
  { path: '/reports', name: 'my-reports', component: () => import('../views/MyReports.vue') },
  // 回收站：7 天内删除的日记可以恢复
  { path: '/trash', name: 'trash', component: () => import('../views/Trash.vue') },
  { path: '/treehole', name: 'treehole', component: () => import('../views/Treehole.vue'), meta: { tab: true } },
  { path: '/treehole/:id', name: 'treehole-detail', component: () => import('../views/TreeholeDetail.vue') },
  // 好友与私聊
  { path: '/chat', name: 'chat-list', component: () => import('../views/ChatList.vue'), meta: { tab: true } },
  { path: '/chat/:id', name: 'chat-room', component: () => import('../views/ChatRoom.vue') },
  { path: '/friends/requests', name: 'friend-requests', component: () => import('../views/FriendRequests.vue') },
  { path: '/notifications', name: 'notifications', component: () => import('../views/Notifications.vue') },
  // 从树洞里的昵称点进来的个人主页（:id 用 u- 前缀的路径，避免和 /user 之类的短路径撞车）
  { path: '/u/:id', name: 'user-profile', component: () => import('../views/UserProfile.vue') },
  { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { tab: true } },
  { path: '/settings', name: 'settings', component: () => import('../views/Settings.vue') },
  // 用户联系管理员 / 客服
  { path: '/support', name: 'support', component: () => import('../views/Support.vue') },
  // 赞助：纯展示页，文案在 Sponsor.vue 里改
  { path: '/sponsor', name: 'sponsor', component: () => import('../views/Sponsor.vue') },
  // 客服工作台：ADMIN 与 STAFF 都能进，STAFF 只看到自己接手与待接手的会话
  { path: '/staff', name: 'staff', component: () => import('../views/staff/StaffConsole.vue'), meta: { staff: true, tab: true } },
  { path: '/staff/:id', name: 'staff-chat', component: () => import('../views/staff/StaffChat.vue'), meta: { staff: true } },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminDashboard.vue'),
    meta: { admin: true },
    children: [
      { path: '', name: 'admin-home', component: () => import('../views/admin/AdminUsers.vue') },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/AdminUsers.vue') },
      { path: 'diaries', name: 'admin-diaries', component: () => import('../views/admin/AdminDiaries.vue') },
      { path: 'posts', name: 'admin-posts', component: () => import('../views/admin/AdminPosts.vue') },
      { path: 'comments', name: 'admin-comments', component: () => import('../views/admin/AdminComments.vue') },
      { path: 'reports', name: 'admin-reports', component: () => import('../views/admin/AdminReports.vue') },
      { path: 'crisis', name: 'admin-crisis', component: () => import('../views/admin/AdminCrisis.vue') },
      { path: 'sensitive', name: 'admin-sensitive', component: () => import('../views/admin/AdminSensitive.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

/* 客服只需要工作台和「我的」，其它页面一律挡回工作台 */
const STAFF_ALLOWED = new Set([
  'staff',
  'staff-chat',
  'profile',
  'settings',
  'sponsor',
  'legal-terms',
  'legal-privacy',
  'legal-disclaimer'
])

/* 初始密码未改时唯一能去的页面 */
const MUST_CHANGE_ALLOWED = new Set(['settings', 'login', 'sponsor', 'legal-terms', 'legal-privacy', 'legal-disclaimer'])

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && userStore.user?.role !== 'ADMIN') {
    return { name: 'home' }
  }
  if (to.meta.staff && !userStore.isAdminOrStaff) {
    return { name: 'home' }
  }
  if (userStore.isStaff && !to.meta.public && !STAFF_ALLOWED.has(to.name)) {
    return { name: 'staff' }
  }
  // 初始密码未改：后端只放行「改密 / 我的信息 / 登录」，前端别让人白点一圈
  if (userStore.user?.mustChangePassword && !MUST_CHANGE_ALLOWED.has(to.name)) {
    return { name: 'settings', query: { forcePassword: '1' } }
  }
  return true
})

// 懒加载的页面在断网时拉不到 chunk，路由会抛错、页面停在上一屏。
// 这里给一句人话提示，比控制台里一个 fetch failed 好得多。
router.onError((error) => {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    showToast('当前离线，这个页面还没下载过')
  } else if (!error?.handled) {
    console.error(error)
  }
})

export default router
