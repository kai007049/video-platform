<template>
  <div class="up-profile" v-if="profile">
    <div class="profile-header">
      <div class="avatar-wrapper">
        <img :src="resolveAvatar(profile.avatar)" class="avatar" alt="" @error="onAvatarError" />
        <label
          v-if="canEditAvatar"
          class="avatar-upload"
          title="点击更换头像"
        >
          <input type="file" accept="image/*" @change="onAvatarChange" />
          更换头像
        </label>
      </div>
      <div class="info">
        <h1 class="username">{{ profile.username }}</h1>
        <div class="bio" v-if="profile.bio">{{ profile.bio }}</div>
        <div class="stats">
          <div class="stat-item">
            <span class="icon">🎬</span>
            <span class="value">{{ profile.videoCount }}</span>
            <span class="label">作品</span>
          </div>
          <div class="stat-item">
            <span class="icon">👥</span>
            <span class="value">{{ formatCount(profile.followingCount) }}</span>
            <span class="label">关注</span>
          </div>
          <div class="stat-item">
            <span class="icon">🌟</span>
            <span class="value">{{ formatCount(profile.fanCount) }}</span>
            <span class="label">粉丝</span>
          </div>
          <div class="stat-item">
            <span class="icon">▶️</span>
            <span class="value">{{ formatCount(profile.viewCount || 0) }}</span>
            <span class="label">总播放</span>
          </div>
        </div>
        <div
          v-if="userStore.isLoggedIn && profile.id !== userStore.userInfo?.id"
          class="action-group"
        >
          <button
            class="btn-follow"
            :class="{ followed: profile.followed }"
            @click="toggleFollow"
          >
            {{ profile.followed ? '已关注' : '+ 关注' }}
          </button>
          <button class="btn-message" @click="goMessage">发消息</button>
        </div>
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
            <img :src="resolveCover(item)" class="cover" alt="" @error="onCoverError" />
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
import { getUpProfile, updateAvatar } from '../api/user'
import { getVideoByAuthor } from '../api/video'
import { follow, unfollow } from '../api/follow'
import { useUserStore } from '../stores/user'
import { applyImageFallbackOnce } from '../utils/imageFallback'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const profile = ref(null)
const videoList = ref([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=user'
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const canEditAvatar = computed(() => userStore.isLoggedIn && profile.value?.id === userStore.userInfo?.id)
const maxAvatarSize = 2 * 1024 * 1024

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

async function onAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    alert('请选择图片文件')
    return
  }
  if (file.size > maxAvatarSize) {
    alert('头像不能超过 2MB')
    return
  }
  try {
    const formData = new FormData()
    formData.append('avatar', file)
    const objectName = await updateAvatar(formData)
    if (!profile.value) return
    profile.value.avatar = objectName
    if (userStore.userInfo) {
      userStore.userInfo.avatar = objectName
      sessionStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    }
    alert('头像更新成功')
  } catch (e) {
    console.error(e)
    alert('头像上传失败')
  }
}

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  if (avatar.startsWith('/api/file/avatar') || avatar.startsWith('/file/avatar')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function onAvatarError(event) {
  applyImageFallbackOnce(event, avatarPlaceholder)
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function goMessage() {
  if (!profile.value?.id) return
  router.push({
    path: '/message',
    query: {
      targetId: profile.value.id,
      targetName: profile.value.username || '用户'
    }
  })
}

function loadMore() {
  fetchVideos(true)
}

function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  }
  return placeholderCover
}

function onCoverError(event) {
  applyImageFallbackOnce(event, placeholderCover)
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
  padding: 24px 0;
}

.profile-header {
  display: flex;
  gap: 24px;
  padding: 32px;
  background: #fff;
  border-radius: 2.5rem;
  margin-bottom: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  position: relative;
  overflow: hidden;
}

.profile-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--bili-pink), var(--bili-blue));
}

.avatar-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  min-width: 120px;
}

.avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.avatar-upload {
  font-size: 12px;
  color: var(--bili-pink);
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 12px;
  background: rgba(251, 114, 153, 0.1);
  transition: all 0.3s;
}

.avatar-upload:hover {
  background: rgba(251, 114, 153, 0.2);
}

.avatar-upload input {
  display: none;
}

.info {
  flex: 1;
}

.username {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.bio {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 20px;
  line-height: 1.5;
}

.stats {
  display: flex;
  gap: 32px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-item .icon {
  font-size: 16px;
  margin-bottom: 4px;
}

.stat-item .value {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-item .label {
  font-size: 12px;
  color: var(--text-secondary);
}

.action-group {
  display: flex;
  gap: 16px;
  align-items: center;
}

.btn-follow {
  padding: 10px 28px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: var(--bili-pink);
  border: none;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(251, 114, 153, 0.3);
}

.btn-follow:hover {
  background: var(--bili-pink-hover);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(251, 114, 153, 0.4);
}

.btn-follow.followed {
  background: var(--bg-gray);
  color: var(--text-secondary);
  box-shadow: none;
}

.btn-follow.followed:hover {
  background: var(--border-color);
  transform: none;
}

.btn-message {
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--bili-pink);
  background: #fff;
  border: 1px solid var(--bili-pink);
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-message:hover {
  background: rgba(251, 114, 153, 0.1);
  transform: translateY(-2px);
}

.video-section h3 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.video-card {
  cursor: pointer;
  background: #fff;
  border-radius: 2.5rem;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16/9;
  border-radius: 2.5rem 2.5rem 0 0;
  overflow: hidden;
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
  background: rgba(0, 0, 0, 0.7);
  padding: 4px 8px;
  border-radius: 12px;
  backdrop-filter: blur(4px);
}

.play-count { 
  left: 12px; 
  bottom: 12px; 
  display: flex;
  align-items: center;
  gap: 4px;
}

.duration { 
  right: 12px; 
  bottom: 12px; 
}

.title {
  padding: 16px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: var(--text-primary);
}

.loading, .load-more {
  text-align: center;
  padding: 32px;
  color: var(--text-secondary);
}

.load-more button {
  padding: 10px 32px;
  font-size: 14px;
  font-weight: 600;
  color: var(--bili-pink);
  background: transparent;
  border: 1px solid var(--bili-pink);
  border-radius: 20px;
  transition: all 0.3s;
}

.load-more button:hover {
  background: rgba(251, 114, 153, 0.1);
  transform: translateY(-2px);
}
</style>
