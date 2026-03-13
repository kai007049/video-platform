import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      { path: '', name: 'Home', component: () => import('../views/Home.vue'), meta: { title: '首页' } },
      { path: 'video/:id', name: 'Video', component: () => import('../views/VideoDetail.vue'), meta: { title: '视频播放' } },
      { path: 'upload', name: 'Upload', component: () => import('../views/Upload.vue'), meta: { title: '投稿', auth: true } },
      { path: 'user/:id', name: 'UpProfile', component: () => import('../views/UpProfile.vue'), meta: { title: 'UP主主页' } },
      { path: 'creator', name: 'Creator', component: () => import('../views/CreatorDashboard.vue'), meta: { title: '创作者中心', auth: true } },
      { path: 'admin', name: 'Admin', component: () => import('../views/AdminPage.vue'), meta: { title: '管理后台', auth: true, adminOnly: true } },
      { path: 'message', name: 'MessageCenter', component: () => import('../views/MessageCenter.vue'), meta: { title: '消息中心', auth: true } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 哔哩哔哩` : '哔哩哔哩'
  if (to.meta.auth) {
    const token = sessionStorage.getItem('token')
    if (!token) {
      next({ path: '/', query: { login: '1' } })
      return
    }
  }
  if (to.meta.adminOnly) {
    const userInfo = JSON.parse(sessionStorage.getItem('userInfo') || 'null')
    if (!userInfo?.isAdmin) {
      next({ path: '/' })
      return
    }
  }
  next()
})

export default router
