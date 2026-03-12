<template>
  <div class="up-profile" v-if="profile">
    <div class="profile-header">
      <img :src="profile.avatar || defaultAvatar" class="avatar" alt="" />
      <div class="info">
        <h1 class="username">{{ profile.username }}</h1>
        <div class="stats">
          <span>投稿 {{ profile.videoCount }}</span>
          <span>粉丝 {{ formatCount(profile.fanCount) }}</span>
          <span>关注 {{ formatCount(profile.followingCount) }}</span>
        </div>
        <button
          v-if="userStore.isLoggedIn && profile.id !== userStore.userInfo?.id"
          class="btn-follow"
          :class="{ followed: profile.followed }"
          @click="toggleFollow"
        >
          {{ profile.followed ? '已关注' : '+ 关注' }}
        </button>
      </div>
    </div>
    <div class="video-section">
      <h3>TA的视频</h3>
      <div class="video-grid">
        <div
          v-for="item in videoList"
          :key="item.id"
          class="video-card"
          @click="goVideo(item.id)"
        >
          <div class="cover-wrap">
            <img :src="item.previewUrl || item.coverUrl || placeholderCover" class="cover" alt="" />
            <span class="play-count"><span class="icon">▶</span> {{ formatCount(item.playCount) }}</span>
            <span class="duration">{{ formatDuration(item.durationSeconds) }}</span>
          </div>
          <h3 class="title">{{ item.title }}</h3>
        </div>
      </div>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="!loading && hasMore && videoList.length" class="load-more">
        <button @click="loadMore">加载更多</button>
      </div>
    </div>
  </div>
  <div v-else class="loading">加载中...</div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUpProfile } from '../api/user'
import { getVideoByAuthor } from '../api/video'
import { follow, unfollow } from '../api/follow'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const profile = ref(null)
const videoList = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=user'
const placeholderCover = 'https://placehold.co/320x180/f4f4f4/999?text=封面'

const upId = computed(() => Number(route.params.id))

async function loadProfile() {
  try {
    profile.value = await getUpProfile(upId.value)
  } catch (e) {
    console.error(e)
  }
}

async function fetchVideos(isMore = false) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getVideoByAuthor(upId.value, isMore ? page.value : 1, 12)
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

async function toggleFollow() {
  if (!userStore.isLoggedIn) return
  try {
    if (profile.value.followed) {
      await unfollow(upId.value)
      profile.value.followed = false
      profile.value.fanCount = Math.max(0, profile.value.fanCount - 1)
    } else {
      await follow(upId.value)
      profile.value.followed = true
      profile.value.fanCount++
    }
  } catch (e) {
    console.error(e)
  }
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function loadMore() {
  fetchVideos(true)
}

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

onMounted(() => {
  loadProfile()
  fetchVideos()
})
</script>

<style scoped>
.up-profile {
  max-width: 960px;
  margin: 0 auto;
}

.profile-header {
  display: flex;
  gap: 24px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 24px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
}

.info {
  flex: 1;
}

.username {
  font-size: 24px;
  margin-bottom: 12px;
}

.stats {
  display: flex;
  gap: 24px;
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 16px;
}

.btn-follow {
  padding: 8px 24px;
  font-size: 14px;
  color: #fff;
  background: var(--bili-pink);
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.btn-follow.followed {
  background: var(--bg-gray);
  color: var(--text-secondary);
}

.video-section h3 {
  font-size: 18px;
  margin-bottom: 16px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.video-card {
  cursor: pointer;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16/9;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-count, .duration {
  position: absolute;
  font-size: 12px;
  color: #fff;
  background: rgba(0,0,0,.6);
  padding: 2px 6px;
  border-radius: 4px;
}

.play-count { left: 8px; bottom: 8px; }
.duration { right: 8px; bottom: 8px; }

.title {
  padding: 12px;
  font-size: 14px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.loading, .load-more {
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
</style>
