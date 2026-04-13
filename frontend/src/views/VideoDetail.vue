<template>
  <VideoDetailSkeleton v-if="!video && !loadError" />
  <div class="video-detail" v-else-if="video">
    <div class="main-content">
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
      <div class="video-info">
        <h1 class="title">{{ video.title }}</h1>
        <div class="author-info">
          <div class="author-bar" @click="goProfile(video.authorId)">
            <img :src="resolveAvatar(video.authorAvatar)" class="avatar" alt="" />
            <div class="author-details">
              <span class="author-name">{{ video.authorName || '用户' }}</span>
              <span class="author-fans">{{ formatCount(video.authorFans || 0) }} 粉丝</span>
            </div>
          </div>
          <button class="btn-follow" @click="toggleFollow">
            {{ video.followed ? '已关注' : '关注' }}
          </button>
        </div>
        <div class="stats">
          <span class="play"><span class="icon">▶</span> {{ formatCount(video.playCount) }} 播放</span>
          <span class="like" :class="{ active: video.liked }" @click="toggleLike">
            <span class="icon">❤</span> {{ formatCount(video.likeCount) }}
          </span>
          <span class="favorite" :class="{ active: video.favorited }" @click="userStore.isLoggedIn && toggleFavorite()">
            <span class="icon">★</span> {{ formatCount(video.saveCount) }} 收藏
          </span>
          <span class="share" @click="shareVideo">
            <span class="icon">↗</span> 分享
          </span>
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
    <div class="sidebar">
      <div class="ai-video-library">
        <div class="ai-header">
          <span class="ai-icon">✨</span>
          <span>AI 视频智库</span>
        </div>
        <div class="ai-content">
          <div class="ai-item" v-for="(item, index) in aiRecommendations" :key="index">
            <div class="ai-time">{{ item.time }}</div>
            <div class="ai-title">{{ item.title }}</div>
          </div>
          <button class="btn-ai">提问 AI 助手</button>
        </div>
      </div>
      <div class="related-videos">
        <h3>相关推荐</h3>
        <div class="related-item" v-for="(item, index) in relatedVideos" :key="index">
          <div class="related-cover">
            <img src="https://via.placeholder.com/120x68" alt="" />
            <div class="related-duration">{{ formatDuration(300) }}</div>
          </div>
          <div class="related-info">
            <div class="related-title">{{ item.title }}</div>
            <div class="related-stats">{{ formatCount(12345) }} 播放</div>
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
import DanmuPlayer from '../utils/danmu-player-optimized.js'
import DanmuControlPanel from '../utils/danmu-control-panel.js'
import VideoDetailSkeleton from '../components/VideoDetailSkeleton.vue'

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

const aiRecommendations = ref([
  { time: '02:45', title: '核心原理：Redis 单线程模型深度解析' },
  { time: '15:20', title: '实战场景：如何解决百万级 QPS 缓存雪崩' },
  { time: '32:10', title: '架构演进：从 Sentinel 到 Cluster 集群模式' }
])

