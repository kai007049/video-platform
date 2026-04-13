import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getUserInfo, getCaptcha } from '../api/user'

// 开发环境模拟用户数据
const MOCK_TOKEN = 'mock-token-dev-12345'
const MOCK_USER = {
  id: 1,
  username: '测试用户',
  avatar: '',
  email: 'test@example.com',
  isAdmin: 0,
  bio: '这是一个模拟登录的测试账号',
}

// 开发环境自动注入模拟登录状态
if (import.meta.env.DEV && !sessionStorage.getItem('token')) {
  sessionStorage.setItem('token', MOCK_TOKEN)
  sessionStorage.setItem('userInfo', JSON.stringify(MOCK_USER))
}

export const useUserStore = defineStore('user', () => {
  const token = ref(sessionStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(sessionStorage.getItem('userInfo') || 'null'))

  const isLoggedIn = computed(() => !!token.value)

  async function login(form) {
    const data = await apiLogin(form)
    token.value = data.token
    userInfo.value = data.userInfo
    sessionStorage.setItem('token', data.token)
    sessionStorage.setItem('userInfo', JSON.stringify(data.userInfo))
  }

  async function fetchCaptcha() {
    return await getCaptcha()
  }

  async function register(form) {
    await apiRegister(form)
  }

  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const info = await getUserInfo()
      userInfo.value = info
      sessionStorage.setItem('userInfo', JSON.stringify(info))
    } catch (error) {
      console.error('Failed to fetch user info:', error)
      // 即使获取失败，也不影响页面渲染
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
  }

  return { token, userInfo, isLoggedIn, login, register, fetchUserInfo, fetchCaptcha, logout }
})
