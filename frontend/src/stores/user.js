import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, getUserInfo, getCaptcha } from '../api/user'

export const useUserStore = defineStore('user', () => {
  // 使用 sessionStorage 实现“每个浏览器窗口/标签页独立登录”
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
    const info = await getUserInfo()
    userInfo.value = info
    sessionStorage.setItem('userInfo', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('userInfo')
  }

  return { token, userInfo, isLoggedIn, login, register, fetchUserInfo, fetchCaptcha, logout }
})
