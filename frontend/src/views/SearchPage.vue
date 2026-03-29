<template>
  <div class="search-page">
    <div class="search-head">
      <h2>“{{ keyword }}” 的搜索结果</h2>
    </div>

    <div class="type-tabs">
      <button v-for="t in typeOptions" :key="t.value" class="tab" :class="{ active: searchType === t.value }" @click="changeType(t.value)">
        {{ t.label }}
      </button>
    </div>

    <div v-if="searchType !== 'user'" class="sort-tabs">
      <button v-for="s in sortOptions" :key="s.value" class="sort-item" :class="{ active: sortBy === s.value }" @click="changeSort(s.value)">
        {{ s.label }}
      </button>
    </div>

    <div v-if="searchType !== 'user'" class="video-grid">
      <div v-for="item in videoList" :key="item.id" class="video-card" @click="goVideo(item.id)">
        <img class="cover" :src="resolveCover(item)" @error="onCoverError" />
        <div class="title">{{ item.title }}</div>
        <div class="meta">播放 {{ formatCount(item.playCount) }} · 点赞 {{ formatCount(item.likeCount) }} · 收藏 {{ formatCount(item.saveCount) }}</div>
      </div>
    </div>

    <div v-if="(searchType === 'user' || searchType === 'comprehensive') && userList.length" class="user-grid">
      <div v-for="u in userList" :key="u.id" class="user-card" @click="goProfile(u.id)">
        <img :src="resolveAvatar(u.avatar)" class="user-avatar" />
        <div class="user-name">{{ u.username }}</div>
      </div>
    </div>

    <div v-if="!loading && !videoList.length && searchType !== 'user'" class="empty">暂无视频结果</div>
    <div v-if="!loading && !userList.length && searchType === 'user'" class="empty">暂无用户结果</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchVideos, searchUsers } from '../api/video'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const searchType = ref('comprehensive')
const sortBy = ref('comprehensive')
const loading = ref(false)
const videoList = ref([])
const userList = ref([])
const pageSize = 20
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href

const typeOptions = [
  { label: '综合', value: 'comprehensive' },
  { label: '视频', value: 'video' },
  { label: '用户', value: 'user' }
]

const sortOptions = [
  { label: '综合排序', value: 'comprehensive' },
  { label: '最多点赞', value: 'like' },
  { label: '最多收藏', value: 'save' },
  { label: '最新发布', value: 'latest' },
  { label: '最多播放', value: 'play' }
]

function syncFromQuery() {
  keyword.value = String(route.query.keyword || '').trim()
  searchType.value = String(route.query.type || 'comprehensive')
  sortBy.value = String(route.query.sortBy || 'comprehensive')
}

function updateQuery(next = {}) {
  router.replace({
    path: '/search',
    query: {
      keyword: keyword.value,
      type: searchType.value,
      sortBy: sortBy.value,
      ...next
    }
  })
}

function changeType(v) {
  searchType.value = v
  updateQuery({ type: v })
}

function changeSort(v) {
  sortBy.value = v
  updateQuery({ sortBy: v })
}

async function loadData() {
  if (!keyword.value) return
  loading.value = true
  try {
    if (searchType.value === 'user') {
      userList.value = await searchUsers(keyword.value, 1, pageSize)
      videoList.value = []
      return
    }

    const res = await searchVideos(keyword.value, 1, pageSize, sortBy.value)
    videoList.value = res.records || []

    if (searchType.value === 'comprehensive') {
      userList.value = await searchUsers(keyword.value, 1, 8)
    } else {
      userList.value = []
    }
  } finally {
    loading.value = false
  }
}

function goVideo(id) {
  const href = router.resolve({ path: `/video/${id}` }).href
  window.open(href, '_blank')
}

function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  return placeholderCover
}

function onCoverError(event) {
  event.target.src = placeholderCover
}

function resolveAvatar(avatar) {
  if (!avatar) return new URL('../assets/avatar-placeholder.png', import.meta.url).href
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function goProfile(id) {
  router.push(`/user/${id}`)
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

watch(() => route.query, async () => {
  syncFromQuery()
  await loadData()
}, { immediate: true })
</script>

<style scoped>
.search-page { padding-bottom: 30px; }
.search-head h2 { margin: 0 0 14px; font-size: 22px; }
.type-tabs, .sort-tabs { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tab, .sort-item { border: 1px solid #e3e5e7; background: #fff; border-radius: 18px; padding: 6px 12px; font-size: 13px; }
.tab.active, .sort-item.active { color: #fb7299; border-color: #fb7299; background: #fff0f4; }
.video-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.video-card { background: #fff; border-radius: 8px; overflow: hidden; cursor: pointer; }
.cover { width: 100%; aspect-ratio: 16/9; object-fit: cover; display: block; }
.title { font-size: 14px; padding: 8px 10px 4px; }
.meta { font-size: 12px; color: #9499a0; padding: 0 10px 10px; }
.user-grid { display: grid; grid-template-columns: repeat(6, minmax(0,1fr)); gap: 12px; margin-top: 16px; }
.user-card { background: #fff; border-radius: 8px; padding: 12px; text-align: center; cursor: pointer; }
.user-avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; }
.user-name { margin-top: 8px; font-size: 13px; }
.empty { color: #9499a0; text-align: center; padding: 40px 0; }
</style>
