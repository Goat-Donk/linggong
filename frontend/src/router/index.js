import { createRouter, createWebHistory } from 'vue-router'
import { userState } from '@/stores/user'

// 路由表：requiresAuth=true 表示需要登录才能访问
const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/home',
    name: 'home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '我的', requiresAuth: true }
  },
  {
    path: '/job/:id',
    name: 'jobDetail',
    component: () => import('@/views/JobDetail.vue'),
    meta: { title: '岗位详情' }
  },
  {
    path: '/feed',
    name: 'feed',
    component: () => import('@/views/Feed.vue'),
    meta: { title: '动态', requiresAuth: true }
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('@/views/PublishJob.vue'),
    meta: { title: '发布岗位', requiresAuth: true }
  },
  {
    path: '/my-applications',
    name: 'myApplications',
    component: () => import('@/views/MyApplications.vue'),
    meta: { title: '我的报名', requiresAuth: true }
  },
  {
    path: '/employer-applications',
    name: 'employerApplications',
    component: () => import('@/views/EmployerApplications.vue'),
    meta: { title: '审核报名', requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 鉴权守卫：需要登录的页面，未登录跳登录页并带上来源（登录后跳回）
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !userState.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · 本地零工平台` : '本地零工平台'
})

export default router
