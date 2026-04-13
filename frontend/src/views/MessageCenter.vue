<template>
  <div class="message-center">
    <!-- 顶部标题栏 -->
    <div class="header">
      <div class="header-content">
        <h1 class="page-title">消息中心</h1>
        <div class="unread-stats">
          <div class="unread-item" v-if="summary.messageUnread > 0">
            <span class="unread-badge">{{ summary.messageUnread }}</span>
            <span class="unread-label">私信</span>
          </div>
          <div class="unread-item" v-if="summary.notificationUnread > 0">
            <span class="unread-badge">{{ summary.notificationUnread }}</span>
            <span class="unread-label">通知</span>
          </div>
          <div class="unread-item" v-if="summary.systemUnread > 0">
            <span class="unread-badge">{{ summary.systemUnread }}</span>
            <span class="unread-label">系统</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 标签页切换 -->
    <div class="tabs-container">
      <div class="tabs">
        <button class="tab" :class="{ active: activeTab === 'message' }" @click="activeTab = 'message'">
          <span class="tab-icon">💬</span>
          <span class="tab-label">私信</span>
          <span class="tab-badge" v-if="summary.messageUnread > 0">{{ summary.messageUnread }}</span>
        </button>
        <button class="tab" :class="{ active: activeTab === 'notification' }" @click="activeTab = 'notification'">
          <span class="tab-icon">🔔</span>
          <span class="tab-label">通知</span>
          <span class="tab-badge" v-if="summary.notificationUnread > 0">{{ summary.notificationUnread }}</span>
        </button>
        <button class="tab" :class="{ active: activeTab === 'system' }" @click="activeTab = 'system'">
          <span class="tab-icon">📢</span>
          <span class="tab-label">系统通知</span>
          <span class="tab-badge" v-if="summary.systemUnread > 0">{{ summary.systemUnread }}</span>
        </button>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content">
      <!-- 私信面板 -->
      <div v-if="activeTab === 'message'" class="panel">
        <div class="message-layout">
          <!-- 会话列表 -->
          <div class="conversation-list">
            <div class="list-header">
              <h3>会话</h3>
            </div>
            <div class="list-content">
              <div
                v-for="item in conversations"
                :key="item.targetId"
                class="conversation-item"
                :class="{ active: currentTarget?.targetId === item.targetId }"
                @click="selectConversation(item)"
              >
                <div class="conversation-avatar">
                  <img
                    :src="resolveAvatar(item.targetAvatar)"
                    alt=""
                    @error="onAvatarError"
                  />
                  <span class="unread-indicator" v-if="item.unread > 0">{{ item.unread }}</span>
                </div>
                <div class="conversation-info">
                  <div class="conversation-header">
                    <span class="conversation-name">{{ item.targetName || '用户' }}</span>
                  </div>
                  <div class="conversation-preview" v-if="item.lastContent">
                    <span class="preview-text">{{ formatConversationPreview(item.lastContent) }}</span>
                  </div>
                </div>
                <div class="conversation-actions" @click.stop>
                  <button class="action-btn" @click="toggleActionMenu(item)">
                    <span class="action-icon">•••</span>
                  </button>
                  <div
                    v-if="actionMenu.visible && actionMenu.target?.targetId === item.targetId"
                    class="action-menu"
                  >
                    <button class="menu-item" @click="handleClearConversation(item)">清空聊天记录</button>
                  </div>
                </div>
              </div>
              <div v-if="conversations.length === 0" class="empty-state">
                <div class="empty-icon">💬</div>
                <p class="empty-text">暂无私信会话</p>
              </div>
            </div>
          </div>

          <!-- 聊天详情 -->
          <div class="chat-detail" v-if="currentTarget">
            <div class="chat-header">
              <div class="chat-info">
                <img
                  class="chat-avatar"
                  :src="resolveAvatar(currentTarget.targetAvatar)"
                  alt=""
                  @error="onAvatarError"
                />
                <span class="chat-name">{{ currentTarget.targetName || '用户' }}</span>
              </div>
            </div>
            <div class="chat-body">
              <template v-for="item in messageItems" :key="item.key">
                <div v-if="item.type === 'time'" class="time-divider">
                  <span>{{ item.text }}</span>
                </div>
                <div
                  v-else
                  class="message"
                  :class="{ 'message-self': item.msg.senderId === userId }"
                >
                  <div class="message-content">
                    <div class="message-bubble" :class="{ 'bubble-self': item.msg.senderId === userId }">
                      <img
                        v-if="isImageMessage(item.msg.content)"
                        class="message-image"
                        :src="resolveMessageImage(item.msg.content)"
                        alt="图片消息"
                      />
                      <span v-else class="message-text">{{ item.msg.content }}</span>
                      <button
                        v-if="item.msg.senderId === userId"
                        class="revoke-btn"
                        @click="revokeMessage(item.msg.id)"
                      >
                        撤回
                      </button>
                    </div>
                  </div>
                </div>
              </template>
            </div>
            <div class="chat-input">
              <div class="input-container">
                <input 
                  v-model="messageText" 
                  placeholder="输入私信内容..." 
                  @keyup.enter="sendMessage"
                  class="message-input"
                />
                <div class="input-actions">
                  <button class="ai-draft-btn" :disabled="draftingAi || !currentTarget" @click="runAiDraft">
                    <span class="ai-icon">🤖</span>
                    <span>{{ draftingAi ? '生成中...' : 'AI 草稿' }}</span>
                  </button>
                  <label class="image-upload-btn" :class="{ disabled: uploadingImage }" :title="uploadingImage ? '上传中...' : '发送图片'">
                    <input type="file" accept="image/*" @change="sendImageMessage" :disabled="uploadingImage" />
                    <span v-if="!uploadingImage" class="upload-icon">🖼️</span>
                    <span v-else class="uploading-indicator"></span>
                  </label>
                  <button class="send-btn" @click="sendMessage" :disabled="!messageText.trim() || !currentTarget">
                    发送
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="empty-chat">
            <div class="empty-icon">💬</div>
            <p class="empty-text">请选择一个会话开始聊天</p>
          </div>
        </div>
      </div>

      <!-- 通知面板 -->
      <div v-if="activeTab === 'notification'" class="panel">
        <div class="panel-header">
          <h3>通知</h3>
        </div>
        <div class="notification-list">
          <div class="notification-item" v-for="n in notifications" :key="n.id" :class="{ unread: n.status === 0 }">
            <div class="notification-icon">🔔</div>
            <div class="notification-content">
              <div class="notification-header">
                <span class="notification-type">{{ n.type }}</span>
                <span class="notification-time">{{ formatTimeLabel(n.createTime) }}</span>
              </div>
              <p class="notification-text">{{ n.content }}</p>
            </div>
            <button v-if="n.status === 0" class="read-btn" @click="markNotificationRead(n.id)">标记已读</button>
          </div>
          <div v-if="notifications.length === 0" class="empty-state">
            <div class="empty-icon">🔔</div>
            <p class="empty-text">暂无通知</p>
          </div>
        </div>
      </div>

      <!-- 系统通知面板 -->
      <div v-if="activeTab === 'system'" class="panel">
        <div class="panel-header">
          <h3>系统通知</h3>
        </div>
        <div class="notification-list">
          <div class="notification-item" v-for="n in systemNotifications" :key="n.id" :class="{ unread: n.status === 0 }">
            <div class="notification-icon">📢</div>
            <div class="notification-content">
              <div class="notification-header">
                <span class="notification-type">系统</span>
                <span class="notification-time">{{ formatTimeLabel(n.createTime) }}</span>
              </div>
              <p class="notification-text">{{ n.content }}</p>
            </div>
            <button v-if="n.status === 0" class="read-btn" @click="markNotificationRead(n.id)">标记已读</button>
          </div>
          <div v-if="systemNotifications.length === 0" class="empty-state">
            <div class="empty-icon">📢</div>
            <p class="empty-text">暂无系统通知</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'
