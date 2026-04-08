<template>
  <div class="home">
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'recommend' }" @click="switchTab('recommend')">推荐</button>
      <button class="tab" :class="{ active: activeTab === 'latest' }" @click="switchTab('latest')">最新</button>
      <button class="tab" :class="{ active: activeTab === 'hot' }" @click="switchTab('hot')">热门</button>
    </div>

    <div v-if="error" class="ai-answer error">{{ error }}</div>

    <div class="video-grid">
      <div v-for="item in videoList" :key="item.id" class="video-card" @click="goVideo(item.id)">
        <div class="cover-wrap">
          <img :src="resolveCover(item)" :alt="item.title" class="cover" @error="onCoverError" />
          <div class="cover-overlay">
            <span class="stat"><span class="stat-icon">▶</span> {{ formatCount(item.playCount) }}</span>
            <span class="stat"><span class="stat-icon">💬</span> {{ formatCount(item.commentCount) }}</span>
          </div>
          <span class="duration-badge">{{ formatDuration(item.durationSeconds) }}</span>
        </div>
        <div class="card-info">
          <h3 class="card-title">{{ item.title }}</h3>
          <div class="card-author" @click.stop="goProfile(item.authorId)">
            <span class="author-name">{{ item.authorName || '用户' }}</span>
          </div>
          <div class="card-meta">
            <span class="meta-date">{{ formatDate(item.createTime) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading"><div class="loading-dots"><span></span><span></span><span></span></div></div>

    <div v-if="!loading && hasMore && videoList.length" class="load-more">
      <button @click="loadMore">加载更多</button>
    </div>

    <div v-else-if="!loading && !videoList.length" class="empty">
      <div class="empty-icon">📵</div>
      <div>{{ error || '暂无视频' }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVideoList, getRecommended, getHotList } from '../api/video'

const router = useRouter()
const route = useRoute()
const activeTab = ref('recommend')
const videoList = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const pageSize = 16
const error = ref('')
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href

const fetchApi = (p) => {
  if (activeTab.value === 'hot') return getHotList(p, pageSize)
  return activeTab.value === 'recommend' ? getRecommended(p, pageSize) : getVideoList(p, pageSize)
}

async function fetchList(isMore = false) {
  if (loading.value) return
  loading.value = true
  if (!isMore) error.value = ''
  try {
    const res = await fetchApi(isMore ? page.value : 1)
    const list = res.records || []
    if (isMore) videoList.value.push(...list)
    else videoList.value = list
    hasMore.value = res.current < res.pages
    page.value = isMore ? page.value + 1 : 2
  } catch (e) {
    console.error('Failed to fetch video list:', e)
    if (!isMore) {
      videoList.value = []
      hasMore.value = false
      error.value = e.message || '视频列表加载失败'
    }
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
  const tab = route.query.tab ? String(route.query.tab) : ''
  if (tab && ['recommend', 'latest', 'hot'].includes(tab)) activeTab.value = tab
}

function loadMore() { fetchList(true) }
function goVideo(id) { router.push(`/video/${id}`) }
function goProfile(authorId) { if (authorId) router.push(`/user/${authorId}`) }
function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  return placeholderCover
}
function onCoverError(event) { event.target.src = placeholderCover }
function formatCount(n) { if (!n) return '0'; if (n >= 10000) return `${(n / 10000).toFixed(1)}万`; return String(n) }
function formatDuration(sec) { if (sec == null) return '--:--'; const m = Math.floor(sec / 60); const s = sec % 60; return `${m}:${String(s).padStart(2, '0')}` }
function formatDate(value) { return value ? String(value).slice(5, 10) : '' }

watch(() => route.query, () => {
  syncFromQuery()
  page.value = 1
  fetchList(false)
}, { immediate: true })
</script>

<style scoped>
.home { padding-bottom: 40px; }
.ai-answer.error { background: #fff5f5; border-color: #ffd7d7; color: #c0392b; }
.tabs { display: flex; align-items: center; gap: 4px; margin-bottom: 20px; border-bottom: 1px solid #e3e5e7; }
.tab { position: relative; padding: 10px 16px; font-size: 15px; color: #61666d; background: transparent; border: none; cursor: pointer; }
.tab.active { color: #fb7299; font-weight: 700; }
.tab.active::after { content: ''; position: absolute; left: 8px; right: 8px; bottom: -1px; height: 3px; background: #fb7299; }
.video-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.video-card { cursor: pointer; border-radius: 8px; overflow: hidden; background: #fff; }
.cover-wrap { position: relative; aspect-ratio: 16/9; background: #f4f5f7; overflow: hidden; }
.cover { width: 100%; height: 100%; object-fit: cover; }
.cover-overlay { position: absolute; left: 0; right: 0; bottom: 0; padding: 12px 8px 6px; background: linear-gradient(transparent, rgba(0,0,0,.6)); display: flex; justify-content: space-between; }
.stat { font-size: 12px; color: #fff; }
.duration-badge { position: absolute; right: 6px; bottom: 6px; padding: 2px 6px; background: rgba(0,0,0,.8); color: #fff; font-size: 12px; border-radius: 4px; }
.card-info { padding: 10px; }
.card-title { font-size: 14px; line-height: 1.4; color: #18191c; margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.author-name { font-size: 12px; color: #61666d; }
.card-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #9499a0; }
.loading { display: flex; justify-content: center; padding: 32px 0; }
.loading-dots { display: flex; gap: 6px; }
.loading-dots span { width: 8px; height: 8px; background: #fb7299; border-radius: 50%; animation: bounce 1.2s infinite; }
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,80%,100% { transform: scale(.8); opacity: .5; } 40% { transform: scale(1.2); opacity: 1; } }
.load-more { text-align: center; padding: 28px 0 0; }
.load-more button { padding: 9px 32px; font-size: 14px; color: #fb7299; background: #fff; border: 1px solid #fb7299; border-radius: 20px; }
.empty { text-align: center; padding: 60px 0; color: #9499a0; font-size: 15px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
</style>
