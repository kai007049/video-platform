<template>
  <div class="message-center">
    <div class="header">
      <h2>消息中心</h2>
      <div class="unread">
        <span class="badge">私信 {{ summary.messageUnread || 0 }}</span>
        <span class="badge">通知 {{ summary.notificationUnread || 0 }}</span>
        <span class="badge">系统 {{ summary.systemUnread || 0 }}</span>
      </div>
    </div>

    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'message' }" @click="activeTab = 'message'">私信</button>
      <button class="tab" :class="{ active: activeTab === 'notification' }" @click="activeTab = 'notification'">通知</button>
      <button class="tab" :class="{ active: activeTab === 'system' }" @click="activeTab = 'system'">系统通知</button>
    </div>

    <div class="content">
      <div v-if="activeTab === 'message'" class="panel">
        <div class="split">
          <div class="list">
            <div
              v-for="item in conversations"
              :key="item.targetId"
              class="list-item"
              :class="{ active: currentTarget?.targetId === item.targetId }"
              @click="selectConversation(item)"
            >
              <img
                class="avatar"
                :src="resolveAvatar(item.targetAvatar)"
                alt=""
                @error="onAvatarError"
              />
              <div class="info">
                <div class="title">
                  <span>{{ item.targetName || '用户' }}</span>
                  <span class="count" v-if="item.unread > 0">{{ item.unread }}</span>
                </div>
              </div>
              <div class="actions" @click.stop>
                <button class="more-btn" @click="toggleActionMenu(item)">⋯</button>
                <div
                  v-if="actionMenu.visible && actionMenu.target?.targetId === item.targetId"
                  class="action-menu"
                >
                  <button class="menu-item" @click="handleClearConversation(item)">清空聊天记录</button>
                </div>
              </div>
            </div>
          </div>

          <div class="detail" v-if="currentTarget">
            <div class="detail-header">
              <span>与 {{ currentTarget.targetName || '用户' }} 的对话</span>
            </div>
            <div class="detail-body">
              <template v-for="item in messageItems" :key="item.key">
                <div v-if="item.type === 'time'" class="time-divider">
                  <span>{{ item.text }}</span>
                </div>
                <div
                  v-else
                  class="msg"
                  :class="{ self: item.msg.senderId === userId }"
                >
                  <div class="bubble">
                    <img
                      v-if="isImageMessage(item.msg.content)"
                      class="msg-image"
                      :src="resolveMessageImage(item.msg.content)"
                      alt="图片消息"
                    />
                    <span v-else>{{ item.msg.content }}</span>
                    <button
                      v-if="item.msg.senderId === userId"
                      class="revoke"
                      @click="revokeMessage(item.msg.id)"
                    >
                      撤回
                    </button>
                  </div>
                </div>
              </template>
            </div>
            <div class="detail-input">
              <input v-model="messageText" placeholder="输入私信内容" @keyup.enter="sendMessage" />
              <button class="ai-draft-btn" :disabled="draftingAi || !currentTarget" @click="runAiDraft">
                {{ draftingAi ? '生成中...' : 'AI 草稿' }}
              </button>
              <label class="img-upload-btn" :class="{ disabled: uploadingImage }" :title="uploadingImage ? '上传中...' : '发送图片'">
                <input type="file" accept="image/*" @change="sendImageMessage" :disabled="uploadingImage" />
                <span v-if="!uploadingImage" class="img-upload-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="3.2" y="4" width="17.6" height="16" rx="3" stroke="currentColor" stroke-width="1.9"/>
                    <circle cx="9" cy="10" r="1.8" fill="currentColor"/>
                    <path d="M6.8 16.4L10.2 12.8C10.6 12.4 11.2 12.4 11.6 12.8L13.6 14.8C14 15.2 14.6 15.2 15 14.8L17.2 12.6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </span>
                <span v-else class="img-uploading-dot" aria-hidden="true"></span>
              </label>
              <button @click="sendMessage">发送</button>
            </div>
          </div>
          <div v-else class="empty">请选择一个会话</div>
        </div>
      </div>

      <div v-if="activeTab === 'notification'" class="panel">
        <div class="notify-item" v-for="n in notifications" :key="n.id">
          <span class="type">{{ n.type }}</span>
          <span class="content">{{ n.content }}</span>
          <button v-if="n.status === 0" class="read" @click="markNotificationRead(n.id)">标记已读</button>
        </div>
      </div>

      <div v-if="activeTab === 'system'" class="panel">
        <div class="notify-item" v-for="n in systemNotifications" :key="n.id">
          <span class="type">系统</span>
          <span class="content">{{ n.content }}</span>
          <button v-if="n.status === 0" class="read" @click="markNotificationRead(n.id)">标记已读</button>
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
import { createMessageDraftTask, pollAgentTask } from '../api/agent'

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
const draftingAi = ref(false)
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

