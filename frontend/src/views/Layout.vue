<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <router-link class="brand" to="/">VP</router-link>

      <nav class="nav-list">
        <router-link class="nav-item" :class="{ active: $route.path === '/' }" to="/" title="首页">首页</router-link>
        <router-link class="nav-item" to="/?tab=recommend" title="推荐">推荐</router-link>
        <router-link class="nav-item" to="/?tab=hot" title="热门">热门</router-link>
        <router-link class="nav-item" to="/?tab=latest" title="最新">最新</router-link>
        <router-link class="nav-item" :class="{ active: $route.path === '/upload' }" to="/upload" title="投稿">投稿</router-link>
        <router-link class="nav-item" :class="{ active: $route.path === '/message' }" to="/message" title="消息">消息</router-link>
        <router-link class="nav-item" :class="{ active: $route.path === '/creator' }" to="/creator" title="我的">我的</router-link>
        <router-link v-if="isAdmin" class="nav-item danger" :class="{ active: $route.path === '/admin' }" to="/admin" title="管理">管理</router-link>
      </nav>

      <button class="nav-item message-entry" @click="openMessageSidebar">
        站内通知
        <span v-if="totalUnreadCount > 0" class="badge">{{ totalUnreadCount }}</span>
      </button>
    </aside>

    <div class="app-main-wrap">
      <header class="topbar">
        <router-link to="/" class="title">Video Platform</router-link>

        <div class="search-wrap" ref="searchWrapRef">
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
            <button class="search-btn" @click="goSearch" @mousedown.prevent>搜索</button>
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

        <div class="actions">
          <template v-if="userStore.isLoggedIn">
            <router-link to="/upload" class="btn btn-ghost">投稿</router-link>
            <div class="user-area" @click="showUserMenu = !showUserMenu">
              <img :src="resolveAvatar(userStore.userInfo?.avatar)" class="avatar" alt="avatar" />
              <span class="username">{{ userStore.userInfo?.username || '用户' }}</span>
              <div v-if="showUserMenu" class="user-menu" @click.stop>
                <label class="menu-item avatar-upload" :class="{ disabled: isUploadingAvatar }">
                  <input type="file" accept="image/*" @change="onAvatarChange" :disabled="isUploadingAvatar" />
                  {{ isUploadingAvatar ? '上传中...' : '修改头像' }}
                </label>
                <div class="menu-item" @click="goCreator">个人中心</div>
                <div class="menu-item" @click="openMessageSidebar">消息中心</div>
                <router-link v-if="isAdmin" class="menu-item" to="/admin" @click="showUserMenu = false">管理面板</router-link>
                <div class="menu-divider"></div>
                <div class="menu-item danger" @click="handleLogout">退出登录</div>
              </div>
            </div>
          </template>

          <template v-else>
            <button class="btn btn-ghost" @click="openLogin">登录</button>
            <button class="btn btn-primary" @click="openRegister">注册</button>
          </template>
        </div>
      </header>

      <main class="app-content">
        <router-view />
      </main>
    </div>

    <LoginModal v-if="showLogin" :model-value="showLogin" @update:modelValue="showLogin = $event" />
    <RegisterModal v-if="showRegister" :model-value="showRegister" @update:modelValue="showRegister = $event" />

    <div class="message-drawer" :class="{ open: showMessageSidebar }">
      <div class="drawer-header">
        <h3>消息中心</h3>
        <button class="close-btn" @click="showMessageSidebar = false">×</button>
      </div>

      <div class="drawer-tabs">
        <button class="drawer-tab" :class="{ active: messageActiveTab === 'message' }" @click="messageActiveTab = 'message'">
          私信 <span v-if="messageUnreadCount > 0" class="badge">{{ messageUnreadCount }}</span>
        </button>
        <button class="drawer-tab" :class="{ active: messageActiveTab === 'notification' }" @click="messageActiveTab = 'notification'">
          通知 <span v-if="notificationUnreadCount > 0" class="badge">{{ notificationUnreadCount }}</span>
        </button>
        <button class="drawer-tab" :class="{ active: messageActiveTab === 'system' }" @click="messageActiveTab = 'system'">
          系统 <span v-if="systemUnreadCount > 0" class="badge">{{ systemUnreadCount }}</span>
        </button>
      </div>

      <div class="drawer-content">
        <div v-if="!userStore.isLoggedIn" class="empty-panel">登录后可查看消息</div>
        <div v-else-if="sidebarLoading" class="empty-panel">加载中...</div>

        <div v-else-if="messageActiveTab === 'message'" class="list-wrap">
          <div v-for="item in conversations" :key="item.targetId" class="list-item" @click="goConversation(item)">
            <img :src="resolveAvatar(item.targetAvatar)" class="tiny-avatar" alt="avatar" />
            <div class="list-main">
              <div class="list-title">{{ item.targetName || `用户 ${item.targetId}` }}</div>
              <div class="list-sub">{{ formatConversationPreview(item.lastContent) }}</div>
            </div>
            <div class="list-right">
              <span class="time">{{ formatDateTime(item.lastTime) }}</span>
              <span v-if="item.unread" class="badge">{{ item.unread }}</span>
            </div>
          </div>
          <div v-if="!conversations.length" class="empty-panel">暂无私信会话</div>
        </div>

        <div v-else-if="messageActiveTab === 'notification'" class="list-wrap">
          <div v-for="item in interactionNotifications" :key="item.id" class="list-item" @click="markNotificationRead(item)">
            <div class="list-main">
              <div class="list-title">互动通知</div>
              <div class="list-sub">{{ item.content }}</div>
            </div>
            <div class="list-right">
              <span class="time">{{ formatDateTime(item.createTime) }}</span>
            </div>
          </div>
          <div v-if="!interactionNotifications.length" class="empty-panel">暂无互动通知</div>
        </div>

        <div v-else class="list-wrap">
          <div v-for="item in systemNotifications" :key="item.id" class="list-item" @click="markNotificationRead(item)">
            <div class="list-main">
              <div class="list-title">{{ item.type || '系统通知' }}</div>
              <div class="list-sub">{{ item.content }}</div>
            </div>
            <div class="list-right">
              <span class="time">{{ formatDateTime(item.createTime) }}</span>
            </div>
          </div>
          <div v-if="!systemNotifications.length" class="empty-panel">暂无系统通知</div>
        </div>
      </div>
    </div>

    <div v-if="showMessageSidebar" class="mask" @click="showMessageSidebar = false"></div>
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