const relatedVideos = ref([
  { title: '系统架构师进阶：从单体到微服务的蜕变过程' },
  { title: '高并发系统设计：百万级流量处理方案' },
  { title: '分布式系统核心：一致性算法与实践' }
])

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
  try {
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
      console.warn('WebSocket connection error, skipping reconnection')
      shouldReconnect = false
    }

    ws.onclose = () => {
      wsState.value = 'closed'
      ws = null
      if (shouldReconnect) {
        scheduleReconnect()
      }
    }
  } catch (e) {
    console.error('WebSocket connection failed:', e)
    wsState.value = 'error'
    shouldReconnect = false
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

async function toggleFollow() {
  if (!userStore.isLoggedIn) return
  try {
    if (video.value.followed) {
      // 取消关注逻辑
      video.value.followed = false
      video.value.authorFans = Math.max(0, (video.value.authorFans || 0) - 1)
    } else {
      // 关注逻辑
      video.value.followed = true
      video.value.authorFans = (video.value.authorFans || 0) + 1
    }
  } catch (e) {
    console.error(e)
  }
}

function shareVideo() {
  if (navigator.share) {
    navigator.share({
      title: video.value.title,
      text: video.value.description,
      url: window.location.href
    })
  } else {
    navigator.clipboard.writeText(window.location.href)
    alert('链接已复制到剪贴板')
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
.video-detail { max-width: 1200px; margin: 0 auto; padding: 20px 0 40px; display: grid; grid-template-columns: 1fr 320px; gap: 32px; }
.main-content { grid-column: 1; }
.sidebar { grid-column: 2; }

.player-area { position: relative; background: #000; border-radius: 12px; overflow: hidden; aspect-ratio: 16 / 9; box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15); }
.video-player { width: 100%; height: 100%; display: block; }
.danmu-toggle { position: absolute; right: 14px; bottom: 14px; padding: 6px 12px; font-size: 12px; color: #fff; background: rgba(0, 0, 0, 0.55); border-radius: 14px; border: 1px solid rgba(255, 255, 255, 0.3); cursor: pointer; backdrop-filter: blur(4px); }
.danmu-toggle:hover { background: rgba(0, 0, 0, 0.7); }
.danmu-container { position: absolute; inset: 0; pointer-events: none; overflow: hidden; }
.danmu-container.hidden { display: none; }

.video-info { margin-top: 20px; padding: 24px; background: #fff; border-radius: 12px; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.title { font-size: 24px; font-weight: 700; margin-bottom: 16px; line-height: 1.3; }

.author-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.author-bar { cursor: pointer; display: flex; align-items: center; gap: 12px; }
.avatar { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; border: 1px solid #e5e7eb; }
.author-details { display: flex; flex-direction: column; }
.author-name { font-size: 16px; font-weight: 600; margin-bottom: 2px; }
.author-fans { font-size: 12px; color: #64748b; }
.btn-follow { padding: 8px 24px; font-size: 14px; color: #fff; background: #00a1d6; border-radius: 16px; border: none; cursor: pointer; font-weight: 500; }
.btn-follow:hover { background: #0086b3; }

.stats { display: flex; gap: 24px; margin-bottom: 20px; color: #64748b; font-size: 14px; }
.stats .icon { margin-right: 6px; }
.like, .favorite, .share { cursor: pointer; transition: color 0.2s; display: flex; align-items: center; }
.like:hover, .like.active, .favorite:hover, .favorite.active { color: #f472b6; }
.share:hover { color: #00a1d6; }

.desc { font-size: 14px; color: #64748b; line-height: 1.6; margin-bottom: 24px; }

.danmu-input-wrap { display: flex; gap: 12px; margin-bottom: 12px; }
.danmu-input-wrap input { flex: 1; padding: 10px 16px; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 14px; }
.danmu-input-wrap input:focus { outline: none; border-color: #00a1d6; }
.btn-danmu { padding: 10px 20px; font-size: 14px; color: #fff; background: #00a1d6; border-radius: 8px; border: none; cursor: pointer; }
.btn-danmu:disabled { cursor: not-allowed; opacity: 0.65; }
.btn-danmu.toggle { background: #2f2f2f; border: 1px solid #3f3f3f; }
.btn-danmu.toggle:hover { background: #1f1f1f; }

.danmu-status { margin-bottom: 24px; font-size: 13px; color: #64748b; }
.danmu-status.open { color: #16a34a; }
.danmu-status.connecting { color: #2563eb; }
.danmu-status.error, .danmu-status.closed { color: #dc2626; }

.comments h3 { font-size: 18px; font-weight: 600; margin-bottom: 20px; }
.comment-input, .comment-item { display: flex; gap: 12px; margin-bottom: 20px; }
.avatar-sm { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.input-wrap { flex: 1; }
.input-wrap textarea { width: 100%; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; resize: none; font-size: 14px; margin-bottom: 8px; }
.btn-comment { padding: 8px 20px; font-size: 14px; color: #fff; background: #00a1d6; border-radius: 6px; border: none; cursor: pointer; }
.comment-login { padding: 20px; text-align: center; color: #64748b; background: #f9fafb; border-radius: 8px; margin-bottom: 20px; }
.comment-body .username { font-size: 14px; color: #00a1d6; margin-right: 8px; font-weight: 500; }
.comment-body .content { font-size: 14px; line-height: 1.6; margin: 4px 0; }
.comment-body .time { font-size: 12px; color: #94a3b8; }

.ai-video-library { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 16px; padding: 24px; margin-bottom: 24px; color: white; }
.ai-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; font-size: 16px; font-weight: 600; }
.ai-icon { font-size: 20px; }
.ai-content { display: flex; flex-direction: column; gap: 16px; }
.ai-item { display: flex; flex-direction: column; gap: 4px; }
.ai-time { font-size: 12px; opacity: 0.8; font-weight: 500; }
.ai-title { font-size: 14px; line-height: 1.4; font-weight: 500; }
.btn-ai { padding: 12px; font-size: 14px; color: #333; background: white; border-radius: 8px; border: none; cursor: pointer; font-weight: 600; margin-top: 8px; }
.btn-ai:hover { background: #f0f0f0; }

.related-videos { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.related-videos h3 { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.related-item { display: flex; gap: 12px; margin-bottom: 16px; cursor: pointer; }
.related-item:hover .related-title { color: #00a1d6; }
.related-cover { position: relative; width: 120px; height: 68px; border-radius: 6px; overflow: hidden; flex-shrink: 0; }
.related-cover img { width: 100%; height: 100%; object-fit: cover; }
.related-duration { position: absolute; bottom: 4px; right: 4px; background: rgba(0, 0, 0, 0.7); color: white; font-size: 10px; padding: 2px 6px; border-radius: 4px; }
.related-info { flex: 1; min-width: 0; }
.related-title { font-size: 13px; font-weight: 500; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; margin-bottom: 4px; }
.related-stats { font-size: 11px; color: #94a3b8; }

.loading { text-align: center; padding: 60px; color: #64748b; grid-column: 1 / -1; }

@media (max-width: 1024px) {
  .video-detail { grid-template-columns: 1fr; }
  .sidebar { grid-column: 1; }
  .ai-video-library { margin-top: 24px; }
}
</style>
