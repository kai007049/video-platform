<template>
  <div class="layout">
    <header class="header">
      <div class="header-left">
        <router-link to="/" class="logo">
          <span class="logo-text">bilibili</span>
        </router-link>
        <nav class="top-nav">
          <router-link class="top-nav-item" to="/">直播</router-link>
          <router-link class="top-nav-item" :class="{ active: $route.path === '/' }" to="/">推荐</router-link>
          <router-link class="top-nav-item" :class="{ active: $route.path === '/message' }" to="/message">消息</router-link>
          <router-link class="top-nav-item" :class="{ active: $route.path === '/creator' }" to="/creator">创作者中心</router-link>
          <router-link class="top-nav-item" :class="{ active: $route.path === '/upload' }" to="/upload">投稿</router-link>
          <router-link
            v-if="userStore.userInfo?.isAdmin"
            class="top-nav-item admin-link"
            :class="{ active: $route.path === '/admin' }"
            to="/admin"
          >
            管理面板
          </router-link>
        </nav>
      </div>

      <div class="nav-right">
        <div class="search-bar">
          <input v-model="keyword" type="text" placeholder="搜索你感兴趣的视频" @keyup.enter="goSearch" />
          <button class="search-btn" @click="goSearch">⌕</button>
        </div>
        <template v-if="userStore.isLoggedIn">
          <div class="user-area" @click="showUserMenu = !showUserMenu">
            <img :src="resolveAvatar(userStore.userInfo?.avatar)" class="avatar" alt="avatar" />
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <label class="menu-item avatar-upload" :class="{ disabled: isUploadingAvatar }">
                <input type="file" accept="image/*" @change="onAvatarChange" :disabled="isUploadingAvatar" />
                {{ isUploadingAvatar ? '上传中...' : '修改头像' }}
              </label>
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

    <aside class="side-nav">
      <router-link class="side-item" :class="{ active: $route.path === '/' }" to="/">
        <span class="side-icon">🏠</span>
        <span class="side-label">首页</span>
      </router-link>
      <router-link class="side-item" to="/">
        <span class="side-icon">▶</span>
        <span class="side-label">精选</span>
      </router-link>
      <router-link class="side-item" :class="{ active: $route.path === '/message' }" to="/message">
        <span class="side-icon">✦</span>
        <span class="side-label">动态</span>
      </router-link>
      <router-link class="side-item" :class="{ active: $route.path === '/creator' }" to="/creator">
        <span class="side-icon">👤</span>
        <span class="side-label">我的</span>
      </router-link>
    </aside>

    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { updateAvatar } from '../api/user'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const showUserMenu = ref(false)
const keyword = ref('')
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const isUploadingAvatar = ref(false)
const maxAvatarSize = 2 * 1024 * 1024

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

async function onAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file || isUploadingAvatar.value) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > maxAvatarSize) {
    alert('头像不能超过 2MB')
    return
  }
  try {
    isUploadingAvatar.value = true
    const formData = new FormData()
    formData.append('avatar', file)
    const objectName = await updateAvatar(formData)
    if (userStore.userInfo) {
      userStore.userInfo.avatar = objectName
      sessionStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    }
    alert('头像更新成功')
  } catch (e) {
    console.error(e)
    alert('头像上传失败')
  } finally {
    isUploadingAvatar.value = false
  }
}

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
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
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 72px;
  padding: 0 24px 0 28px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.logo {
  color: var(--bili-pink);
}

.logo-text {
  font-size: 42px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: -1px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 26px;
}

.top-nav-item {
  position: relative;
  padding: 6px 0;
  font-size: 14px;
  color: var(--text-primary);
  transition: color 0.2s;
}

.top-nav-item:hover,
.top-nav-item.active {
  color: var(--bili-pink);
}

.top-nav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -17px;
  height: 3px;
  border-radius: 999px;
  background: var(--bili-pink);
}

.admin-link {
  color: #e53935;
  font-weight: 600;
}

.admin-link:hover,
.admin-link.active {
  color: #c62828;
}

.admin-link.active::after {
  background: #c62828;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.search-bar {
  display: flex;
  align-items: center;
  width: 430px;
  height: 42px;
  background: #f1f2f3;
  border-radius: 8px;
  padding: 0 10px 0 14px;
}

.search-bar input {
  flex: 1;
  border: none;
  background: transparent;
  color: #61666d;
  font-size: 14px;
  outline: none;
}

.search-btn {
  background: transparent;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  font-size: 18px;
  color: #61666d;
}

.btn-login,
.btn-register {
  padding: 7px 14px;
  font-size: 13px;
  border-radius: 7px;
}

.btn-login {
  color: #18191c;
  background: #fff;
  border: 1px solid var(--border-color);
}

.btn-register {
  color: #fff;
  background: var(--bili-pink);
}

.user-area {
  position: relative;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
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

.avatar-upload input {
  display: none;
}

.avatar-upload.disabled {
  color: var(--text-secondary);
  cursor: not-allowed;
}

.main {
  margin-left: 86px;
  padding: 20px 20px 28px;
}

.side-nav {
  position: fixed;
  top: 86px;
  left: 10px;
  width: 64px;
  z-index: 30;
  border-radius: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  padding: 10px 0;
}

.side-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 10px 0;
  color: #61666d;
}

.side-item.active {
  color: var(--bili-pink);
}

.side-icon {
  font-size: 16px;
  line-height: 1;
}

.side-label {
  font-size: 12px;
}

@media (max-width: 1280px) {
  .search-bar {
    width: 320px;
  }
}

@media (max-width: 1024px) {
  .top-nav {
    display: none;
  }

  .side-nav {
    display: none;
  }

  .main {
    margin-left: 0;
  }

  .search-bar {
    width: 260px;
  }
}
</style>