async function runAiDraft() {
  if (!currentTarget.value) return
  draftingAi.value = true
  try {
    const latest = messages.value.length ? messages.value[messages.value.length - 1].content || '' : '你好'
    const { data } = await createMessageDraftTask({
      target_id: currentTarget.value.targetId,
      scenario: 'reply',
      latest_user_message: latest,
      tone: 'friendly'
    })
    const done = await pollAgentTask(data.task_id)
    if (done.status !== 'success' || !done.result) {
      throw new Error(done.error || 'AI 草稿生成失败')
    }
    messageText.value = done.result.draft || ''
  } catch (e) {
    alert(e.message || 'AI 草稿生成失败')
  } finally {
    draftingAi.value = false
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
.message-center {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 0 40px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.unread .badge {
  background: rgba(251,114,153,.15);
  color: var(--bili-pink);
  padding: 4px 10px;
  border-radius: 999px;
  margin-left: 8px;
  font-size: 12px;
}

.tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.tab {
  padding: 8px 18px;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-surface);
  color: var(--text-primary);
}

.tab.active {
  color: var(--bili-pink);
  border-color: rgba(251,114,153,.5);
  box-shadow: 0 2px 6px rgba(251,114,153,.2);
}

.panel {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px;
}

.split {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
}

.list {
  border-right: 1px solid var(--border-color);
  position: relative;
}

.context-menu {
  position: fixed;
  z-index: 100;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  box-shadow: var(--card-shadow);
  border-radius: 8px;
  padding: 6px;
  min-width: 140px;
}

.context-menu .menu-item {
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  font-size: 14px;
  color: var(--text-primary);
  background: transparent;
  border-radius: 6px;
}

.context-menu .menu-item:hover {
  background: var(--bg-gray);
}

.list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
}

.list-item.active,
.list-item:hover {
  background: rgba(251,114,153,.1);
}

.actions {
  margin-left: auto;
  position: relative;
}

.more-btn {
  border: none;
  background: transparent;
  font-size: 18px;
  color: #9aa0a6;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 6px;
  cursor: pointer;
}

.more-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.action-menu {
  position: absolute;
  right: 0;
  top: 28px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: var(--card-shadow);
  min-width: 140px;
  z-index: 5;
  padding: 6px 0;
}

.action-menu .menu-item {
  width: 100%;
  text-align: left;
  padding: 8px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
}

.action-menu .menu-item:hover {
  background: var(--bg-gray);
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-gray);
  display: flex;
  align-items: center;
  justify-content: center;
}

.info {
  flex: 1;
  min-width: 0;
}

.info .title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}

.count {
  background: var(--bili-pink);
  color: #fff;
  border-radius: 999px;
  padding: 0 6px;
  font-size: 12px;
}

.detail {
  display: flex;
  flex-direction: column;
  height: 520px;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background: var(--bg-page);
  border-radius: 8px;
  margin: 10px 0;
}

.time-divider {
  text-align: center;
  margin: 10px 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.time-divider span {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
}

.msg {
  margin-bottom: 10px;
  display: flex;
}

.msg.self {
  justify-content: flex-end;
}

.bubble {
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 8px 10px;
  max-width: 70%;
  position: relative;
}

.revoke {
  margin-left: 8px;
  font-size: 12px;
  color: #888;
}

.detail-input {
  display: flex;
  gap: 10px;
}

.detail-input input {
  flex: 1;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.img-upload-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all .18s ease;
}

.img-upload-btn:hover {
  border-color: var(--bili-pink);
  color: var(--bili-pink);
  background: rgba(251, 114, 153, .06);
}

.img-upload-btn input {
  display: none;
}

.img-upload-icon {
  width: 22px;
  height: 22px;
  display: inline-flex;
}

.img-upload-icon svg {
  width: 100%;
  height: 100%;
}

.img-uploading-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--bili-pink);
  animation: msg-upload-pulse 1s infinite ease-in-out;
}

.img-upload-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.detail-input button {
  padding: 10px 18px;
  background: var(--bili-pink);
  color: #fff;
  border-radius: 8px;
}

.ai-draft-btn {
  white-space: nowrap;
  background: #fff !important;
  color: var(--bili-pink) !important;
  border: 1px solid var(--bili-pink);
}

.ai-draft-btn:disabled {
  opacity: .65;
}

.msg-image {
  max-width: 180px;
  max-height: 180px;
  border-radius: 8px;
  display: block;
}

@keyframes msg-upload-pulse {
  0%, 100% { transform: scale(0.75); opacity: 0.5; }
  50% { transform: scale(1); opacity: 1; }
}

.notify-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
}

.notify-item .type {
  font-size: 12px;
  background: var(--bg-gray);
  padding: 2px 8px;
  border-radius: 6px;
}

.notify-item .read {
  margin-left: auto;
  font-size: 12px;
  color: var(--bili-pink);
}

.empty {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary);
}
</style>
