<template>
  <div class="creator-dashboard">
    <h2>创作者中心</h2>
    <div class="stats-grid" v-if="stats">
      <div class="stat-card">
        <span class="label">总播放量</span>
        <span class="value">{{ formatCount(stats.totalPlayCount) }}</span>
      </div>
      <div class="stat-card">
        <span class="label">总点赞数</span>
        <span class="value">{{ formatCount(stats.totalLikeCount) }}</span>
      </div>
      <div class="stat-card">
        <span class="label">视频数</span>
        <span class="value">{{ stats.videoCount }}</span>
      </div>
      <div class="stat-card">
        <span class="label">粉丝数</span>
        <span class="value">{{ formatCount(stats.fanCount) }}</span>
      </div>
    </div>
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else class="tabs">
      <div class="tab-header">
        <button
          v-for="item in tabs"
          :key="item.key"
          :class="['tab-btn', { active: activeTab === item.key }]"
          @click="switchTab(item.key)"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="tab-body">
        <div v-if="currentList.length === 0" class="empty">暂无数据</div>
        <div v-else class="video-grid">
          <div v-for="video in currentList" :key="video.id" class="video-card">
            <div class="cover">
              <img :src="resolveCover(video)" alt="cover" />
              <span class="duration">{{ formatDuration(video.durationSeconds) }}</span>
            </div>
            <div class="info">
              <div class="title" :title="video.title">{{ video.title }}</div>
              <div class="meta">
                <span>▶ {{ formatCount(video.playCount) }}</span>
                <span>💬 {{ formatCount(video.commentCount) }}</span>
                <span>👍 {{ formatCount(video.likeCount) }}</span>
              </div>
              <div class="actions" v-if="activeTab === 'works'">
                <button class="btn-delete" @click="confirmDelete(video.id)">删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getCreatorStats } from '../api/user'
import {
  getCreatorVideos,
  getLikedVideos,
  getFavoriteVideos,
  getHistoryVideos,
  deleteVideo
} from '../api/video'

const stats = ref(null)
const loading = ref(true)
const activeTab = ref('works')
const listMap = ref({
  works: [],
  liked: [],
  favorite: [],
  history: []
})

const tabs = [
  { key: 'works', label: '我的作品' },
  { key: 'liked', label: '点赞记录' },
  { key: 'favorite', label: '收藏记录' },
  { key: 'history', label: '历史观看' }
]

const defaultCover = '/favicon.svg'

const currentList = computed(() => listMap.value[activeTab.value] || [])

async function loadStats() {
  stats.value = await getCreatorStats()
}

async function loadList(key) {
  if (key === 'works') {
    listMap.value.works = (await getCreatorVideos()).records || []
  } else if (key === 'liked') {
    listMap.value.liked = (await getLikedVideos()).records || []
  } else if (key === 'favorite') {
    listMap.value.favorite = (await getFavoriteVideos()).records || []
  } else if (key === 'history') {
    listMap.value.history = (await getHistoryVideos()).records || []
  }
}

async function switchTab(key) {
  activeTab.value = key
  await loadList(key)
}

async function confirmDelete(id) {
  if (!confirm('确认删除该视频吗？')) return
  await deleteVideo(id)
  await loadList('works')
}

async function load() {
  try {
    await Promise.all([loadStats(), loadList(activeTab.value)])
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function resolveCover(video) {
  if (video.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(video.coverUrl)}`
  }
  return defaultCover
}

onMounted(load)
</script>

<style scoped>
.creator-dashboard {
  max-width: 1100px;
  margin: 0 auto;
}

h2 {
  font-size: 24px;
  margin-bottom: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  text-align: center;
}

.stat-card .label {
  display: block;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.stat-card .value {
  font-size: 24px;
  font-weight: 600;
  color: var(--bili-pink);
}

.loading {
  text-align: center;
  padding: 40px;
  color: var(--text-secondary);
}

.tabs {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px 24px;
}

.tab-header {
  display: flex;
  gap: 12px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 20px;
}

.tab-btn {
  padding: 10px 16px;
  border-radius: 8px;
  background: none;
  font-weight: 500;
  color: var(--text-secondary);
}

.tab-btn.active {
  color: var(--bili-pink);
  background: rgba(255, 102, 153, 0.1);
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.video-card {
  border: 1px solid var(--border-color);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #f5f5f5;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.duration {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 6px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 4px;
}

.info {
  padding: 12px;
}

.title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  height: 38px;
  overflow: hidden;
}

.meta {
  display: flex;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 6px;
}

.actions {
  margin-top: 10px;
}

.btn-delete {
  padding: 6px 10px;
  border-radius: 6px;
  background: #ff4d4f;
  color: #fff;
  font-size: 12px;
}

.btn-delete:hover {
  background: #ff7875;
}

.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: 40px 0;
}
</style>
