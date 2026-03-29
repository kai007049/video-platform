<template>
  <div class="search-page">
    <div class="search-header">
      <h1>搜索结果</h1>
    </div>
    <div class="search-content">
      <div class="search-filters">
        <div class="filter-tabs">
          <button 
            v-for="tab in filterTabs" 
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="sort-options">
          <span>排序：</span>
          <button 
            v-for="sort in sortOptions" 
            :key="sort.key"
            :class="{ active: activeSort === sort.key }"
            @click="activeSort = sort.key"
          >
            {{ sort.label }}
          </button>
        </div>
      </div>
      <div class="search-results">
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="noResults" class="no-results">
          <p>未找到相关内容</p>
          <p>请尝试其他关键词</p>
        </div>
        <div v-else class="video-list">
          <div v-for="video in videos" :key="video.id" class="video-item">
            <div class="video-cover">
              <img :src="video.cover" alt="视频封面" />
              <span class="video-duration">{{ formatDuration(video.duration) }}</span>
            </div>
            <div class="video-info">
              <h3 class="video-title">{{ video.title }}</h3>
              <div class="video-meta">
                <span class="video-up">{{ video.username }}</span>
                <span class="video-views">{{ formatNumber(video.viewCount) }} 播放</span>
                <span class="video-date">{{ formatDate(video.createTime) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { searchVideos } from '../api/video'

const route = useRoute()
const keyword = ref('')
const loading = ref(false)
const videos = ref([])
const noResults = ref(false)
const activeTab = ref('comprehensive')
const activeSort = ref('comprehensive')

const filterTabs = [
  { key: 'comprehensive', label: '综合' },
  { key: 'video', label: '视频' },
  { key: 'user', label: '用户' }
]

const sortOptions = [
  { key: 'comprehensive', label: '综合排序' },
  { key: 'viewCount', label: '播放量' },
  { key: 'createTime', label: '最新发布' }
]

onMounted(() => {
  const query = route.query.keyword
  if (query) {
    keyword.value = query
    performSearch()
  }
})

async function performSearch() {
  if (!keyword.value) return
  
  loading.value = true
  try {
    const result = await searchVideos({
      keyword: keyword.value,
      type: activeTab.value,
      sortBy: activeSort.value,
      page: 1,
      size: 20
    })
    
    videos.value = result.data || []
    noResults.value = videos.value.length === 0
  } catch (error) {
    console.error('搜索失败:', error)
    noResults.value = true
  } finally {
    loading.value = false
  }
}

function formatDuration(seconds) {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

function formatNumber(num) {
  if (num < 1000) return num
  if (num < 10000) return (num / 1000).toFixed(1) + 'k'
  return (num / 10000).toFixed(1) + 'w'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString()
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

.search-filters {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e3e5e7;
}

.filter-tabs {
  display: flex;
  gap: 16px;
}

.filter-tabs button {
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: #61666d;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.filter-tabs button:hover {
  color: #fb7299;
}

.filter-tabs button.active {
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

.sort-options button {
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: #61666d;
  font-size: 14px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.sort-options button:hover {
  color: #fb7299;
}

.sort-options button.active {
  color: #fb7299;
  font-weight: 600;
}

.search-results {
  min-height: 400px;
}

.loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 400px;
  font-size: 16px;
  color: #9499a0;
}

.no-results {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 400px;
  text-align: center;
  color: #9499a0;
}

.no-results p:first-child {
  font-size: 18px;
  margin-bottom: 8px;
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
  aspect-ratio: 16/9;
  border-radius: 8px;
  overflow: hidden;
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
}

@media (max-width: 768px) {
  .search-filters {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .video-list {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
</style>