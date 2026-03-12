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
        @ended="onEnded"
        @loadedmetadata="onLoadedMetadata"
      />
      <div
        ref="danmuContainer"
        class="danmu-container"
        :class="{ hidden: !showDanmu }"
      ></div>
      <button class="danmu-toggle" @click="showDanmu = !showDanmu">
        {{ showDanmu ? '关闭弹幕' : '开启弹幕' }}
      </button>
    </div>
    <div class="content">
      <h1 class="title">{{ video.title }}</h1>
      <div class="stats">
        <span class="play"><span class="icon">▶</span> {{ formatCount(video.playCount) }} 播放</span>
        <span class="like" :class="{ active: video.liked }" @click="toggleLike">
          <span class="icon">♥</span> {{ formatCount(video.likeCount) }}
        </span>
        <span class="favorite" :class="{ active: video.favorited }" @click="userStore.isLoggedIn && toggleFavorite()">
          <span class="icon">★</span> {{ formatCount(video.saveCount) }} 收藏
        </span>
      </div>
      <div class="author-bar" @click="goProfile(video.authorId)">
        <img :src="video.authorAvatar || defaultAvatar" class="avatar" alt="" />
        <span class="author-name">{{ video.authorName || '用户' }}</span>
      </div>
      <div v-if="video.description" class="desc">{{ video.description }}</div>

      <!-- 弹幕输入 -->
      <div class="danmu-input-wrap" v-if="userStore.isLoggedIn">
        <input
          v-model="danmuText"
          type="text"
          placeholder="发个弹幕吧~"
          maxlength="256"
          @keyup.enter="sendDanmu"
        />
        <button class="btn-danmu" @click="sendDanmu">发送</button>
        <button class="btn-danmu toggle" @click="showDanmu = !showDanmu">
          {{ showDanmu ? '关闭弹幕' : '开启弹幕' }}
        </button>
      </div>

      <!-- 评论区 -->
      <div class="comments">
        <h3>评论区 ({{ comments.length }})</h3>
        <div v-if="userStore.isLoggedIn" class="comment-input">
          <img :src="userStore.userInfo?.avatar || defaultAvatar" class="avatar-sm" alt="" />
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
            <img :src="c.userAvatar || defaultAvatar" class="avatar-sm" alt="" />
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
  <div v-else class="loading">加载中...</div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getVideoDetail, recordPlay, saveProgress } from '../api/video'
import { getComments, addComment } from '../api/comment'
import { getDanmus } from '../api/danmu'
import { like, unlike } from '../api/like'
import { addFavorite, removeFavorite } from '../api/favorite'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
let progressTimer = null
const video = ref(null)
const videoEl = ref(null)
const danmuContainer = ref(null)
const comments = ref([])
const commentText = ref('')
const danmuText = ref('')
const showDanmu = ref(true)
const placeholderCover = 'https://placehold.co/960x540/f4f4f4/999?text=封面'
const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=user'

let ws = null
let played = false
const historicalDanmus = ref([])
const shownDanmuIds = ref(new Set())

const videoId = computed(() => Number(route.params.id))

async function loadVideo() {
  try {
    video.value = await getVideoDetail(videoId.value)
    await loadComments()
    await loadDanmus()
  } catch (e) {
    console.error(e)
  }
}

async function loadDanmus() {
  try {
    historicalDanmus.value = await getDanmus(videoId.value) || []
    shownDanmuIds.value = new Set()
  } catch (e) {
    console.error(e)
  }
}

async function loadComments() {
  try {
    comments.value = await getComments(videoId.value)
  } catch (e) {
    console.error(e)
  }
}

function connectDanmu() {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = location.host
  const token = userStore.token ? `?token=${userStore.token}` : ''
  const url = `${protocol}//${host}/ws/danmu/${videoId.value}${token}`
  ws = new WebSocket(url)
  ws.onmessage = (e) => {
    try {
      const d = JSON.parse(e.data)
      if (d.error) return
      appendDanmu(d)
    } catch (_) {}
  }
}

function appendDanmu(d) {
  if (!danmuContainer.value || !showDanmu.value) return
  const span = document.createElement('span')
  span.className = 'danmu-item'
  span.textContent = d.content
  span.style.top = Math.random() * 60 + 10 + '%'
  span.style.animationDuration = (10 + Math.random() * 6) + 's'
  span.style.animationDelay = (Math.random() * 0.6) + 's'
  span.style.opacity = '0.95'
  danmuContainer.value.appendChild(span)
  setTimeout(() => span.remove(), 16000)
}

