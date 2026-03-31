<template>
  <div class="layout" :class="{ 'sidebar-collapsed': sidebarCollapsed, 'message-sidebar-open': showMessageSidebar }">
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
              @focus="activateSearch"
              @input="activateSearch"
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
                <button
                  v-for="item in searchHistory"
                  :key="`history-${item}`"
                  class="search-tag"
                  @click="clickSuggest(item)"
                >
                  {{ item }}
                </button>
              </div>
              <div v-else class="panel-empty">暂无搜索历史</div>
            </div>

            <div class="panel-section">
              <div class="panel-title-row">
                <span class="panel-title">热门搜索</span>
              </div>
              <div v-if="hotSearches.length" class="hot-list">
                <button
                  v-for="(item, index) in hotSearches"
                  :key="`hot-${item}`"
                  class="hot-item"
                  @click="clickSuggest(item)"
                >
                  <span class="rank">{{ index + 1 }}</span>
                  <span class="hot-text">{{ item }}</span>
                </button>
              </div>
              <div v-else class="panel-empty">暂无热门搜索</div>
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

    <aside class="sidebar">
      <div class="sidebar-inner">
        <router-link class="side-item" :class="{ active: $route.path === '/' }" to="/" title="首页">
          <span class="side-icon">🏠</span>
          <span class="side-label">首页</span>
        </router-link>
        <div class="side-item" @click="toggleMessageSidebar" title="消息">
          <span class="side-icon">💬</span>
          <span class="side-label">消息</span>
          <span v-if="totalUnreadCount > 0" class="message-badge">{{ totalUnreadCount }}</span>
        </div>
        <router-link class="side-item" :class="{ active: $route.path === '/upload' }" to="/upload" title="投稿">
          <span class="side-icon">📹</span>
          <span class="side-label">投稿</span>
        </router-link>
        <router-link class="side-item" to="/?tab=recommend" title="推荐">
          <span class="side-icon">✨</span>
          <span class="side-label">推荐</span>
        </router-link>
        <router-link class="side-item" to="/?tab=hot" title="热门">
          <span class="side-icon">🔥</span>
          <span class="side-label">热门</span>
        </router-link>
        <router-link class="side-item" to="/?tab=latest" title="最新">
          <span class="side-icon">🆕</span>
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

    <div class="message-sidebar" :class="{ open: showMessageSidebar }">
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
            <span class="tab-label">系统</span>
            <span v-if="systemUnreadCount > 0" class="unread-badge">{{ systemUnreadCount }}</span>
          </button>
        </div>

        <div class="message-content">
          <div v-if="!userStore.isLoggedIn" class="empty-panel">登录后可查看消息</div>

          <template v-else>
            <div v-if="sidebarLoading" class="empty-panel">加载中...</div>

            <div v-else-if="messageActiveTab === 'message'" class="message-list">
              <div
                v-for="item in conversations"
                :key="item.targetId"
                class="message-item"
                @click="goConversation(item)"
              >
                <div class="message-avatar">
                  <img :src="resolveAvatar(item.targetAvatar)" alt="avatar" />
                </div>
                <div class="message-info">
                  <div class="message-name">{{ item.targetName || `用户 ${item.targetId}` }}</div>
                  <div class="message-preview">{{ item.lastContent || '暂无消息内容' }}</div>
                </div>
                <div class="message-time">
                  <div>{{ formatDateTime(item.lastTime) }}</div>
                  <span v-if="item.unread" class="inline-badge">{{ item.unread }}</span>
                </div>
              </div>
              <div v-if="!conversations.length" class="empty-panel">暂无私信会话</div>
            </div>

            <div v-else-if="messageActiveTab === 'notification'" class="notification-list">
              <div
                v-for="item in interactionNotifications"
                :key="item.id"
                class="notification-item"
                @click="markNotificationRead(item)"
              >
                <div class="notification-icon">🔔</div>
                <div class="notification-content">
                  <div class="notification-title">{{ item.content }}</div>
                  <div class="notification-time">{{ formatDateTime(item.createTime) }}</div>
                </div>
              </div>
              <div v-if="!interactionNotifications.length" class="empty-panel">暂无互动通知</div>
            </div>

            <div v-else class="system-list">
              <div
                v-for="item in systemNotifications"
                :key="item.id"
                class="system-item"
                @click="markNotificationRead(item)"
              >
                <div class="system-icon">📢</div>
                <div class="system-content">
                  <div class="system-title">{{ item.type || '系统通知' }}</div>
                  <div class="system-text">{{ item.content }}</div>
                  <div class="system-time">{{ formatDateTime(item.createTime) }}</div>
                </div>
              </div>
              <div v-if="!systemNotifications.length" class="empty-panel">暂无系统通知</div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { updateAvatar } from '../api/user'
