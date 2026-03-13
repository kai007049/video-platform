<template>
  <div class="home">
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'recommend' }" @click="switchTab('recommend')">推荐</button>
      <button class="tab" :class="{ active: activeTab === 'latest' }" @click="switchTab('latest')">最新</button>
      <button class="tab" :class="{ active: activeTab === 'hot' }" @click="switchTab('hot')">最热</button>
      <button v-if="currentKeyword" class="tab" :class="{ active: activeTab === 'search' }">
        搜索：{{ currentKeyword }}
      </button>
    </div>
    <div class="video-grid">
      <div
        v-for="item in videoList"
        :key="item.id"
        class="video-card"
        @click="goVideo(item.id)"
      >
        <div class="cover-wrap">
          <img
            :src="resolveCover(item)"
            :alt="item.title"
            class="cover"
            @error="onCoverError"
          />
          <span class="play-count">
            <span class="icon">▶</span> {{ formatCount(item.playCount) }}
          </span>
          <span class="comment-count">
            <span class="icon">💬</span> {{ formatCount(item.commentCount) }}
          </span>
          <span class="duration">{{ formatDuration(item.durationSeconds) }}</span>
        </div>
        <div class="info">
          <h3 class="title">{{ item.title }}</h3>
          <div class="meta" @click.stop="goProfile(item.authorId)">
            <img :src="resolveAvatar(item.authorAvatar)" class="avatar" alt="" @error="onAvatarError" />
            <span class="author">{{ item.authorName || '用户' }}</span>
          </div>
        </div>
      </div>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-if="!loading && hasMore && videoList.length" class="load-more">
      <button @click="loadMore">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVideoList, getRecommended, getHotList, searchVideos } from '../api/video'

const router = useRouter()
const route = useRoute()
const activeTab = ref('recommend')
const videoList = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const pageSize = 12
const currentKeyword = ref('')
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const defaultAvatar = new URL('../assets/avatar-placeholder.png', import.meta.url).href

const fetchApi = (p) => {
  if (activeTab.value === 'search') {
    return searchVideos(currentKeyword.value, p, pageSize)
  }
  if (activeTab.value === 'hot') {
    return getHotList(p, pageSize)
  }
  return activeTab.value === 'recommend'
    ? getRecommended(p, pageSize)
    : getVideoList(p, pageSize)
}

async function fetchList(isMore = false) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await fetchApi(isMore ? page.value : 1)
    const list = res.records || []
    if (isMore) {
      videoList.value.push(...list)
    } else {
      videoList.value = list
    }
    hasMore.value = res.current < res.pages
    if (isMore) page.value++
    else page.value = 2
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  page.value = 1
  fetchList(false)
}

function syncFromQuery() {
  const keyword = route.query.keyword ? String(route.query.keyword) : ''
  const tab = route.query.tab ? String(route.query.tab) : ''
  if (keyword) {
    currentKeyword.value = keyword
    activeTab.value = 'search'
  } else if (tab && ['recommend', 'latest', 'hot'].includes(tab)) {
    activeTab.value = tab
  }
}

function goProfile(authorId) {
  if (authorId) router.push(`/user/${authorId}`)
}

function loadMore() {
  fetchList(true)
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  }
  return placeholderCover
}

function onCoverError(event) {
  event.target.src = placeholderCover
}

function resolveAvatar(avatar) {
  if (!avatar) return defaultAvatar
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function onAvatarError(event) {
  event.target.src = defaultAvatar
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatDuration(sec) {
  if (sec === null || sec === undefined) return '--:--'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m)}:${String(s).padStart(2, '0')}`
}

watch(() => route.query, () => {
  syncFromQuery()
  page.value = 1
  fetchList(false)
}, { immediate: true })
</script>

<style scoped>
.home {
  padding-bottom: 40px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
}

.tab {
  padding: 8px 20px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  cursor: pointer;
}

.tab.active {
  color: var(--bili-pink);
  border-color: rgba(251,114,153,.5);
  box-shadow: 0 2px 6px rgba(251,114,153,.2);
}

.meta {
  cursor: pointer;
}

.meta:hover .author {
  color: var(--bili-pink);
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.video-card {
  cursor: pointer;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  transition: transform 0.2s, box-shadow 0.2s;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,.12);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16/9;
  background: var(--bg-gray);
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-count {
  position: absolute;
  left: 8px;
  bottom: 8px;
  padding: 2px 6px;
  font-size: 12px;
  color: #fff;
  background: rgba(0,0,0,.6);
  border-radius: 4px;
}

.comment-count {
  position: absolute;
  left: 8px;
  bottom: 32px;
  padding: 2px 6px;
  font-size: 12px;
  color: #fff;
  background: rgba(0,0,0,.6);
  border-radius: 4px;
}

.icon {
  font-size: 10px;
}

.duration {
  position: absolute;
  right: 8px;
  bottom: 8px;
  font-size: 12px;
  color: #fff;
  background: rgba(0,0,0,.6);
  padding: 2px 6px;
  border-radius: 4px;
}

.info {
  padding: 12px;
}

.title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}

.loading,
.load-more {
  text-align: center;
  padding: 24px;
  color: var(--text-secondary);
}

.load-more button {
  padding: 8px 24px;
  font-size: 14px;
  color: var(--bili-pink);
  background: transparent;
  border: 1px solid var(--bili-pink);
  border-radius: 6px;
}

.load-more button:hover {
  background: rgba(251,114,153,.08);
}

@media (max-width: 1200px) {
  .video-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .video-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