let messageWs = null
let messageReconnectTimer = null
let messageReconnectAttempt = 0
let shouldReconnectMessageWs = false

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

function isImageMessage(content) {
  if (!content) return false
  const lower = String(content).toLowerCase()
  return lower.startsWith('message/') && (
    lower.endsWith('.png') ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.webp') ||
    lower.endsWith('.gif')
  )
}

function formatConversationPreview(content) {
  if (!content) return '暂无消息内容'
  if (content === '[已撤回]') return content
  if (isImageMessage(content)) return '[图片]'
  return content
}

function buildMessageWsUrl() {
  const userId = userStore.userInfo?.id
  if (!userId) return ''
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/message?userId=${encodeURIComponent(userId)}`
}

function scheduleMessageWsReconnect() {
  if (messageReconnectTimer) {
    clearTimeout(messageReconnectTimer)
  }
  messageReconnectAttempt += 1
  const delay = Math.min(1000 * 2 ** Math.max(0, messageReconnectAttempt - 1), 10000)
  messageReconnectTimer = setTimeout(() => {
    connectMessageWs()
  }, delay)
}

function closeMessageWs() {
  shouldReconnectMessageWs = false
  if (messageReconnectTimer) {
    clearTimeout(messageReconnectTimer)
    messageReconnectTimer = null
  }
  if (messageWs) {
    messageWs.close()
    messageWs = null
  }
}

function connectMessageWs() {
  if (!userStore.isLoggedIn || !userStore.userInfo?.id) return
  if (messageWs && (messageWs.readyState === WebSocket.OPEN || messageWs.readyState === WebSocket.CONNECTING)) return

  const url = buildMessageWsUrl()
  if (!url) return

  shouldReconnectMessageWs = true
  messageWs = new WebSocket(url)

  messageWs.onopen = () => {
    messageReconnectAttempt = 0
  }

  messageWs.onmessage = async (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'message') {
        await Promise.all([loadMessageSummary(), loadSidebarConversations()])
      } else if (data.type === 'notification') {
        await Promise.all([loadMessageSummary(), loadSidebarNotifications()])
      }
    } catch (error) {
      console.error('Parse message websocket failed', error)
    }
  }

  messageWs.onerror = () => {}

  messageWs.onclose = () => {
    messageWs = null
    if (shouldReconnectMessageWs) {
      scheduleMessageWsReconnect()
    }
  }
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
  } catch (error) {
    console.error(error)
    alert('头像上传失败')
  } finally {
    isUploadingAvatar.value = false
  }
}

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

async function loadMessageSummary() {
  if (!userStore.isLoggedIn) {
    messageUnreadCount.value = 0
    notificationUnreadCount.value = 0
    systemUnreadCount.value = 0
    return
  }
  const summary = await getMessageSummary().catch(() => null)
  messageUnreadCount.value = Number(summary?.messageUnread || 0)
  notificationUnreadCount.value = Number(summary?.notificationUnread || 0)
  systemUnreadCount.value = Number(summary?.systemUnread || 0)
}

async function loadSidebarConversations() {
  if (!userStore.isLoggedIn) {
    conversations.value = []
    return
  }
  const list = await getConversations().catch(() => [])
  conversations.value = Array.isArray(list) ? list : []
}

async function loadSidebarNotifications() {
  if (!userStore.isLoggedIn) {
    interactionNotifications.value = []
    systemNotifications.value = []
    return
  }
  const page = await getNotifications(1, 30).catch(() => ({ records: [] }))
  const records = Array.isArray(page?.records) ? page.records : []
  interactionNotifications.value = records.filter((item) => String(item.type || '').toLowerCase() !== 'system')
  systemNotifications.value = records.filter((item) => String(item.type || '').toLowerCase() === 'system')
}

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
    await Promise.all([loadMessageSummary(), loadSidebarConversations(), loadSidebarNotifications()])
  } catch (error) {
    console.error('Failed to load sidebar messages:', error)
  } finally {
    sidebarLoading.value = false
  }
}

function activateSearch() {
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
  closeMessageWs()
  userStore.logout()
  showUserMenu.value = false
  showMessageSidebar.value = false
  router.push('/')
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

async function markNotificationRead(item) {
  if (!item?.id || item.status === 1) return
  try {
    await readNotificationApi(item.id)
    item.status = 1
    await Promise.all([loadMessageSummary(), loadSidebarNotifications()])
  } catch (error) {
    console.error(error)
  }
}

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
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
    closeMessageWs()
    if (id) {
      await loadSidebarMessages()
      connectMessageWs()
    } else {
      conversations.value = []
      interactionNotifications.value = []
      systemNotifications.value = []
      messageUnreadCount.value = 0
      notificationUnreadCount.value = 0
      systemUnreadCount.value = 0
    }
  },
  { immediate: true }
)

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
  if (userStore.isLoggedIn) {
    await loadSidebarMessages()
    connectMessageWs()
  }
  document.addEventListener('click', onGlobalClick)
})

onUnmounted(() => {
  closeMessageWs()
  document.removeEventListener('click', onGlobalClick)
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f6f7f9;
  display: flex;
}

.app-sidebar {
  width: 88px;
  background: #ffffff;
  border-right: 1px solid #eceff3;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 10px;
  gap: 14px;
  z-index: 30;
}

.brand {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #111827;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  text-decoration: none;
}

.nav-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  width: 100%;
  border: none;
  background: transparent;
  color: #374151;
  border-radius: 10px;
  padding: 9px 6px;
  text-align: center;
  text-decoration: none;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-item:hover,
.nav-item.active {
  background: #eef2ff;
  color: #1f2937;
}

.nav-item.danger {
  color: #b91c1c;
}

.message-entry {
  margin-top: auto;
  position: relative;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  line-height: 1;
  margin-left: 6px;
}

.app-main-wrap {
  margin-left: 88px;
  width: calc(100% - 88px);
  min-height: 100vh;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 64px;
  background: rgba(246, 247, 249, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid #eceff3;
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 16px;
}

.title {
  text-decoration: none;
  color: #111827;
  font-weight: 600;
  min-width: 130px;
}

.search-wrap {
  position: relative;
  flex: 1;
  max-width: 620px;
}

.search-bar {
  height: 40px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 0 8px 0 12px;
}

.search-bar:focus-within {
  border-color: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.18);
}

.search-bar input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  background: transparent;
}

.search-btn {
  border: none;
  border-radius: 8px;
  background: #111827;
  color: #fff;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 12px;
}

.search-panel {
  position: absolute;
  top: 46px;
  left: 0;
  width: 100%;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.1);
  padding: 12px;
  z-index: 40;
}

.panel-section + .panel-section {
  margin-top: 12px;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.panel-link {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.search-tag {
  border: 1px solid #e5e7eb;
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
}

.hot-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.hot-item {
  border: none;
  background: transparent;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
}

.hot-item:hover {
  background: #f8fafc;
}

.rank {
  color: #64748b;
  min-width: 14px;
}

.hot-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-empty {
  color: #94a3b8;
  font-size: 12px;
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn {
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #fff;
  color: #111827;
  padding: 7px 12px;
  font-size: 13px;
  cursor: pointer;
}

.btn-primary {
  border-color: #111827;
  background: #111827;
  color: #fff;
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
  background: #eef2f7;
}

.avatar,
.tiny-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  max-width: 90px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
}

.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
  min-width: 140px;
  overflow: hidden;
  z-index: 50;
}

.menu-item {
  display: block;
  padding: 10px 12px;
  font-size: 13px;
  color: #111827;
  text-decoration: none;
  cursor: pointer;
}

.menu-item:hover {
  background: #f8fafc;
}

.menu-item.danger {
  color: #b91c1c;
}

.menu-divider {
  height: 1px;
  background: #e5e7eb;
}

.avatar-upload input {
  display: none;
}

.avatar-upload.disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.app-content {
  padding: 22px;
}

.message-drawer {
  position: fixed;
  top: 0;
  right: -420px;
  width: 400px;
  max-width: 92vw;
  height: 100vh;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  z-index: 80;
  display: flex;
  flex-direction: column;
  transition: right 0.25s ease;
}

.message-drawer.open {
  right: 0;
}

.drawer-header {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
}

.close-btn {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 20px;
}

.close-btn:hover {
  background: #f1f5f9;
}

.drawer-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
}

.drawer-tab {
  flex: 1;
  border: none;
  background: #fff;
  padding: 10px;
  font-size: 13px;
  cursor: pointer;
  color: #334155;
}

.drawer-tab.active {
  background: #f8fafc;
  font-weight: 600;
}

.drawer-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.list-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
}

.list-item:hover {
  background: #f8fafc;
}

.list-main {
  min-width: 0;
  flex: 1;
}

.list-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.list-sub {
  font-size: 12px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.time {
  font-size: 11px;
  color: #94a3b8;
}

.empty-panel {
  text-align: center;
  color: #94a3b8;
  padding: 30px 0;
  font-size: 13px;
}

.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.3);
  z-index: 70;
}

@media (max-width: 900px) {
  .app-sidebar {
    display: none;
  }

  .app-main-wrap {
    margin-left: 0;
    width: 100%;
  }

  .topbar {
    padding: 0 12px;
    gap: 10px;
  }

  .title {
    display: none;
  }

  .username {
    display: none;
  }

  .app-content {
    padding: 14px;
  }
}
</style>
