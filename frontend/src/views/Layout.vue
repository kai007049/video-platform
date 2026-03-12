<template>
  <div class="layout">
    <header class="header">
      <router-link to="/" class="logo">
        <span class="logo-icon">b</span>
        <span class="logo-text">哔哩哔哩</span>
      </router-link>
      <div class="nav-center">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">首页</router-link>
        <router-link to="/upload" class="nav-item">投稿</router-link>
        <router-link to="/message" class="nav-item">消息</router-link>
        <router-link to="/creator" class="nav-item" v-if="userStore.isLoggedIn">个人中心</router-link>
        <router-link to="/admin" class="nav-item" v-if="userStore.userInfo?.isAdmin">管理后台</router-link>
      </div>
      <div class="nav-right">
        <div class="search-bar">
          <input v-model="keyword" type="text" placeholder="请输入感兴趣的内容" @keyup.enter="goSearch" />
          <button class="search-btn" @click="goSearch">搜索</button>
        </div>
        <button class="theme-toggle" @click="toggleTheme">
          {{ themeLabel }}
        </button>
        <template v-if="userStore.isLoggedIn">
          <div class="user-area" @click="showUserMenu = !showUserMenu">
            <img :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar" alt="avatar" />
            <span class="username">{{ userStore.userInfo?.username }}</span>
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <div class="menu-item" @click="goCreator">个人中心</div>
              <div class="menu-item" @click="handleLogout">退出登录</div>
            </div>
          </div>
        </template>
        <template v-else>
          <button class="btn-login" @click="openLogin">登录</button>
          <button class="btn-register" @click="openRegister">注册</button>
        </template>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const showUserMenu = ref(false)
const keyword = ref('')
const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=default'

const theme = ref(localStorage.getItem('theme') || 'light')
const themeLabel = computed(() => (theme.value === 'dark' ? '亮色' : '暗色'))

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
  applyTheme()
})

function openLogin() {
  router.push({ path: route.path, query: { ...route.query, login: '1' } })
}

function openRegister() {
  router.push({ path: route.path, query: { ...route.query, register: '1' } })
}

function goCreator() {
  router.push('/creator')
  showUserMenu.value = false
}

function handleLogout() {
  userStore.logout()
  showUserMenu.value = false
  router.push('/')
}

function applyTheme() {
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('theme', theme.value)
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme()
}

function goSearch() {
  const value = keyword.value.trim()
  if (!value) return
  router.push({ path: '/', query: { ...route.query, keyword: value, tab: 'search' } })
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--bg-page);
}

.header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 20px;
  height: 70px;
  padding: 0 32px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: var(--bg-surface);
}

.logo-icon {
  width: 32px;
  height: 32px;
  line-height: 32px;
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 8px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.nav-center {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav-item {
  padding: 8px 20px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: all 0.2s;
}

.nav-item:hover,
.nav-item.active {
  color: var(--bili-pink);
  border-color: rgba(251,114,153,.5);
  box-shadow: 0 2px 6px rgba(251,114,153,.2);
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-bar {
  display: flex;
  align-items: center;
  width: 320px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 6px 12px;
}

.search-bar input {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
}

.search-btn {
  background: transparent;
  font-size: 14px;
  color: var(--bili-pink);
  border: 1px solid rgba(251,114,153,.3);
  border-radius: 999px;
  padding: 4px 10px;
}

.theme-toggle {
  padding: 8px 12px;
  font-size: 13px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-primary);
}

.btn-login,
.btn-register {
  padding: 8px 18px;
  font-size: 14px;
  border-radius: 12px;
  transition: all 0.2s;
}

.btn-login {
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
}

.btn-register {
  color: #fff;
  background: var(--bili-pink);
}

.btn-register:hover {
  background: var(--bili-pink-hover);
}

.btn-upload {
  padding: 8px 18px;
  font-size: 14px;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 12px;
  transition: background 0.2s;
}

.btn-upload:hover {
  background: var(--bili-pink-hover);
}

.user-area {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 10px;
  cursor: pointer;
}

.user-area:hover {
  background: var(--bg-gray);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  font-size: 14px;
  color: var(--text-primary);
}

.user-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 8px;
  padding: 8px 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: var(--card-shadow);
  min-width: 120px;
}

.menu-item {
  padding: 10px 16px;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
}

.menu-item:hover {
  background: var(--bg-gray);
}

.main {
  padding: 24px 32px;
  max-width: 1400px;
  margin: 0 auto;
}
</style>
