<template>
  <div class="home">
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'recommend' }" @click="switchTab('recommend')">推荐</button>
      <button class="tab" :class="{ active: activeTab === 'latest' }" @click="switchTab('latest')">最新</button>
      <button class="tab" :class="{ active: activeTab === 'hot' }" @click="switchTab('hot')">热门</button>
      <button v-if="currentKeyword" class="tab active">搜索：{{ currentKeyword }}</button>
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
          <div class="cover-overlay">
            <span class="stat">
              <span class="stat-icon">▶</span> {{ formatCount(item.playCount) }}
            </span>
            <span class="stat">
              <span class="stat-icon">💬</span> {{ formatCount(item.commentCount) }}
            </span>
          </div>
          <span class="duration-badge">{{ formatDuration(item.durationSeconds) }}</span>
        </div>
        <div class="card-info">
          <h3 class="card-title">{{ item.title }}</h3>
          <div class="card-author" @click.stop="goProfile(item.authorId)">
            <span class="author-name">{{ item.authorName || '用户' }}</span>
          </div>
          <div class="card-meta">
            <span class="meta-tag">{{ item.categoryName || '视频' }}</span>
            <span class="meta-dot">·</span>
            <span class="meta-date">{{ item.createTime ? String(item.createTime).slice(5, 10) : '' }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading">
      <div class="loading-dots">
        <span></span><span></span><span></span>
      </div>
    </div>

    <div v-if="!loading && hasMore && videoList.length" class="load-more">
      <button @click="loadMore">加载更多</button>
    </div>

    <div v-if="!loading && !videoList.length" class="empty">
      <div class="empty-icon">📺</div>
      <div>暂无视频</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVideoList, getRecommended, getHotList, searchVideos } from '../api/video'

const router = useRouter()
const route = useRoute()
const activeTab = ref('recommend')
const videoList = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const pageSize = 16
const currentKeyword = ref('')
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href

const fetchApi = (p) => {
  if (activeTab.value === 'search') return searchVideos(currentKeyword.value, p, pageSize)
  if (activeTab.value === 'hot') return getHotList(p, pageSize)
  return activeTab.value === 'recommend' ? getRecommended(p, pageSize) : getVideoList(p, pageSize)
}

async function fetchList(isMore = false) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await fetchApi(isMore ? page.value : 1)
    const list = res.records || []
    if (isMore) videoList.value.push(...list)
    else videoList.value = list
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

function loadMore() { fetchList(true) }
function goVideo(id) { router.push(`/video/${id}`) }

function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  return placeholderCover
}

function onCoverError(event) { event.target.src = placeholderCover }

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatDuration(sec) {
  if (!sec) return '--:--'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
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

/* ===== Tabs ===== */
.tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 1px solid #e3e5e7;
  padding-bottom: 0;
}

.tab {
  position: relative;
  padding: 10px 16px;
  font-size: 15px;
  color: #61666d;
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  transition: color 0.2s;
}

.tab:hover { color: #18191c; }

.tab.active {
  color: #fb7299;
  font-weight: 700;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: -1px;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #fb7299;
}

/* ===== Video Grid ===== */
.video-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.video-card {
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: transform 0.2s, box-shadow 0.2s;
}

.video-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
}

/* Cover */
.cover-wrap {
  position: relative;
  aspect-ratio: 16/9;
  background: #f4f5f7;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.video-card:hover .cover {
  transform: scale(1.03);
}

.cover-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 8px 6px;
  background: linear-gradient(transparent, rgba(0,0,0,.6));
  display: flex;
  justify-content: space-between;
  align-items: center;
  opacity: 1;
}

.stat {
  font-size: 12px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 3px;
  text-shadow: 0 1px 2px rgba(0,0,0,.5);
}

.stat-icon { font-size: 11px; }

.duration-badge {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 2px 6px;
  background: rgba(0,0,0,.8);
  color: #fff;
  font-size: 12px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

/* Card Info */
.card-info {
  padding: 10px;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: #18191c;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-author {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  cursor: pointer;
}

.author-name {
  font-size: 12px;
  color: #61666d;
  transition: color 0.15s;
}

.card-author:hover .author-name {
  color: #fb7299;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9499a0;
}

.meta-tag {
  padding: 1px 6px;
  background: #f4f5f7;
  border-radius: 4px;
  color: #9499a0;
  font-size: 11px;
}

.meta-dot { color: #c9cdd4; }

/* Loading */
.loading {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.loading-dots {
  display: flex;
  gap: 6px;
  align-items: center;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  background: #fb7299;
  border-radius: 50%;
  animation: bounce 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1.2); opacity: 1; }
}

/* Load more */
.load-more {
  text-align: center;
  padding: 28px 0 0;
}

.load-more button {
  padding: 9px 32px;
  font-size: 14px;
  color: #fb7299;
  background: #fff;
  border: 1px solid #fb7299;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.load-more button:hover {
  background: #fb7299;
  color: #fff;
}

/* Empty */
.empty {
  text-align: center;
  padding: 60px 0;
  color: #9499a0;
  font-size: 15px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

/* Responsive */
@media (max-width: 1600px) {
  .video-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

@media (max-width: 1200px) {
  .video-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .video-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
}

@media (max-width: 480px) {
  .video-grid { grid-template-columns: 1fr; }
}
</style>