import { clearSearchHistory, getHotSearches, getSearchHistory } from '../api/search'
import { getConversations, getMessageSummary, getNotifications, readNotificationApi } from '../api/message'
import LoginModal from '../components/LoginModal.vue'
import RegisterModal from '../components/RegisterModal.vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const showUserMenu = ref(false)
const showLogin = ref(route.query.login === '1')
const showRegister = ref(route.query.register === '1')
const keyword = ref('')
const showSearchPanel = ref(false)
const searchHistory = ref([])
const hotSearches = ref([])
const searchWrapRef = ref(null)
const sidebarCollapsed = ref(false)
const isSearchActive = ref(false)
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const isUploadingAvatar = ref(false)
const maxAvatarSize = 2 * 1024 * 1024

const showMessageSidebar = ref(false)
const messageActiveTab = ref('message')
const messageUnreadCount = ref(0)
const notificationUnreadCount = ref(0)
const systemUnreadCount = ref(0)
const sidebarLoading = ref(false)
const conversations = ref([])
const interactionNotifications = ref([])
const systemNotifications = ref([])

const isAdmin = computed(() => {
  const value = userStore.userInfo?.isAdmin
  return value === true || value === 1 || value === '1'
})

const totalUnreadCount = computed(
  () => messageUnreadCount.value + notificationUnreadCount.value + systemUnreadCount.value
)

function resolveAvatar(avatar) {
  if (!avatar) {
    return avatarPlaceholder
  }
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) {
    return avatar
  }
  if (avatar.startsWith('/api/file/avatar') || avatar.startsWith('/file/avatar')) {
    return avatar
  }
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

/**
 * 上传头像后同步本地用户信息，保证右上角头像即时更新。
 */
async function onAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file || isUploadingAvatar.value) {
    return
  }
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
  } catch (error) {
    console.error(error)
    alert('头像上传失败')
  } finally {
    isUploadingAvatar.value = false
  }
}

/**
 * 加载右侧搜索面板的数据源。
 */
async function loadSearchPanelData() {
  try {
    const [history, hot] = await Promise.all([
      getSearchHistory(10).catch(() => []),
      getHotSearches(10).catch(() => [])
    ])
    searchHistory.value = Array.isArray(history) ? history : []
    hotSearches.value = Array.isArray(hot) ? hot : []
  } catch (error) {
    console.error(error)
  }
}

/**
 * 按后端真实接口加载消息中心摘要、会话和通知列表。
 */
async function loadSidebarMessages() {
  if (!userStore.isLoggedIn) {
    conversations.value = []
    interactionNotifications.value = []
    systemNotifications.value = []
    messageUnreadCount.value = 0
    notificationUnreadCount.value = 0
    systemUnreadCount.value = 0
    return
  }

  sidebarLoading.value = true
  try {
    const [summary, conversationList, notificationPage] = await Promise.all([
      getMessageSummary().catch(() => null),
      getConversations().catch(() => []),
      getNotifications(1, 20).catch(() => ({ records: [] }))
    ])

    messageUnreadCount.value = Number(summary?.messageUnread || 0)
    notificationUnreadCount.value = Number(summary?.notificationUnread || 0)
    systemUnreadCount.value = Number(summary?.systemUnread || 0)
    conversations.value = Array.isArray(conversationList) ? conversationList : []

    const notifications = Array.isArray(notificationPage?.records) ? notificationPage.records : []
    interactionNotifications.value = notifications.filter((item) => item.type !== 'SYSTEM')
    systemNotifications.value = notifications.filter((item) => item.type === 'SYSTEM')
  } finally {
    sidebarLoading.value = false
  }
}