import {
  getMessageSummary,
  getConversations,
  getMessageList,
  sendMessageApi,
  uploadMessageImageApi,
  readMessageApi,
  revokeMessageApi,
  clearConversationApi,
  getNotifications,
  readNotificationApi
} from '../api/message'

const userStore = useUserStore()
const route = useRoute()
const userId = userStore.userInfo?.id

const summary = ref({})
const activeTab = ref('message')
const conversations = ref([])
const currentTarget = ref(null)
const messages = ref([])
const messageItems = ref([])
const messageText = ref('')
const uploadingImage = ref(false)
const notifications = ref([])
const systemNotifications = ref([])
const actionMenu = ref({ visible: false, target: null })

async function loadSummary() {
  summary.value = await getMessageSummary()
}

async function loadConversations() {
  conversations.value = await getConversations()
}

async function selectConversation(item) {
  currentTarget.value = item
  await loadMessages()
  await readMessageApi(item.targetId)
  await loadSummary()
}

async function loadMessages() {
  if (!currentTarget.value) return
  const res = await getMessageList(currentTarget.value.targetId, 1, 50)
  messages.value = res.records || []
  buildMessageItems()
}

async function sendMessage() {
  if (!messageText.value || !currentTarget.value) return
  await sendMessageApi({ receiverId: currentTarget.value.targetId, content: messageText.value })
  messageText.value = ''
  await loadMessages()
}