function sendDanmu() {
  if (!danmuText.value.trim() || !userStore.isLoggedIn) return
  const content = danmuText.value.trim()
  const time = videoEl.value ? Math.floor(videoEl.value.currentTime) : 0
  const d = { content, timePoint: time }
  appendDanmu(d)
  danmuText.value = ''
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ content, timePoint: time }))
  }
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
  if (videoEl.value) {
    const t = Math.floor(videoEl.value.currentTime)
    if (showDanmu.value) {
      historicalDanmus.value.forEach((d) => {
        if (d.timePoint <= t + 1 && !shownDanmuIds.value.has(d.id)) {
          shownDanmuIds.value.add(d.id)
          appendDanmu({ ...d })
        }
      })
    }
    if (userStore.isLoggedIn) {
      if (progressTimer) clearTimeout(progressTimer)
      progressTimer = setTimeout(() => {
        saveProgress(videoId.value, t).catch(() => {})
      }, 2000)
    }
  }
}

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
    } else {
      await addFavorite(videoId.value)
      video.value.favorited = true
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
      video.value.likeCount = Math.max(0, video.value.likeCount - 1)
    } else {
      await like(videoId.value)
      video.value.liked = true
      video.value.likeCount++
    }
  } catch (e) {
    console.error(e)
  }
}

async function submitComment() {
  if (!commentText.value.trim() || !userStore.isLoggedIn) return
  try {
    const c = await addComment({
      videoId: videoId.value,
      content: commentText.value.trim()
    })
    comments.value.unshift(c)
    commentText.value = ''
  } catch (e) {
    console.error(e)
  }
}

function resolveCover(item) {
  if (!item) return placeholderCover
  if (item.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  }
  return placeholderCover
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString()
}

onMounted(() => {
  loadVideo()
  if (userStore.isLoggedIn) connectDanmu()
})

onUnmounted(() => {
  if (ws) ws.close()
  if (progressTimer) clearTimeout(progressTimer)
  if (videoEl.value && userStore.isLoggedIn) {
    saveProgress(videoId.value, Math.floor(videoEl.value.currentTime)).catch(() => {})
  }
})
</script>

<style scoped>
.video-detail {
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.player-area {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.video-player {
  width: 100%;
  height: 100%;
  display: block;
}

.danmu-toggle {
  position: absolute;
  right: 14px;
  bottom: 14px;
  padding: 6px 12px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  cursor: pointer;
  backdrop-filter: blur(4px);
}

.danmu-toggle:hover {
  background: rgba(0, 0, 0, 0.7);
}

.danmu-container {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.danmu-container.hidden {
  display: none;
}

.danmu-item {
  position: absolute;
  left: 0;
  white-space: nowrap;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #ffffff;
  text-shadow:
    0 0 2px rgba(0, 0, 0, 0.8),
    0 0 6px rgba(0, 0, 0, 0.6),
    1px 1px 1px rgba(0, 0, 0, 0.9);
  filter: drop-shadow(0 0 2px rgba(0, 0, 0, 0.8));
  animation: danmu-move 12s linear;
  will-change: transform;
}

@keyframes danmu-move {
  from { transform: translateX(110vw); }
  to { transform: translateX(-140vw); }
}

.content {
  margin-top: 20px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
}

.title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 12px;
}

.stats {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.stats .icon {
  margin-right: 4px;
}

.like {
  cursor: pointer;
  transition: color 0.2s;
}

.like:hover,
.like.active,
.favorite:hover,
.favorite.active {
  color: var(--bili-pink);
}

.favorite {
  cursor: pointer;
  transition: color 0.2s;
}

.author-bar {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  font-size: 15px;
}

.author-bar:hover .author-name {
  color: var(--bili-pink);
}

.desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 24px;
}

.danmu-input-wrap {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.danmu-input-wrap input {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
}

.danmu-input-wrap input:focus {
  outline: none;
  border-color: var(--bili-pink);
}

.btn-danmu {
  padding: 10px 20px;
  font-size: 14px;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 8px;
}

.btn-danmu.toggle {
  background: #2f2f2f;
  border: 1px solid #3f3f3f;
}

.btn-danmu.toggle:hover {
  background: #1f1f1f;
}

.comments h3 {
  font-size: 16px;
  margin-bottom: 16px;
}

.comment-input,
.comment-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.avatar-sm {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.input-wrap {
  flex: 1;
}

.input-wrap textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  resize: none;
  font-size: 14px;
  margin-bottom: 8px;
}

.btn-comment {
  padding: 8px 20px;
  font-size: 14px;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 6px;
}

.comment-login {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  background: var(--bg-gray);
  border-radius: 8px;
  margin-bottom: 20px;
}

.comment-body .username {
  font-size: 14px;
  color: var(--bili-pink);
  margin-right: 8px;
}

.comment-body .content {
  font-size: 14px;
  line-height: 1.6;
  margin: 4px 0;
}

.comment-body .time {
  font-size: 12px;
  color: var(--text-secondary);
}

.loading {
  text-align: center;
  padding: 60px;
  color: var(--text-secondary);
}
</style>
