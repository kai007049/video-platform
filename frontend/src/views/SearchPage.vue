<template>
  <div class="search-page">
    <div class="search-header">
      <h1>搜索结果</h1>
      <p v-if="keyword" class="search-keyword">关键词：{{ keyword }}</p>
    </div>

    <div class="search-content">
      <div class="search-filters">
        <div class="filter-tabs">
          <button
            v-for="tab in filterTabs"
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="sort-options" v-if="activeTab === 'video'">
          <span>排序：</span>
          <button
            v-for="sort in sortOptions"
            :key="sort.key"
            :class="{ active: activeSort === sort.key }"
            @click="switchSort(sort.key)"
          >
            {{ sort.label }}
          </button>
        </div>
      </div>

      <div class="search-results">
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="!keyword" class="no-results">
          <p>请输入关键词后再搜索</p>
        </div>
        <div v-else-if="noResults" class="no-results">
          <p>未找到相关内容</p>
          <p>请尝试其他关键词</p>
        </div>

        <div v-else-if="activeTab === 'video'" class="video-list">
          <div v-for="video in videos" :key="video.id" class="video-item" @click="goVideo(video.id)">
            <div class="video-cover">
              <img :src="resolveCover(video)" alt="视频封面" />
              <span class="video-duration">{{ formatDuration(video.durationSeconds) }}</span>
            </div>
            <div class="video-info">
              <h3 class="video-title">{{ video.title }}</h3>
              <div class="video-meta">
                <span class="video-up">{{ video.authorName || '未知作者' }}</span>
                <span class="video-views">{{ formatNumber(video.playCount || 0) }} 播放</span>
                <span class="video-date">{{ formatDate(video.createTime) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="user-list">
          <div v-for="user in users" :key="user.id" class="user-item" @click="goUser(user.id)">
            <img :src="resolveAvatar(user.avatar)" alt="头像" class="user-avatar" />
            <div class="user-info">
              <div class="user-name">{{ user.username }}</div>
              <div class="user-extra">{{ user.signature || '这个用户还没有简介' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchUsers, searchVideos } from '../api/video'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const loading = ref(false)
const videos = ref([])
const users = ref([])
const noResults = ref(false)
const activeTab = ref('video')
const activeSort = ref('comprehensive')

const defaultCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href

const filterTabs = [
  { key: 'video', label: '视频' },
  { key: 'user', label: '用户' }
]

const sortOptions = [
  { key: 'comprehensive', label: '综合排序' },
  { key: 'viewCount', label: '播放量' },
  { key: 'createTime', label: '最新发布' }
]

/**
 * 监听路由变化，保证从顶部搜索框再次搜索时能重新拉取数据。
 */
watch(
  () => route.query,
  async (query) => {
    keyword.value = typeof query.keyword === 'string' ? query.keyword.trim() : ''
    activeTab.value = query.type === 'user' ? 'user' : 'video'
    activeSort.value = typeof query.sortBy === 'string' ? query.sortBy : 'comprehensive'

    if (!keyword.value) {
      videos.value = []
      users.value = []
      noResults.value = false
      return
    }

    await performSearch()
  },
  { immediate: true }
)

async function performSearch() {
  if (!keyword.value) {
    return
  }

  loading.value = true
  try {
    if (activeTab.value === 'user') {
      const result = await searchUsers(keyword.value, 1, 20)
      users.value = Array.isArray(result) ? result : []
      videos.value = []
      noResults.value = users.value.length === 0
      return
    }

    const result = await searchVideos(keyword.value, 1, 20, activeSort.value)
    videos.value = Array.isArray(result?.records) ? result.records : []
    users.value = []
    noResults.value = videos.value.length === 0
  } catch (error) {
    console.error('搜索失败:', error)
    videos.value = []
    users.value = []
    noResults.value = true
  } finally {
    loading.value = false
  }
}

function switchTab(tabKey) {
  router.replace({
    path: '/search',
    query: {
      keyword: keyword.value,
      type: tabKey,
      sortBy: activeSort.value
    }
  })
}

function switchSort(sortKey) {
  router.replace({
    path: '/search',
    query: {
      keyword: keyword.value,
      type: activeTab.value,
      sortBy: sortKey
    }
  })
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function goUser(id) {
  router.push(`/user/${id}`)
}

function resolveCover(video) {
  if (video.previewUrl) {
    return video.previewUrl
  }
  if (video.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(video.coverUrl)}`
  }
  return defaultCover
}

function resolveAvatar(avatar) {
  if (!avatar) {
    return avatarPlaceholder
  }
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) {
    return avatar
  }
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function formatDuration(seconds) {
  if (!seconds) {
    return '0:00'
  }
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

function formatNumber(num) {
  if (num < 1000) return num
  if (num < 10000) return `${(num / 1000).toFixed(1)}k`
  return `${(num / 10000).toFixed(1)}w`
}

function formatDate(dateStr) {
  if (!dateStr) {
    return ''
  }
  const date = new Date(dateStr)
  return Number.isNaN(date.getTime()) ? String(dateStr) : date.toLocaleDateString()
}
</script>

<style scoped>
.search-page {
  padding: 20px;
}

.search-header {
  margin-bottom: 20px;
}

.search-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #18191c;
}

.search-keyword {
  margin-top: 8px;
  color: #61666d;
  font-size: 14px;
}

.search-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e3e5e7;
  gap: 16px;
}

.filter-tabs {
  display: flex;
  gap: 16px;
}

.filter-tabs button,
.sort-options button {
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: #61666d;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.filter-tabs button:hover,
.sort-options button:hover {
  color: #fb7299;
}

.filter-tabs button.active,
.sort-options button.active {
  color: #fb7299;
  font-weight: 600;
  background: #fff0f4;
}

.sort-options {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #61666d;
}

.search-results {
  min-height: 400px;
}

.loading,
.no-results {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 400px;
  text-align: center;
  color: #9499a0;
}

.video-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.video-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.2s;
}

.video-item:hover {
  transform: translateY(-4px);
}

.video-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: #f4f5f7;
}

.video-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-duration {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
}

.video-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.video-title {
  font-size: 14px;
  font-weight: 500;
  color: #18191c;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.video-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #9499a0;
  flex-wrap: wrap;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-item {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 14px;
  background: #fff;
  border: 1px solid #e3e5e7;
  border-radius: 10px;
  cursor: pointer;
}

.user-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  object-fit: cover;
  background: #f4f5f7;
}

.user-info {
  min-width: 0;
}

.user-name {
  font-size: 15px;
  font-weight: 600;
  color: #18191c;
}

.user-extra {
  margin-top: 6px;
  color: #9499a0;
  font-size: 13px;
}

@media (max-width: 768px) {
  .search-filters {
    flex-direction: column;
    align-items: flex-start;
  }

  .video-list {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
</style>