async function sendImageMessage(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file || !currentTarget.value || uploadingImage.value) return
  try {
    uploadingImage.value = true
    const formData = new FormData()
    formData.append('file', file)
    const objectName = await uploadMessageImageApi(formData)
    await sendMessageApi({ receiverId: currentTarget.value.targetId, content: objectName })
    await loadMessages()
  } finally {
    uploadingImage.value = false
  }
}

async function revokeMessage(id) {
  await revokeMessageApi(id)
  await loadMessages()
}

async function loadNotifications() {
  const res = await getNotifications(1, 30)
  const list = res.records || []
  notifications.value = list.filter((n) => n.type !== 'system')
  systemNotifications.value = list.filter((n) => n.type === 'system')
}

async function markNotificationRead(id) {
  await readNotificationApi(id)
  await loadNotifications()
  await loadSummary()
}

async function clearConversation(item) {
  if (!item?.targetId) return
  if (!confirm('确定要清空该会话的聊天记录吗？')) return
  await clearConversationApi(item.targetId)
  if (currentTarget.value?.targetId === item.targetId) {
    currentTarget.value = null
    messages.value = []
    messageItems.value = []
  }
  await loadConversations()
  await loadSummary()
}

function buildMessageItems() {
  const list = [...(messages.value || [])]
  list.sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())

  const items = []
  let lastTime = null
  const GAP = 5 * 60 * 1000

  for (const msg of list) {
    const t = new Date(msg.createTime).getTime()
    if (!lastTime || t - lastTime >= GAP) {
      items.push({
        type: 'time',
        key: `t-${t}`,
        text: formatTimeLabel(msg.createTime)
      })
    }
    items.push({
      type: 'msg',
      key: `m-${msg.id}`,
      msg
    })
    lastTime = t
  }

  messageItems.value = items
}

function formatTimeLabel(val) {
  const d = new Date(val)
  const Y = d.getFullYear()
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const D = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  return `${Y}-${M}-${D} ${h}:${m}`
}

