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
    path: '/publish-blog',
    name: 'publishBlog',
    component: () => import('@/views/PublishBlog.vue'),
    meta: { title: '发布动态', requiresAuth: true }
  },
  {
    path: '/my-blogs',
    name: 'myBlogs',
    component: () => import('@/views/MyBlogs.vue'),
    meta: { title: '我的动态', requiresAuth: true }
  },
  {
    path: '/edit-profile',
    name: 'editProfile',
    component: () => import('@/views/EditProfile.vue'),
    meta: { title: '编辑资料', requiresAuth: true }
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('@/views/PublishJob.vue'),
    meta: { title: '发布岗位', requiresAuth: true }
  },
  {
    path: '/my-jobs',
    name: 'myJobs',
    component: () => import('@/views/MyJobs.vue'),
    meta: { title: '我的岗位', requiresAuth: true }
  },
  {
    path: '/wallet',
    name: 'wallet',
    component: () => import('@/views/Wallet.vue'),
    meta: { title: '我的钱包', requiresAuth: true }
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/views/NotificationList.vue'),
    meta: { title: '消息通知', requiresAuth: true }
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
  },
  {
    path: '/attendance/my',
    name: 'attendanceMy',
    component: () => import('@/views/AttendanceMy.vue'),
    meta: { title: '我的考勤', requiresAuth: true }
  },
  {
    path: '/attendance/manage',
    name: 'attendanceManage',
    component: () => import('@/views/AttendanceManage.vue'),
    meta: { title: '考勤核销', requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'favorites',
    component: () => import('@/views/FavoriteList.vue'),
    meta: { title: '我的收藏', requiresAuth: true }
  },
  {
    path: '/worker-profile/edit',
    name: 'workerProfileEdit',
    component: () => import('@/views/WorkerProfileEdit.vue'),
    meta: { title: '求职登记', requiresAuth: true }
  },
  {
    path: '/worker-profile/view/:id',
    name: 'workerProfileView',
    component: () => import('@/views/WorkerProfileView.vue'),
    meta: { title: '求职主页', requiresAuth: true }
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
