<template>
  <div class="layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="header-left">
        <button class="menu-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <span></span><span></span><span></span>
        </button>
        <router-link to="/" class="logo">
          <span class="logo-text">bilibili</span>
        </router-link>
      </div>

      <div class="header-center">
      </div>

      <div class="header-right">
        <div class="search-wrap" ref="searchWrapRef">
          <div class="search-bar">
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索视频、用户"
              @focus="openSearchPanel"
              @keyup.enter="goSearch"
            />
            <button class="search-btn" @click="goSearch">
              <span class="search-icon">🔍</span>
            </button>
          </div>

          <div v-if="showSearchPanel" class="search-panel" @mousedown.prevent>
            <div class="panel-section">
              <div class="panel-title-row">
                <span class="panel-title">搜索历史</span>
                <button class="panel-link" @click="handleClearHistory">清空</button>
              </div>
              <div v-if="searchHistory.length" class="tag-list">
                <button v-for="item in searchHistory" :key="`h-${item}`" class="search-tag" @click="clickSuggest(item)">
                  {{ item }}
                </button>
              </div>
              <div v-else class="panel-empty">暂无搜索历史</div>
            </div>

            <div class="panel-section">
              <div class="panel-title-row">
                <span class="panel-title">热搜</span>
              </div>
              <div v-if="hotSearches.length" class="hot-list">
                <button v-for="(item, idx) in hotSearches" :key="`hot-${item}`" class="hot-item" @click="clickSuggest(item)">
                  <span class="rank">{{ idx + 1 }}</span>
                  <span class="hot-text">{{ item }}</span>
                </button>
              </div>
              <div v-else class="panel-empty">暂无热搜</div>
            </div>
          </div>
        </div>
        <template v-if="userStore.isLoggedIn">
          <router-link to="/upload" class="upload-btn">+ 投稿</router-link>
          <div class="user-area" @click="showUserMenu = !showUserMenu">
            <img :src="resolveAvatar(userStore.userInfo?.avatar)" class="avatar" alt="avatar" />
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <label class="menu-item avatar-upload" :class="{ disabled: isUploadingAvatar }">
                <input type="file" accept="image/*" @change="onAvatarChange" :disabled="isUploadingAvatar" />
                {{ isUploadingAvatar ? '上传中...' : '修改头像' }}
              </label>
              <div class="menu-item" @click="goCreator">个人中心</div>
              <router-link class="menu-item" to="/message" @click="showUserMenu = false">消息中心</router-link>
              <router-link v-if="isAdmin" class="menu-item admin" to="/admin" @click="showUserMenu = false">管理面板</router-link>
              <div class="menu-divider"></div>
              <div class="menu-item logout" @click="handleLogout">退出登录</div>
            </div>
          </div>
        </template>
        <template v-else>
          <button class="btn-login" @click="openLogin">登录</button>
          <button class="btn-register" @click="openRegister">注册</button>
        </template>
      </div>
    </header>

    <!-- 左侧侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-inner">
        <button class="side-item side-back" :class="{ disabled: !canBackToHomeFromSearch }" :disabled="!canBackToHomeFromSearch" type="button" @click="goBack" title="返回主页">
          <span class="side-icon">‹</span>
          <span class="side-label">返回</span>
        </button>
        <router-link class="side-item" :class="{ active: $route.path === '/' }" to="/" title="首页">
          <span class="side-icon">🏠</span>
          <span class="side-label">首页</span>
        </router-link>
        <router-link class="side-item" :class="{ active: $route.path === '/message' }" to="/message" title="消息">
          <span class="side-icon">💬</span>
          <span class="side-label">消息</span>
        </router-link>
        <router-link class="side-item" :class="{ active: $route.path === '/upload' }" to="/upload" title="投稿">
          <span class="side-icon">📤</span>
          <span class="side-label">投稿</span>
        </router-link>

        <div class="side-divider"></div>
        <div class="side-section-title">分类</div>

        <router-link class="side-item" to="/?tab=recommend" title="推荐">
          <span class="side-icon">⭐</span>
          <span class="side-label">推荐</span>
        </router-link>
        <router-link class="side-item" to="/?tab=hot" title="热门">
          <span class="side-icon">🔥</span>
          <span class="side-label">热门</span>
        </router-link>
        <router-link class="side-item" to="/?tab=latest" title="最新">
          <span class="side-icon">🕐</span>
          <span class="side-label">最新</span>
        </router-link>

        <template v-if="isAdmin">
          <div class="side-divider"></div>
          <router-link class="side-item admin-item" :class="{ active: $route.path === '/admin' }" to="/admin" title="管理">
            <span class="side-icon">⚙️</span>
            <span class="side-label">管理</span>
          </router-link>
        </template>

        <div class="side-bottom">
          <router-link class="side-item" :class="{ active: $route.path === '/creator' }" to="/creator" title="我的">
            <span class="side-icon">👤</span>
            <span class="side-label">我的</span>
          </router-link>
        </div>
      </div>
    </aside>

    <main class="main">
      <router-view />
    </main>

    <LoginModal v-if="showLogin" :model-value="showLogin" @update:modelValue="showLogin = $event" />
    <RegisterModal v-if="showRegister" :model-value="showRegister" @update:modelValue="showRegister = $event" />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import { updateAvatar } from '../api/user'
