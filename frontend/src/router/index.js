// 心语 (Xinyu) · AI 情绪日记与匿名树洞
// Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
// 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/Register.vue'), meta: { public: true } },
  { path: '/', name: 'home', component: () => import('../views/Home.vue'), meta: { tab: true } },
  { path: '/diaries', name: 'diary-list', component: () => import('../views/DiaryList.vue'), meta: { tab: true } },
  { path: '/diaries/write', name: 'diary-write', component: () => import('../views/DiaryWrite.vue') },
  { path: '/diaries/:id', name: 'diary-detail', component: () => import('../views/DiaryDetail.vue') },
  { path: '/trend', name: 'trend', component: () => import('../views/Trend.vue'), meta: { tab: true } },
  { path: '/treehole', name: 'treehole', component: () => import('../views/Treehole.vue'), meta: { tab: true } },
  { path: '/treehole/:id', name: 'treehole-detail', component: () => import('../views/TreeholeDetail.vue') },
  { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { tab: true } },
  { path: '/settings', name: 'settings', component: () => import('../views/Settings.vue') },
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
      { path: 'crisis', name: 'admin-crisis', component: () => import('../views/admin/AdminCrisis.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

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
  return true
})

export default router
