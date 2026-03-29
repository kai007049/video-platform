<template>
  <div class="layout" :class="{ 'sidebar-collapsed': sidebarCollapsed, 'message-sidebar-open': showMessageSidebar }">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="header-left" :class="{ 'search-active': isSearchActive }">
        <button class="menu-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <span></span><span></span><span></span>
        </button>
        <router-link to="/" class="logo">
          <span class="logo-text">bilibili</span>
        </router-link>
      </div>

      <div class="header-right" :class="{ 'search-active': isSearchActive }">
        <div class="search-wrap" :class="{ 'search-active': isSearchActive }" ref="searchWrapRef">
          <div class="search-bar">
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索视频、用户"
              @focus="activateSearch($event)"
              @input="activateSearch($event)"
              @keyup.enter="goSearch"
              @blur="handleSearchBlur"
            />
            <button class="search-btn" @click="goSearch" @mousedown.prevent>
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
          <router-link to="/upload" class="upload-btn" :class="{ 'search-active': isSearchActive }">+ 投稿</router-link>
          <div class="user-area" @click="showUserMenu = !showUserMenu" :class="{ 'search-active': isSearchActive }">
            <img :src="resolveAvatar(userStore.userInfo?.avatar)" class="avatar" alt="avatar" />
            <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <label class="menu-item avatar-upload" :class="{ disabled: isUploadingAvatar }">
                <input type="file" accept="image/*" @change="onAvatarChange" :disabled="isUploadingAvatar" />
                {{ isUploadingAvatar ? '上传中...' : '修改头像' }}
              </label>
              <div class="menu-item" @click="goCreator">个人中心</div>
              <div class="menu-item" @click="openMessageSidebar">消息中心</div>
              <router-link v-if="isAdmin" class="menu-item admin" to="/admin" @click="showUserMenu = false">管理面板</router-link>
              <div class="menu-divider"></div>
              <div class="menu-item logout" @click="handleLogout">退出登录</div>
            </div>
          </div>
        </template>
        <template v-else>
          <button class="btn-login" @click="openLogin" :class="{ 'search-active': isSearchActive }">登录</button>
          <button class="btn-register" @click="openRegister" :class="{ 'search-active': isSearchActive }">注册</button>
        </template>
      </div>
    </header>

    <!-- 左侧侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-inner">

        <router-link class="side-item" :class="{ active: $route.path === '/' }" to="/" title="首页">
          <span class="side-icon">🏠</span>
          <span class="side-label">首页</span>
        </router-link>
        <div class="side-item" @click="toggleMessageSidebar" title="消息">
          <span class="side-icon">💬</span>
          <span class="side-label">消息</span>
          <span v-if="messageUnreadCount + notificationUnreadCount + systemUnreadCount > 0" class="message-badge">{{ messageUnreadCount + notificationUnreadCount + systemUnreadCount }}</span>
        </div>
        <router-link class="side-item" :class="{ active: $route.path === '/upload' }" to="/upload" title="投稿">
          <span class="side-icon">📤</span>
          <span class="side-label">投稿</span>
        </router-link>

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

    <!-- 消息侧边栏 -->
    <div class="message-sidebar" :class="{ 'open': showMessageSidebar }">
      <div class="message-sidebar-header">
        <h3>消息</h3>
        <button class="close-btn" @click="showMessageSidebar = false">×</button>
      </div>
      <div class="message-sidebar-content">
        <div class="message-tabs">
          <button class="message-tab" :class="{ active: messageActiveTab === 'message' }" @click="messageActiveTab = 'message'">
            <span class="tab-icon">💬</span>
            <span class="tab-label">私信</span>
            <span v-if="messageUnreadCount > 0" class="unread-badge">{{ messageUnreadCount }}</span>
          </button>
          <button class="message-tab" :class="{ active: messageActiveTab === 'notification' }" @click="messageActiveTab = 'notification'">
            <span class="tab-icon">🔔</span>
            <span class="tab-label">通知</span>
            <span v-if="notificationUnreadCount > 0" class="unread-badge">{{ notificationUnreadCount }}</span>
          </button>
          <button class="message-tab" :class="{ active: messageActiveTab === 'system' }" @click="messageActiveTab = 'system'">
            <span class="tab-icon">📢</span>
            <span class="tab-label">系统通知</span>
            <span v-if="systemUnreadCount > 0" class="unread-badge">{{ systemUnreadCount }}</span>
          </button>
        </div>
        <div class="message-content">
          <div v-if="messageActiveTab === 'message'" class="message-list">
            <div class="message-item" v-for="i in 5" :key="i" @click="markAsRead('message')">
              <div class="message-avatar">
                <img src="https://i0.hdslb.com/bfs/face/8a2718d1c7081c990c436b02d357a3704684751e.jpg@68w_68h_1c_1s.jpg" alt="" />
              </div>
              <div class="message-info">
                <div class="message-name">用户{{ i }}</div>
                <div class="message-preview">这是一条私信消息预览...</div>
              </div>
              <div class="message-time">10:0{{ i }}</div>
            </div>
          </div>
          <div v-if="messageActiveTab === 'notification'" class="notification-list">
            <div class="notification-item" v-for="i in 5" :key="i" @click="markAsRead('notification')">
              <div class="notification-icon">⭐</div>
              <div class="notification-content">
                <div class="notification-title">收到了{{ i }}个赞</div>
                <div class="notification-time">今天 10:0{{ i }}</div>
              </div>
            </div>
          </div>
          <div v-if="messageActiveTab === 'system'" class="system-list">
            <div class="system-item" v-for="i in 3" :key="i" @click="markAsRead('system')">
              <div class="system-icon">📢</div>
              <div class="system-content">
                <div class="system-title">系统通知{{ i }}</div>
                <div class="system-text">这是一条系统通知内容...</div>
                <div class="system-time">2024-03-27</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>


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
const searchWrapCenterRef = ref(null)
const sidebarCollapsed = ref(false)
const isSearchActive = ref(false)
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const isUploadingAvatar = ref(false)
const maxAvatarSize = 2 * 1024 * 1024