import { getSearchHistory, clearSearchHistory, getHotSearches } from '../api/search'
import LoginModal from '../components/LoginModal.vue'
import RegisterModal from '../components/RegisterModal.vue'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const showUserMenu = ref(false)
const showLogin = ref(route.query.login === '1')
const showRegister = ref(route.query.register === '1')
const keyword = ref('')
const showSearchPanel = ref(false)
const searchHistory = ref([])
const hotSearches = ref([])
const searchWrapRef = ref(null)
const sidebarCollapsed = ref(false)
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const isUploadingAvatar = ref(false)
const maxAvatarSize = 2 * 1024 * 1024
const isAdmin = computed(() => {
  const v = userStore.userInfo?.isAdmin
  return v === true || v === 1 || v === '1'
})

const canBackToHomeFromSearch = computed(() => {
  return route.path === '/search' && !!String(route.query.keyword || '').trim()
})

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  if (avatar.startsWith('/api/file/avatar') || avatar.startsWith('/file/avatar')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

async function onAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file || isUploadingAvatar.value) return
  if (!file.type.startsWith('image/')) { alert('请选择图片文件'); return }
  if (file.size > maxAvatarSize) { alert('头像不能超过 2MB'); return }
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
  if (userStore.isLoggedIn && !userStore.userInfo) userStore.fetchUserInfo()
  document.addEventListener('click', onGlobalClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onGlobalClick)
})

watch(
  () => route.query,
  (q) => {
    showLogin.value = q.login === '1'
    showRegister.value = q.register === '1'
  },
  { immediate: true }
)

function openLogin() {
  const q = { ...route.query, login: '1' }
  delete q.register
  router.push({ path: route.path, query: q })
}
function openRegister() {
  const q = { ...route.query, register: '1' }
  delete q.login
  router.push({ path: route.path, query: q })
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

async function loadSearchPanelData() {
  try {
    const [history, hot] = await Promise.all([
      getSearchHistory(10).catch(() => []),
      getHotSearches(10).catch(() => [])
    ])
    searchHistory.value = Array.isArray(history) ? history : []
    hotSearches.value = Array.isArray(hot) ? hot : []
  } catch (e) {
    console.error(e)
  }
}

async function openSearchPanel() {
  showSearchPanel.value = true
  await loadSearchPanelData()
}

function closeSearchPanel() {
  showSearchPanel.value = false
}

function clickSuggest(text) {
  keyword.value = text || ''
  goSearch()
}

async function handleClearHistory() {
  try {
    await clearSearchHistory()
    searchHistory.value = []
  } catch (e) {
    console.error(e)
  }
}

function onGlobalClick(e) {
  const el = searchWrapRef.value
  if (!el) return
  if (!el.contains(e.target)) {
    closeSearchPanel()
  }
}

function goSearch() {
  const value = keyword.value.trim()
  if (!value) return
  closeSearchPanel()
  router.push({ path: '/search', query: { keyword: value, type: 'comprehensive', sortBy: 'comprehensive' } })
}

function goBack() {
  // 仅在“首页的搜索态”生效：回到首页默认推荐流
  if (!canBackToHomeFromSearch.value) return
  router.push({ path: '/', query: {} })
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f4f5f7;
}

/* ===== 顶部导航 ===== */
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0 20px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,.08);
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.menu-toggle {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  width: 32px;
  height: 32px;
  padding: 4px;
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 4px;
}

.menu-toggle:hover {
  background: #f4f5f7;
}

.menu-toggle span {
  display: block;
  height: 2px;
  background: #61666d;
  border-radius: 2px;
  transition: all 0.2s;
}

.logo-text {
  font-size: 24px;
  font-weight: 900;
  color: #fb7299;
  letter-spacing: -1px;
  font-style: italic;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.search-wrap {
  position: relative;
  width: 400px;
}

.search-bar {
  display: flex;
  align-items: center;
  width: 100%;
  height: 36px;
  background: #f1f2f3;
  border-radius: 18px;
  padding: 0 6px 0 16px;
  transition: box-shadow 0.2s;
}

.search-panel {
  position: absolute;
  top: 44px;
  left: 0;
  width: 100%;
  background: #fff;
  border: 1px solid #e3e5e7;
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(0, 0, 0, .12);
  padding: 12px;
  z-index: 250;
}

.panel-section + .panel-section {
  margin-top: 14px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #18191c;
}

.panel-link {
  border: none;
  background: transparent;
  color: #9499a0;
  font-size: 13px;
}

.panel-link:hover {
  color: #fb7299;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.search-tag {
  border: 1px solid #e3e5e7;
  background: #f6f7f8;
  color: #61666d;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 13px;
  max-width: 100%;
}

.search-tag:hover {
  border-color: #fb7299;
  color: #fb7299;
}

.hot-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
}