function activateSearch() {
  isSearchActive.value = true
  showSearchPanel.value = true
  loadSearchPanelData()
}

function handleSearchBlur() {
  setTimeout(() => {
    const searchWrap = searchWrapRef.value
    if (searchWrap && !searchWrap.contains(document.activeElement)) {
      closeSearchPanel()
    }
  }, 200)
}

function closeSearchPanel() {
  showSearchPanel.value = false
  setTimeout(() => {
    isSearchActive.value = false
  }, 200)
}

function clickSuggest(text) {
  keyword.value = text || ''
  goSearch()
}

async function handleClearHistory() {
  try {
    await clearSearchHistory()
    searchHistory.value = []
  } catch (error) {
    console.error(error)
  }
}

function onGlobalClick(event) {
  if (showUserMenu.value) {
    const target = event.target
    if (!target.closest('.user-area')) {
      showUserMenu.value = false
    }
  }

  const searchWrap = searchWrapRef.value
  if (searchWrap && !searchWrap.contains(event.target)) {
    closeSearchPanel()
  }
}

function goSearch() {
  const value = keyword.value.trim()
  if (!value) {
    activateSearch()
    return
  }
  closeSearchPanel()
  router.push({
    path: '/search',
    query: {
      keyword: value,
      type: 'comprehensive',
      sortBy: 'comprehensive'
    }
  })
}

function openLogin() {
  const query = { ...route.query, login: '1' }
  delete query.register
  router.push({ path: route.path, query })
}

function openRegister() {
  const query = { ...route.query, register: '1' }
  delete query.login
  router.push({ path: route.path, query })
}

function goCreator() {
  router.push('/creator')
  showUserMenu.value = false
}

function handleLogout() {
  userStore.logout()
  showUserMenu.value = false
  showMessageSidebar.value = false
  router.push('/')
}

async function toggleMessageSidebar() {
  showMessageSidebar.value = !showMessageSidebar.value
  if (showMessageSidebar.value) {
    await loadSidebarMessages()
  }
}

async function openMessageSidebar() {
  showMessageSidebar.value = true
  showUserMenu.value = false
  await loadSidebarMessages()
}

function goConversation(item) {
  showMessageSidebar.value = false
  router.push({
    path: '/message',
    query: {
      targetId: item.targetId,
      targetName: item.targetName
    }
  })
}

/**
 * 点击通知后调用后端已读接口，并同步刷新未读数。
 */
async function markNotificationRead(item) {
  if (!item?.id || item.status === 1) {
    return
  }
  try {
    await readNotificationApi(item.id)
    item.status = 1
    if (item.type === 'SYSTEM') {
      systemUnreadCount.value = Math.max(0, systemUnreadCount.value - 1)
    } else {
      notificationUnreadCount.value = Math.max(0, notificationUnreadCount.value - 1)
    }
  } catch (error) {
    console.error(error)
  }
}

function formatDateTime(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

watch(
  () => route.query,
  (query) => {
    showLogin.value = query.login === '1'
    showRegister.value = query.register === '1'
  },
  { immediate: true }
)

watch(
  () => userStore.userInfo?.id,
  async (id) => {
    if (id) {
      await loadSidebarMessages()
    }
  }
)

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
  if (userStore.isLoggedIn) {
    await loadSidebarMessages()
  }
  document.addEventListener('click', onGlobalClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onGlobalClick)
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f4f5f7;
}

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
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
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
}

.logo-text {
  font-size: 24px;
  font-weight: 900;
  color: #fb7299;
  letter-spacing: -1px;
  font-style: italic;
}