// 消息侧边栏相关状态
const showMessageSidebar = ref(false)
const messageActiveTab = ref('message')
const messageUnreadCount = ref(3)
const notificationUnreadCount = ref(5)
const systemUnreadCount = ref(2)
const isAdmin = computed(() => {
  const v = userStore.userInfo?.isAdmin
  return v === true || v === 1 || v === '1'
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
  // 模拟登录状态，用于开发测试
  if (!userStore.isLoggedIn) {
    userStore.mockLogin()
  }
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

function activateSearch(e) {
  isSearchActive.value = true
  showSearchPanel.value = true
  loadSearchPanelData()
  
  // 鼠标跟随搜索框移动至中间
  if (e && e.clientX && e.clientY) {
    // 获取搜索框的最终位置（页面中间）
    const centerX = window.innerWidth / 2
    const centerY = 30 // 搜索框的垂直位置
    
    // 平滑移动鼠标到中间位置
    // 使用requestAnimationFrame实现平滑动画
    let startTime = null
    const startX = e.clientX
    const startY = e.clientY
    const duration = 300 // 与CSS动画时间匹配
    
    function moveMouse(currentTime) {
      if (!startTime) startTime = currentTime
      const elapsed = currentTime - startTime
      const progress = Math.min(elapsed / duration, 1)
      
      // 使用缓动函数使移动更自然
      const easeProgress = 1 - Math.pow(1 - progress, 3)
      
      const currentX = startX + (centerX - startX) * easeProgress
      const currentY = startY + (centerY - startY) * easeProgress
      
      // 移动鼠标（这里使用模拟方法，实际浏览器不允许直接控制鼠标位置）
      // 注意：由于浏览器安全限制，无法直接控制鼠标位置
      // 这里仅做示例，实际效果可能有限
      
      if (progress < 1) {
        requestAnimationFrame(moveMouse)
      }
    }
    
    requestAnimationFrame(moveMouse)
  }
}

function handleSearchBlur() {
  // 延迟处理，以便点击搜索按钮时能正常触发
  setTimeout(() => {
    // 检查当前焦点元素是否在搜索框或搜索面板内
    const searchWrap = searchWrapRef.value
    if (searchWrap && !searchWrap.contains(document.activeElement)) {
      closeSearchPanel()
    }
  }, 200)
}

function closeSearchPanel() {
  showSearchPanel.value = false
  // 延迟重置搜索状态，让CSS动画能够完整显示
  setTimeout(() => {
    isSearchActive.value = false
    keyword.value = ''
  }, 300) // 与CSS transition时间匹配
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
  // 当点击搜索框外部时关闭，无论搜索面板是否显示
  if (!el.contains(e.target)) {
    closeSearchPanel()
  }
}

function goSearch() {
  const value = keyword.value.trim()
  if (!value) {
    // 当搜索框为空时，触发搜索框的动画效果
    activateSearch()
    return
  }
  closeSearchPanel()
  router.push({ path: '/search', query: { keyword: value, type: 'comprehensive', sortBy: 'comprehensive' } })
}

function toggleMessageSidebar() {
  showMessageSidebar.value = !showMessageSidebar.value
}

// 打开消息侧边栏并关闭用户菜单
function openMessageSidebar() {
  showMessageSidebar.value = true
  showUserMenu.value = false
}

function markAsRead(type) {
  if (type === 'message' && messageUnreadCount.value > 0) {
    messageUnreadCount.value--
  } else if (type === 'notification' && notificationUnreadCount.value > 0) {
    notificationUnreadCount.value--
  } else if (type === 'system' && systemUnreadCount.value > 0) {
    systemUnreadCount.value--
  }
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
  transition: all 0.3s ease;
}

.header-left.search-active {
  transform: translateX(-20px);
  opacity: 0.7;
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
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.search-wrap.search-active {
  width: 600px;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 18px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.search-wrap.search-active .search-panel {
  width: 600px;
  left: 0;
  right: 0;
  margin: 0 auto;
}

.search-bar {
  display: flex;
  align-items: center;
  width: 100%;
  height: 36px;
  background: #f1f2f3;
  border-radius: 18px;
  padding: 0 6px 0 16px;
  transition: all 0.2s;
}

.search-panel {
  position: absolute;
  top: 44px;
  left: 0;
  width: 100%;
  background: rgba(255, 255, 255, 0.98);
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
  transition: all 0.3s ease;
}

.header-right.search-active {
  opacity: 0.7;
  gap: 8px;
}

.header-right.search-active .upload-btn,
.header-right.search-active .user-area,
.header-right.search-active .btn-login,
.header-right.search-active .btn-register {
  opacity: 0.7;
  transform: scale(0.95);
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
  transition: all 0.3s ease;
}

.upload-btn.search-active {
  transform: translateX(10px);
  opacity: 0.7;
}

.user-area {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.user-area.search-active {
  transform: translateX(10px);
  opacity: 0.7;
}

.btn-login {
  padding: 6px 16px;
  font-size: 14px;
  border-radius: 6px;
  border: 1px solid #e3e5e7;
  background: #fff;
  color: #18191c;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-login.search-active {
  transform: translateX(10px);
  opacity: 0.7;
}

.btn-register {
  padding: 6px 16px;
  font-size: 14px;
  border-radius: 6px;
  border: none;
  background: #fb7299;
  color: #fff;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-register.search-active {
  transform: translateX(10px);
  opacity: 0.7;
}

.upload-btn:hover {
  border-color: #fb7299;
  color: #fb7299;
}

.btn-register:hover { background: #fc87ad; }

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
  position: relative;
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

.message-badge {
  position: absolute;
  top: 8px;
  right: 12px;
  background: #fb7299;
  color: #fff;
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 10px;
  min-width: 16px;
  text-align: center;
}

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

.layout.message-sidebar-open .main {
  margin-left: 540px;
}

.layout.sidebar-collapsed.message-sidebar-open .main {
  margin-left: 420px;
}

/* ===== 消息侧边栏 ===== */


.message-sidebar {
  position: fixed;
  top: 60px;
  left: -400px;
  width: 360px;
  height: calc(100vh - 60px);
  background: #fff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  z-index: 60;
  display: flex;
  flex-direction: column;
  transition: left 0.3s ease;
}

.message-sidebar.open {
  left: 0;
}

.layout.sidebar-collapsed .message-sidebar.open {
  left: 0;
}

.message-sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e3e5e7;
}

.message-sidebar-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #18191c;
  margin: 0;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: #61666d;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: #f4f5f7;
}

.message-sidebar-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.message-tabs {
  display: flex;
  border-bottom: 1px solid #e3e5e7;
}

.message-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 0;
  border: none;
  background: transparent;
  color: #61666d;
  font-size: 14px;
  cursor: pointer;
  position: relative;
}

.message-tab.active {
  color: #fb7299;
  font-weight: 500;
}

.message-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: #fb7299;
}

.tab-icon {
  font-size: 16px;
}

.unread-badge {
  background: #fb7299;
  color: #fff;
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 10px;
  min-width: 16px;
  text-align: center;
}

.message-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.message-list, .notification-list, .system-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.message-item:hover {
  background: #f6f7f8;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.message-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-info {
  flex: 1;
  min-width: 0;
}

.message-name {
  font-size: 14px;
  font-weight: 500;
  color: #18191c;
  margin-bottom: 4px;
}

.message-preview {
  font-size: 13px;
  color: #61666d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-time {
  font-size: 12px;
  color: #9499a0;
  flex-shrink: 0;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.notification-item:hover {
  background: #f6f7f8;
}

.notification-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.notification-content {
  flex: 1;
}

.notification-title {
  font-size: 14px;
  color: #18191c;
  margin-bottom: 4px;
}

.notification-time {
  font-size: 12px;
  color: #9499a0;
}

.system-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.system-item:hover {
  background: #f6f7f8;
}

.system-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.system-content {
  flex: 1;
}

.system-title {
  font-size: 14px;
  font-weight: 500;
  color: #18191c;
  margin-bottom: 4px;
}

.system-text {
  font-size: 13px;
  color: #61666d;
  margin-bottom: 4px;
  line-height: 1.4;
}

.system-time {
  font-size: 12px;
  color: #9499a0;
}



@media (max-width: 1024px) {
  .sidebar { width: 60px; }
  .side-label { opacity: 0; pointer-events: none; }
  .side-section-title { opacity: 0; }
  .main { margin-left: 60px; }
  .search-wrap { width: 300px; }
  .search-wrap-center.active { width: 500px; }
}

@media (max-width: 768px) {
  .sidebar { display: none; }
  .main { margin-left: 0; padding: 16px; }
  .search-wrap { width: 200px; }
  .search-wrap-center.active { width: 300px; }
  .username { display: none; }
  .upload-btn { display: none; }
}
</style>