.hot-item {
  border: none;
  background: transparent;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #18191c;
  padding: 4px 2px;
}

.hot-item:hover .hot-text {
  color: #fb7299;
}

.rank {
  width: 18px;
  color: #9499a0;
  font-weight: 700;
}

.hot-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-empty {
  font-size: 13px;
  color: #9499a0;
}

.search-bar:focus-within {
  box-shadow: 0 0 0 2px rgba(251,114,153,.3);
  background: #fff;
}

.search-bar input {
  flex: 1;
  border: none;
  background: transparent;
  color: #18191c;
  font-size: 14px;
  outline: none;
}

.search-bar input::placeholder {
  color: #9499a0;
}

.search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 30px;
  background: transparent;
  color: #61666d;
  border: none;
  border-radius: 15px;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;
}

.search-btn:hover {
  color: #fb7299;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  justify-content: flex-end;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px solid #e3e5e7;
  border-radius: 6px;
  font-size: 13px;
  color: #61666d;
  white-space: nowrap;
  transition: all 0.2s;
}

.upload-btn:hover {
  border-color: #fb7299;
  color: #fb7299;
}

.btn-login {
  padding: 6px 16px;
  font-size: 14px;
  border-radius: 6px;
  border: 1px solid #e3e5e7;
  background: #fff;
  color: #18191c;
  cursor: pointer;
}

.btn-register {
  padding: 6px 16px;
  font-size: 14px;
  border-radius: 6px;
  border: none;
  background: #fb7299;
  color: #fff;
  cursor: pointer;
}

.btn-register:hover { background: #fc87ad; }

.user-area {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-area:hover { background: #f4f5f7; }

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #e3e5e7;
}

.username {
  font-size: 13px;
  color: #18191c;
  max-width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  padding: 6px 0;
  background: #fff;
  border: 1px solid #e3e5e7;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
  min-width: 120px;
  z-index: 200;
}

.menu-item {
  display: block;
  padding: 8px 16px;
  font-size: 14px;
  color: #18191c;
  cursor: pointer;
  transition: background 0.15s;
  text-decoration: none;
}

.menu-item:hover { background: #f4f5f7; }
.menu-item.admin { color: #e53935; }
.menu-item.logout { color: #fb7299; }

.menu-divider {
  height: 1px;
  background: #e3e5e7;
  margin: 4px 0;
}

.avatar-upload input { display: none; }
.avatar-upload.disabled { color: #9499a0; cursor: not-allowed; }

/* ===== 侧边栏 ===== */
.sidebar {
  position: fixed;
  top: 60px;
  left: 0;
  bottom: 0;
  width: 180px;
  background: #fff;
  border-right: 1px solid #e3e5e7;
  overflow-y: auto;
  overflow-x: hidden;
  z-index: 50;
  transition: width 0.25s ease;
}

.layout.sidebar-collapsed .sidebar {
  width: 60px;
}

.sidebar-inner {
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 60px);
}

.side-bottom {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #e3e5e7;
}

.side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  color: #61666d;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.15s;
  white-space: nowrap;
  overflow: hidden;
}

.side-item:hover {
  background: #fff0f4;
  color: #fb7299;
}

.side-item.active {
  background: #fff0f4;
  color: #fb7299;
  font-weight: 600;
}

.side-item.admin-item { color: #e53935; }
.side-item.admin-item:hover,
.side-item.admin-item.active { background: #fff5f5; color: #c62828; }

.side-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}

.side-label {
  transition: opacity 0.2s;
}

.layout.sidebar-collapsed .side-label {
  opacity: 0;
  pointer-events: none;
}

.layout.sidebar-collapsed .side-section-title {
  opacity: 0;
}

.side-divider {
  height: 1px;
  background: #e3e5e7;
  margin: 8px 12px;
}

.side-section-title {
  padding: 6px 16px;
  font-size: 12px;
  color: #9499a0;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: opacity 0.2s;
}

/* ===== 主内容区 ===== */
.side-back {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
}

.side-back.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.side-back.disabled:hover {
  background: transparent;
  color: #61666d;
}

.side-back .side-icon {
  font-size: 24px;
  font-weight: 500;
  line-height: 1;
}

.main {
  margin-top: 60px;
  margin-left: 180px;
  padding: 20px;
  min-height: calc(100vh - 60px);
  transition: margin-left 0.25s ease;
}

.layout.sidebar-collapsed .main {
  margin-left: 60px;
}

@media (max-width: 1024px) {
  .sidebar { width: 60px; }
  .side-label { opacity: 0; pointer-events: none; }
  .side-section-title { opacity: 0; }
  .main { margin-left: 60px; }
  .search-bar { width: 300px; }
}

@media (max-width: 768px) {
  .sidebar { display: none; }
  .main { margin-left: 0; padding: 16px; }
  .search-bar { width: 200px; }
  .username { display: none; }
  .upload-btn { display: none; }
}
</style>
