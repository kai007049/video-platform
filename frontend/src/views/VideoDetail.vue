<template>
  <div class="video-detail" v-if="video">
    <div class="player-area">
      <video
        ref="videoEl"
        class="video-player"
        :src="video.playUrl || video.videoUrl"
        :poster="resolveCover(video)"
        controls
        @play="onPlay"
        @pause="onPause"
        @timeupdate="onTimeUpdate"
        @seeking="onSeeking"
        @ended="onEnded"
        @loadedmetadata="onLoadedMetadata"
      />
      <div ref="danmuContainer" class="danmu-container" :class="{ hidden: !showDanmu }"></div>
      <button class="danmu-toggle" @click="showDanmu = !showDanmu">
        {{ showDanmu ? '关闭弹幕' : '开启弹幕' }}
      </button>
    </div>
    <div class="content">
      <h1 class="title">{{ video.title }}</h1>
      <div class="stats">
        <span class="play"><span class="icon">▶</span> {{ formatCount(video.playCount) }} 播放</span>
        <span class="like" :class="{ active: video.liked }" @click="toggleLike">
          <span class="icon">❤</span> {{ formatCount(video.likeCount) }}
        </span>
        <span class="favorite" :class="{ active: video.favorited }" @click="userStore.isLoggedIn && toggleFavorite()">
          <span class="icon">★</span> {{ formatCount(video.saveCount) }} 收藏
        </span>
      </div>
      <div class="author-bar" @click="goProfile(video.authorId)">
        <img :src="resolveAvatar(video.authorAvatar)" class="avatar" alt="" />
        <span class="author-name">{{ video.authorName || '用户' }}</span>
      </div>
      <div v-if="video.description" class="desc">{{ video.description }}</div>

      <div class="danmu-input-wrap" v-if="userStore.isLoggedIn">
        <input
          v-model="danmuText"
          type="text"
          placeholder="发个弹幕吧"
          maxlength="256"
          @keyup.enter="sendDanmu"
        />
        <button class="btn-danmu" :disabled="!canSendDanmu" @click="sendDanmu">
          {{ canSendDanmu ? '发送' : '连接中' }}
        </button>
        <button class="btn-danmu toggle" @click="showDanmu = !showDanmu">
          {{ showDanmu ? '关闭弹幕' : '开启弹幕' }}
        </button>
      </div>
      <div class="danmu-status" :class="wsState">
        <span>弹幕连接：</span>
        <span>{{ wsStatusText }}</span>
      </div>

      <div class="comments">
        <h3>评论区 ({{ comments.length }})</h3>
        <div v-if="userStore.isLoggedIn" class="comment-input">
          <img :src="resolveAvatar(userStore.userInfo?.avatar)" class="avatar-sm" alt="" />
          <div class="input-wrap">
            <textarea v-model="commentText" placeholder="说点什么..." rows="3"></textarea>
            <button class="btn-comment" @click="submitComment">发送评论</button>
          </div>
        </div>
        <div v-else class="comment-login">
          <span>登录后参与评论</span>
        </div>
        <div class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <img :src="resolveAvatar(c.userAvatar)" class="avatar-sm" alt="" />
            <div class="comment-body">
              <span class="username">{{ c.username }}</span>
              <p class="content">{{ c.content }}</p>
              <span class="time">{{ formatTime(c.createTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="loading">{{ loadError || '加载中...' }}</div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVideoDetail, recordPlay, saveProgress } from '../api/video'
import { getComments, addComment } from '../api/comment'
import { getDanmus } from '../api/danmu'
import { like, unlike } from '../api/like'
import { addFavorite, removeFavorite } from '../api/favorite'
import { useUserStore } from '../stores/user'
import DanmuPlayer from '../utils/danmu-player.js'
import DanmuControlPanel from '../utils/danmu-control-panel.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const video = ref(null)
const loadError = ref('')
const videoEl = ref(null)
const danmuContainer = ref(null)
const comments = ref([])
const commentText = ref('')
const danmuText = ref('')
const showDanmu = ref(true)
const wsState = ref('connecting')

const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href

let ws = null
let played = false
let progressTimer = null
let reconnectTimer = null
let reconnectAttempt = 0
let shouldReconnect = true

const historicalDanmus = ref([])
let danmuPlayer = null
let danmuControlPanel = null
const danmuDedupSet = new Set()

const videoId = computed(() => Number(route.params.id))
const canSendDanmu = computed(() => userStore.isLoggedIn && wsState.value === 'open')
const wsStatusText = computed(() => {
  if (wsState.value === 'open') return '已连接'
  if (wsState.value === 'connecting') return '连接中...'
  if (wsState.value === 'error') return '连接异常，正在重试'
  return '已断开，正在重连'
})

async function loadVideo() {
  loadError.value = ''
  try {
    video.value = await getVideoDetail(videoId.value)
    await Promise.all([loadComments(), loadDanmus()])
  } catch (e) {
    console.error(e)
    loadError.value = e.message || '视频加载失败'
    video.value = null
  }
}

async function loadDanmus() {
  try {
    const data = await getDanmus(videoId.value)
    historicalDanmus.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error(e)
    historicalDanmus.value = []
  }

  danmuDedupSet.clear()
  historicalDanmus.value.forEach((item) => {
    danmuDedupSet.add(buildDanmuKey(item))
  })
  initDanmuPlayer()
}

function initDanmuPlayer() {
  if (!videoEl.value || !danmuContainer.value) return
  if (danmuPlayer) danmuPlayer.destroy()
  if (danmuControlPanel) danmuControlPanel.destroy()

  const danmuData = historicalDanmus.value.map((d) => ({
    content: d.content,
    time: Number(d.timePoint) || 0,
    type: 'scroll',
    color: '#ffffff',
    id: d.id ?? d.clientMessageId
  }))

  danmuPlayer = new DanmuPlayer({
    video: videoEl.value,
    container: danmuContainer.value,
    danmuData,
    enabled: showDanmu.value,
    speed: 140,
    opacity: 0.95,
    fontSize: 22,
    trackCount: 8,
    trackHeight: 32,
    danmuGap: 32
  })

  nextTick(() => {
    danmuControlPanel = new DanmuControlPanel(danmuPlayer, { container: document.body })
  })
}

async function loadComments() {
  try {
    comments.value = await getComments(videoId.value)
  } catch (e) {
    console.error(e)
    comments.value = []
  }
}

function buildWsUrl() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = userStore.token ? `?token=${encodeURIComponent(userStore.token)}` : ''
  return `${protocol}//${location.host}/ws/danmu/${videoId.value}${token}`
}

function connectDanmu() {
  if (!videoId.value) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return

  wsState.value = 'connecting'
  ws = new WebSocket(buildWsUrl())

  ws.onopen = () => {
    wsState.value = 'open'
    reconnectAttempt = 0
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.error) {
        console.warn('Danmu websocket error:', data.error)
        return
      }
      addIncomingDanmu(data)
    } catch (e) {
      console.error('Parse danmu websocket message failed', e)
    }
  }

  ws.onerror = () => {
    wsState.value = 'error'
  }

  ws.onclose = () => {
    wsState.value = 'closed'
    ws = null
    if (shouldReconnect) {
      scheduleReconnect()
    }
  }
}

function scheduleReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
  reconnectAttempt += 1
  const delay = Math.min(1000 * 2 ** Math.max(0, reconnectAttempt - 1), 10000)
  reconnectTimer = setTimeout(() => {
    connectDanmu()
  }, delay)
}

function buildDanmuKey(danmu) {
  if (danmu.clientMessageId) {
    return `client:${danmu.clientMessageId}`
  }
  if (danmu.id) {
    return `id:${danmu.id}`
  }
  return `fallback:${danmu.userId || ''}:${danmu.timePoint || 0}:${danmu.content || ''}`
}

function addIncomingDanmu(danmu) {
  const normalized = {
    ...danmu,
    timePoint: Math.max(0, Number(danmu.timePoint) || 0)
  }
  const key = buildDanmuKey(normalized)
  if (danmuDedupSet.has(key)) {
    return
  }
  danmuDedupSet.add(key)
  historicalDanmus.value.push(normalized)

  if (danmuPlayer) {
    danmuPlayer.addDanmu({
      content: normalized.content,
      time: normalized.timePoint,
      type: 'scroll',
      color: '#ffffff',
      id: normalized.id ?? normalized.clientMessageId
    })
  }
}

function sendDanmu() {
  if (!danmuText.value.trim() || !userStore.isLoggedIn) return
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    connectDanmu()
    return
  }

  const content = danmuText.value.trim()
  const time = videoEl.value ? Math.floor(videoEl.value.currentTime) : 0
  const clientMessageId = `${userStore.userInfo?.id || 'guest'}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const localDanmu = {
    videoId: videoId.value,
    userId: userStore.userInfo?.id,
    username: userStore.userInfo?.username,
    content,
    timePoint: time,
    clientMessageId
  }

  danmuText.value = ''
  ws.send(JSON.stringify({ content, timePoint: time, clientMessageId }))
  // 后端会排除发送者自身，这里本地即时回显，同时用 clientMessageId 去重。
  addIncomingDanmu(localDanmu)
}

function onPlay() {
  if (!played && userStore.isLoggedIn) {
    played = true
    recordPlay(videoId.value).catch(() => {})
  }
}

function onLoadedMetadata() {
  const last = video.value?.lastWatchSeconds
  if (last && last > 0 && videoEl.value) {
    videoEl.value.currentTime = last
  }
}

function onTimeUpdate() {
  if (!videoEl.value || !userStore.isLoggedIn) return
  const t = Math.floor(videoEl.value.currentTime)
  if (progressTimer) clearTimeout(progressTimer)
  progressTimer = setTimeout(() => {
    saveProgress(videoId.value, t).catch(() => {})
  }, 2000)
}

function onSeeking() {}

function onPause() {
  if (videoEl.value && userStore.isLoggedIn) {
    saveProgress(videoId.value, Math.floor(videoEl.value.currentTime)).catch(() => {})
  }
}

function onEnded() {
  if (videoEl.value && userStore.isLoggedIn) {
    saveProgress(videoId.value, Math.floor(videoEl.value.duration || 0)).catch(() => {})
  }
}

function goProfile(authorId) {
  if (authorId) router.push(`/user/${authorId}`)
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) return
  try {
    if (video.value.favorited) {
      await removeFavorite(videoId.value)
      video.value.favorited = false
      video.value.saveCount = Math.max(0, (video.value.saveCount || 0) - 1)
    } else {
      await addFavorite(videoId.value)
      video.value.favorited = true
      video.value.saveCount = (video.value.saveCount || 0) + 1
    }
  } catch (e) {
    console.error(e)
  }
}

async function toggleLike() {
  if (!userStore.isLoggedIn) return
  try {
    if (video.value.liked) {
      await unlike(videoId.value)
      video.value.liked = false
      video.value.likeCount = Math.max(0, (video.value.likeCount || 0) - 1)
    } else {
      await like(videoId.value)
      video.value.liked = true
      video.value.likeCount = (video.value.likeCount || 0) + 1
    }
  } catch (e) {
    console.error(e)
  }
}

async function submitComment() {
  if (!commentText.value.trim() || !userStore.isLoggedIn) return
  try {
    const comment = await addComment({ videoId: videoId.value, content: commentText.value.trim() })
    comments.value.unshift(comment)
    commentText.value = ''
  } catch (e) {
    console.error(e)
  }
}

function resolveCover(item) {
  if (!item) return placeholderCover
  if (item.coverUrl) return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  return placeholderCover
}

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  if (avatar.startsWith('/api/file/avatar') || avatar.startsWith('/file/avatar')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  return String(n)
}

function formatTime(value) {
  if (!value) return ''
  const d = new Date(value)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString()
}

onMounted(async () => {
  await loadVideo()
  connectDanmu()
})

onUnmounted(() => {
  shouldReconnect = false
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (ws) ws.close()
  if (progressTimer) clearTimeout(progressTimer)
  if (danmuPlayer) danmuPlayer.destroy()
  if (danmuControlPanel) danmuControlPanel.destroy()
  if (videoEl.value && userStore.isLoggedIn) {
    saveProgress(videoId.value, Math.floor(videoEl.value.currentTime)).catch(() => {})
  }
})
</script>

<style scoped>
.video-detail { max-width: 960px; margin: 0 auto; padding-bottom: 40px; }
.player-area { position: relative; background: #000; border-radius: 8px; overflow: hidden; aspect-ratio: 16 / 9; }
.video-player { width: 100%; height: 100%; display: block; }
.danmu-toggle { position: absolute; right: 14px; bottom: 14px; padding: 6px 12px; font-size: 12px; color: #fff; background: rgba(0, 0, 0, 0.55); border-radius: 14px; border: 1px solid rgba(255, 255, 255, 0.3); cursor: pointer; backdrop-filter: blur(4px); }
.danmu-toggle:hover { background: rgba(0, 0, 0, 0.7); }
.danmu-container { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.danmu-container.hidden { display: none; }
.content { margin-top: 20px; padding: 20px; background: #fff; border-radius: 8px; }
.title { font-size: 20px; font-weight: 600; margin-bottom: 12px; }
.stats { display: flex; gap: 24px; margin-bottom: 16px; color: var(--text-secondary); font-size: 14px; }
.stats .icon { margin-right: 4px; }
.like, .favorite { cursor: pointer; transition: color 0.2s; }
.like:hover, .like.active, .favorite:hover, .favorite.active { color: var(--bili-pink); }
.author-bar { cursor: pointer; display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.avatar { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.author-name { font-size: 15px; }
.author-bar:hover .author-name { color: var(--bili-pink); }
.desc { font-size: 14px; color: var(--text-secondary); line-height: 1.6; margin-bottom: 24px; }
.danmu-input-wrap { display: flex; gap: 12px; margin-bottom: 12px; }
.danmu-input-wrap input { flex: 1; padding: 10px 16px; border: 1px solid var(--border-color); border-radius: 8px; font-size: 14px; }
.danmu-input-wrap input:focus { outline: none; border-color: var(--bili-pink); }
.btn-danmu { padding: 10px 20px; font-size: 14px; color: #fff; background: var(--bili-pink); border-radius: 8px; }
.btn-danmu:disabled { cursor: not-allowed; opacity: 0.65; }
.btn-danmu.toggle { background: #2f2f2f; border: 1px solid #3f3f3f; }
.btn-danmu.toggle:hover { background: #1f1f1f; }
.danmu-status { margin-bottom: 24px; font-size: 13px; color: var(--text-secondary); }
.danmu-status.open { color: #16a34a; }
.danmu-status.connecting { color: #2563eb; }
.danmu-status.error, .danmu-status.closed { color: #dc2626; }
.comments h3 { font-size: 16px; margin-bottom: 16px; }
.comment-input, .comment-item { display: flex; gap: 12px; margin-bottom: 20px; }
.avatar-sm { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.input-wrap { flex: 1; }
.input-wrap textarea { width: 100%; padding: 12px; border: 1px solid var(--border-color); border-radius: 8px; resize: none; font-size: 14px; margin-bottom: 8px; }
.btn-comment { padding: 8px 20px; font-size: 14px; color: #fff; background: var(--bili-pink); border-radius: 6px; }
.comment-login { padding: 20px; text-align: center; color: var(--text-secondary); background: var(--bg-gray); border-radius: 8px; margin-bottom: 20px; }
.comment-body .username { font-size: 14px; color: var(--bili-pink); margin-right: 8px; }
.comment-body .content { font-size: 14px; line-height: 1.6; margin: 4px 0; }
.comment-body .time { font-size: 12px; color: var(--text-secondary); }
.loading { text-align: center; padding: 60px; color: var(--text-secondary); }
</style>