const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function onAvatarError(event) {
  event.target.src = avatarPlaceholder
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

function resolveMessageImage(content) {
  return `/api/file/message?url=${encodeURIComponent(content)}`
}

function formatConversationPreview(content) {
  if (!content) return ''
  if (isImageMessage(content)) return '[图片]'
  return content.length > 20 ? content.substring(0, 20) + '...' : content
}

function toggleActionMenu(item) {
  if (actionMenu.value.visible && actionMenu.value.target?.targetId === item.targetId) {
    actionMenu.value.visible = false
    actionMenu.value.target = null
    return
  }
  actionMenu.value = {
    visible: true,
    target: item
  }
}

function closeActionMenu() {
  if (actionMenu.value.visible) {
    actionMenu.value.visible = false
    actionMenu.value.target = null
  }
}

async function handleClearConversation(item) {
  closeActionMenu()
  await clearConversation(item)
}

async function initFromRoute() {
  const targetId = route.query.targetId ? Number(route.query.targetId) : null
  const targetName = route.query.targetName
  if (!targetId) return

  let convo = conversations.value.find((c) => c.targetId === targetId)
  if (!convo) {
    convo = {
      targetId,
      targetName: targetName || '用户',
      unread: 0,
      lastContent: ''
    }
    conversations.value.unshift(convo)
  }
  await selectConversation(convo)
}

onMounted(async () => {
  await loadSummary()
  await loadConversations()
  await loadNotifications()
  await initFromRoute()
  document.addEventListener('click', closeActionMenu)
  document.addEventListener('scroll', closeActionMenu, true)
})

onUnmounted(() => {
  document.removeEventListener('click', closeActionMenu)
  document.removeEventListener('scroll', closeActionMenu, true)
})
</script>

<style scoped>
/* 整体布局 */
.message-center {
  min-height: 100vh;
  background: #f6f7f9;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部标题栏 */
.header {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(32px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  padding: 20px 24px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.unread-stats {
  display: flex;
  gap: 16px;
  align-items: center;
}

.unread-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.unread-badge {
  background: #ef4444;
  color: #fff;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  min-width: 16px;
  text-align: center;
  line-height: 1;
}

.unread-label {
  font-size: 14px;
  color: #64748b;
}

/* 标签页容器 */
.tabs-container {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(32px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  padding: 8px;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  background: transparent;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  color: #64748b;
  font-size: 14px;
  font-weight: 600;
}

.tab:hover {
  background: rgba(79, 70, 229, 0.08);
  color: #4338ca;
}

.tab.active {
  background: #4338ca;
  color: #fff;
  box-shadow: 0 8px 16px rgba(67, 56, 202, 0.3);
}

.tab-icon {
  font-size: 18px;
}

.tab-label {
  font-size: 14px;
  font-weight: 600;
}

.tab-badge {
  background: rgba(255, 255, 255, 0.9);
  color: #4338ca;
  border-radius: 999px;
  padding: 1px 6px;
  font-size: 12px;
  font-weight: 600;
  min-width: 18px;
  text-align: center;
}

.tab.active .tab-badge {
  background: #fff;
}

/* 内容区域 */
.content {
  flex: 1;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(32px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 消息布局 */
.message-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: calc(100vh - 240px);
  min-height: 500px;
}

/* 会话列表 */
.conversation-list {
  border-right: 1px solid #eceff3;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.list-header {
  padding: 16px 20px;
  border-bottom: 1px solid #eceff3;
  background: #f8fafc;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.list-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  background: #ffffff;
  border: 1px solid transparent;
}

.conversation-item:hover {
  background: rgba(79, 70, 229, 0.08);
  border-color: #e2e8f0;
}

.conversation-item.active {
  background: #4338ca;
  color: #fff;
  border-color: #4338ca;
  box-shadow: 0 4px 12px rgba(67, 56, 202, 0.2);
}

.conversation-avatar {
  position: relative;
}

.conversation-avatar img {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid #e2e8f0;
}

.unread-indicator {
  position: absolute;
  top: -2px;
  right: -2px;
  background: #ef4444;
  color: #fff;
  border-radius: 999px;
  padding: 2px 6px;
  font-size: 12px;
  font-weight: 600;
  min-width: 18px;
  text-align: center;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.conversation-info {
  flex: 1;
  min-width: 0;
}

.conversation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.conversation-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item.active .conversation-name {
  color: #fff;
}

.conversation-preview {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item.active .conversation-preview {
  color: rgba(255, 255, 255, 0.8);
}

.conversation-actions {
  position: relative;
}

.action-btn {
  border: none;
  background: transparent;
  font-size: 18px;
  color: #94a3b8;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: #f1f5f9;
}

.conversation-item.active .action-btn {
  color: rgba(255, 255, 255, 0.8);
}

.conversation-item.active .action-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.action-menu {
  position: absolute;
  right: 0;
  top: 36px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
  min-width: 160px;
  z-index: 100;
  padding: 6px 0;
}

.menu-item {
  width: 100%;
  text-align: left;
  padding: 10px 16px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 14px;
  color: #1f2937;
  transition: all 0.2s ease;
}

.menu-item:hover {
  background: #f8fafc;
}

/* 聊天详情 */
.chat-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
}

.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid #eceff3;
  background: #f8fafc;
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid #e2e8f0;
}

.chat-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f8fafc;
}

.time-divider {
  text-align: center;
  margin: 20px 0;
  font-size: 12px;
  color: #94a3b8;
}

.time-divider span {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
}

.message {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-end;
}

.message.message-self {
  justify-content: flex-end;
}

.message-content {
  max-width: 70%;
}

.message-bubble {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  position: relative;
}

.message-bubble.bubble-self {
  background: #4338ca;
  color: #fff;
  border-color: #4338ca;
  box-shadow: 0 4px 12px rgba(67, 56, 202, 0.2);
}

.message-text {
  font-size: 14px;
  line-height: 1.4;
  color: #1f2937;
}

.message-bubble.bubble-self .message-text {
  color: #fff;
}

.message-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  display: block;
  border: 1px solid #e2e8f0;
}

.revoke-btn {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.revoke-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.message-bubble.bubble-self .revoke-btn {
  color: rgba(255, 255, 255, 0.8);
}

.message-bubble.bubble-self .revoke-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.chat-input {
  padding: 16px 20px;
  border-top: 1px solid #eceff3;
  background: #ffffff;
}

.input-container {
  display: flex;
  gap: 10px;
  align-items: center;
  background: #f8fafc;
  border-radius: 16px;
  padding: 8px;
  border: 1px solid #e2e8f0;
}

.message-input {
  flex: 1;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s ease;
  resize: none;
  background: #ffffff;
  color: #1f2937;
}

.message-input:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(67, 56, 202, 0.1);
}

.input-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ai-draft-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #ffffff;
  color: #1f2937;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.ai-draft-btn:hover:not(:disabled) {
  background: #4338ca;
  color: #fff;
  border-color: #4338ca;
  box-shadow: 0 4px 12px rgba(67, 56, 202, 0.3);
}

.ai-draft-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ai-icon {
  font-size: 14px;
}

.image-upload-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.image-upload-btn:hover:not(.disabled) {
  border-color: #4338ca;
  background: rgba(79, 70, 229, 0.08);
}

.image-upload-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.image-upload-btn input {
  display: none;
}

.upload-icon {
  font-size: 16px;
  color: #64748b;
}

.uploading-indicator {
  width: 12px;
  height: 12px;
  border: 2px solid #4338ca;
  border-top: 2px solid transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.send-btn {
  padding: 8px 18px;
  background: #4338ca;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(67, 56, 202, 0.2);
}

.send-btn:hover:not(:disabled) {
  background: #3730a3;
  box-shadow: 0 4px 12px rgba(67, 56, 202, 0.3);
  transform: translateY(-1px);
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 空状态 */
.empty-state,
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  background: #f8fafc;
  height: 100%;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
  color: #94a3b8;
}

.empty-text {
  font-size: 16px;
  color: #64748b;
  margin: 0;
  font-weight: 500;
}

/* 通知面板 */
.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid #eceff3;
  background: #f8fafc;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.notification-list {
  padding: 8px;
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  background: #ffffff;
}

.notification-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-radius: 16px;
  margin-bottom: 8px;
  transition: all 0.2s ease;
  position: relative;
  background: #ffffff;
  border: 1px solid transparent;
}

.notification-item:hover {
  background: rgba(79, 70, 229, 0.08);
  border-color: #e2e8f0;
}

.notification-item.unread {
  background: #eef2ff;
  border-color: #dbeafe;
  border-left: 3px solid #4338ca;
}

.notification-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
  color: #64748b;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.notification-type {
  font-size: 12px;
  font-weight: 600;
  color: #4338ca;
  background: rgba(67, 56, 202, 0.1);
  padding: 2px 8px;
  border-radius: 6px;
}

.notification-time {
  font-size: 12px;
  color: #94a3b8;
}

.notification-text {
  font-size: 14px;
  color: #1f2937;
  line-height: 1.4;
  margin: 0;
  word-break: break-word;
}

.read-btn {
  padding: 6px 12px;
  background: #ffffff;
  color: #1f2937;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 2px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.read-btn:hover {
  background: #4338ca;
  color: #fff;
  border-color: #4338ca;
  box-shadow: 0 4px 12px rgba(67, 56, 202, 0.2);
}

/* 响应式设计 */
@media (max-width: 900px) {
  .message-center {
    padding: 14px;
    gap: 14px;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .unread-stats {
    width: 100%;
    justify-content: space-around;
  }

  .message-layout {
    grid-template-columns: 1fr;
    height: calc(100vh - 220px);
  }

  .conversation-list {
    display: none;
  }

  .chat-body {
    padding: 12px;
  }

  .message-content {
    max-width: 85%;
  }

  .input-container {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .input-actions {
    justify-content: space-between;
  }

  .ai-draft-btn {
    flex: 1;
    justify-content: center;
  }

  .send-btn {
    flex: 1;
  }

  .notification-list {
    max-height: calc(100vh - 220px);
  }
}
</style>