.search-wrap {
  position: relative;
  width: 400px;
  transition: all 0.3s ease;
}

.search-wrap.search-active {
  width: 600px;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 1000;
}

.search-bar {
  display: flex;
  align-items: center;
  width: 100%;
  height: 36px;
  background: #f1f2f3;
  border-radius: 18px;
  padding: 0 6px 0 16px;
}

.search-panel {
  position: absolute;
  top: 44px;
  left: 0;
  width: 100%;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #e3e5e7;
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.12);
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
  cursor: pointer;
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
  cursor: pointer;
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
  cursor: pointer;
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
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.3);
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

.upload-btn,
.btn-login,
.btn-register {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  white-space: nowrap;
}

.upload-btn,
.btn-login {
  border: 1px solid #e3e5e7;
  background: #fff;
  color: #61666d;
}

.btn-register {
  border: none;
  background: #fb7299;
  color: #fff;
}

.user-area {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}

.user-area:hover {
  background: #f4f5f7;
}

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
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  min-width: 120px;
  z-index: 200;
}

.menu-item {
  display: block;
  padding: 8px 16px;
  font-size: 14px;
  color: #18191c;
  cursor: pointer;
  text-decoration: none;
}

.menu-item:hover {
  background: #f4f5f7;
}

.menu-item.admin {
  color: #e53935;
}

.menu-item.logout {
  color: #fb7299;
}

.menu-divider {
  height: 1px;
  background: #e3e5e7;
  margin: 4px 0;
}

.avatar-upload input {
  display: none;
}

.avatar-upload.disabled {
  color: #9499a0;
  cursor: not-allowed;
}

.sidebar {
  position: fixed;
  top: 60px;
  left: 0;
  bottom: 0;
  width: 180px;
  background: #fff;
  border-right: 1px solid #e3e5e7;
  overflow-y: auto;
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
  position: relative;
}

.side-item:hover,
.side-item.active {
  background: #fff0f4;
  color: #fb7299;
}

.side-item.admin-item {
  color: #e53935;
}

.message-badge,
.unread-badge,
.inline-badge {
  background: #fb7299;
  color: #fff;
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 10px;
  min-width: 16px;
  text-align: center;
}

.message-badge {
  position: absolute;
  top: 8px;
  right: 12px;
}

.side-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}

.layout.sidebar-collapsed .side-label {
  opacity: 0;
  pointer-events: none;
}

.side-divider {
  height: 1px;
  background: #e3e5e7;
  margin: 8px 12px;
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

.layout.message-sidebar-open .main {
  margin-left: 540px;
}

.layout.sidebar-collapsed.message-sidebar-open .main {
  margin-left: 420px;
}

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

.message-sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e3e5e7;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: #61666d;
  cursor: pointer;
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

.message-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.message-list,
.notification-list,
.system-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item,
.notification-item,
.system-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
}

.message-item:hover,
.notification-item:hover,
.system-item:hover {
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

.message-info,
.notification-content,
.system-content {
  flex: 1;
  min-width: 0;
}

.message-name,
.system-title {
  font-size: 14px;
  font-weight: 500;
  color: #18191c;
  margin-bottom: 4px;
}

.message-preview,
.system-text {
  font-size: 13px;
  color: #61666d;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-time,
.notification-time,
.system-time {
  font-size: 12px;
  color: #9499a0;
}

.notification-icon,
.system-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.empty-panel {
  padding: 40px 20px;
  text-align: center;
  color: #9499a0;
}

@media (max-width: 1024px) {
  .sidebar {
    width: 60px;
  }

  .side-label {
    opacity: 0;
    pointer-events: none;
  }

  .main {
    margin-left: 60px;
  }

  .search-wrap {
    width: 300px;
  }
}

@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .main {
    margin-left: 0;
    padding: 16px;
  }

  .search-wrap {
    width: 200px;
  }

  .username,
  .upload-btn {
    display: none;
  }
}
</style>
