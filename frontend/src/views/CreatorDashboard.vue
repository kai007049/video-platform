<template>
  <div class="creator-dashboard">
    <section class="profile-card" v-if="userInfo">
      <img :src="resolveAvatar(userInfo.avatar)" class="avatar" alt="avatar" @error="onAvatarError" />
      <div class="profile-main">
        <h1>{{ userInfo.username }}</h1>
        <p class="profile-sign">{{ userInfo.sign || '这个人很神秘，什么都没有留下。' }}</p>
        <div class="profile-stats">
          <div class="stat-item">
            <span class="stat-value">{{ formatCount(stats.videoCount) }}</span>
            <span class="stat-label">作品</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatCount(followingCount) }}</span>
            <span class="stat-label">关注</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatCount(stats.fanCount) }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatCount(stats.totalPlayCount) }}</span>
            <span class="stat-label">总播放</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ formatCount(stats.totalLikeCount) }}</span>
            <span class="stat-label">总点赞</span>
          </div>
        </div>
      </div>
    </section>

    <section class="tabs-section">
      <button
        v-for="item in tabs"
        :key="item.key"
        :class="['tab-item', { active: activeTab === item.key }]"
        @click="switchTab(item.key)"
      >
        {{ item.label }}
      </button>
    </section>

    <section class="content-section">
      <div v-if="loading" class="state-panel">加载中...</div>
      <div v-else-if="errorMessage" class="state-panel error">{{ errorMessage }}</div>
      <div v-else-if="currentList.length === 0" class="state-panel">当前还没有内容</div>

      <div v-else-if="isVideoTab" class="video-grid">
        <article v-for="video in currentList" :key="video.id" class="video-card">
          <div class="cover-wrap" @click="goVideo(video.id)">
            <img :src="resolveCover(video)" :alt="video.title" class="cover" @error="onCoverError" />
            <span class="duration">{{ formatDuration(video.durationSeconds) }}</span>
          </div>
          <div class="card-body">
            <h3 class="video-title" @click="goVideo(video.id)">{{ video.title }}</h3>
            <div class="video-meta">
              <span>播放 {{ formatCount(video.playCount) }}</span>
              <span>评论 {{ formatCount(video.commentCount) }}</span>
              <span>点赞 {{ formatCount(video.likeCount) }}</span>
            </div>
            <button
              v-if="activeTab === 'works'"
              class="delete-btn"
              @click="confirmDelete(video.id)"
            >
              删除视频
            </button>
          </div>
        </article>
      </div>

      <div v-else class="user-list">
        <article v-for="user in currentList" :key="user.id" class="user-card">
          <img :src="resolveAvatar(user.avatar)" class="user-avatar" alt="avatar" @error="onAvatarError" />
          <div class="user-card-main">
            <div class="user-name">{{ user.username }}</div>
            <div class="user-signature">{{ user.sign || '这个用户还没有个性签名' }}</div>
          </div>
          <div class="user-actions">
            <button class="action-btn primary" @click="goMessage(user)">私信</button>
            <button class="action-btn" @click="router.push(`/user/${user.id}`)">主页</button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getCreatorStats } from '../api/user'
import { getFanList, getFollowingList } from '../api/follow'
import {
  deleteVideo,
  getCreatorVideos,
  getFavoriteVideos,
  getHistoryVideos,
  getLikedVideos
} from '../api/video'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('works')
const loading = ref(true)
const errorMessage = ref('')
const followingCount = ref(0)
const listMap = ref({
  works: [],
  liked: [],
  favorite: [],
  history: [],
  following: [],
  fans: []
})
const stats = ref({
  totalPlayCount: 0,
  totalLikeCount: 0,
  videoCount: 0,
  fanCount: 0
})

const tabs = [
  { key: 'works', label: '我的作品' },
  { key: 'liked', label: '点赞记录' },
  { key: 'favorite', label: '收藏记录' },
  { key: 'history', label: '观看历史' },
  { key: 'following', label: '关注列表' },
  { key: 'fans', label: '粉丝列表' }
]

const defaultCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href

const userInfo = computed(() => userStore.userInfo)
const currentList = computed(() => listMap.value[activeTab.value] || [])
const isVideoTab = computed(() => ['works', 'liked', 'favorite', 'history'].includes(activeTab.value))

/**
 * 加载创作者统计信息和当前页签列表。
 */
async function loadDashboard() {
  loading.value = true
  errorMessage.value = ''

  try {
    if (userStore.isLoggedIn && !userStore.userInfo) {
      await userStore.fetchUserInfo()
    }
    await Promise.all([loadStats(), loadFollowingCount(), loadList(activeTab.value)])
  } catch (error) {
    console.error(error)
    errorMessage.value = '加载创作者中心失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/**
 * 获取创作者统计数据。
 */
async function loadStats() {
  const data = await getCreatorStats()
  stats.value = {
    totalPlayCount: data?.totalPlayCount || 0,
    totalLikeCount: data?.totalLikeCount || 0,
    videoCount: data?.videoCount || 0,
    fanCount: data?.fanCount || 0
  }
}

/**
 * 获取关注数，用于个人中心顶部统计。
 */
async function loadFollowingCount() {
  const uid = userStore.userInfo?.id
  if (!uid) {
    followingCount.value = 0
    return
  }
  const list = await getFollowingList(uid)
  followingCount.value = Array.isArray(list) ? list.length : 0
}

/**
 * 根据页签加载对应列表数据。
 */
async function loadList(key) {
  if (key === 'works') {
    listMap.value.works = normalizePageRecords(await getCreatorVideos())
    return
  }
  if (key === 'liked') {
    listMap.value.liked = normalizePageRecords(await getLikedVideos())
    return
  }
  if (key === 'favorite') {
    listMap.value.favorite = normalizePageRecords(await getFavoriteVideos())
    return
  }
  if (key === 'history') {
    listMap.value.history = normalizePageRecords(await getHistoryVideos())
    return
  }

  const uid = userStore.userInfo?.id
  if (!uid) {
    listMap.value[key] = []
    return
  }

  if (key === 'following') {
    const list = await getFollowingList(uid)
    listMap.value.following = Array.isArray(list) ? list : []
    return
  }

  if (key === 'fans') {
    const list = await getFanList(uid)
    listMap.value.fans = Array.isArray(list) ? list : []
  }
}

/**
 * 切换页签后只刷新当前列表。
 */
async function switchTab(key) {
  activeTab.value = key
  loading.value = true
  errorMessage.value = ''
  try {
    await loadList(key)
  } catch (error) {
    console.error(error)
    errorMessage.value = '加载列表失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/**
 * 删除自己的视频并刷新作品列表。
 */
async function confirmDelete(videoId) {
  if (!window.confirm('确认删除这个视频吗？')) {
    return
  }
  await deleteVideo(videoId)
  if (activeTab.value === 'works') {
    await switchTab('works')
    await loadStats()
  }
}

function normalizePageRecords(pageData) {
  if (!pageData) {
    return []
  }
  return Array.isArray(pageData.records) ? pageData.records : []
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function goMessage(user) {
  router.push({
    path: '/message',
    query: {
      targetId: user.id,
      targetName: user.username
    }
  })
}

function formatCount(value) {
  if (!value) {
    return '0'
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}万`
  }
  return String(value)
}

function formatDuration(seconds) {
  if (seconds === null || seconds === undefined) {
    return '00:00'
  }
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainSeconds).padStart(2, '0')}`
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

function onAvatarError(event) {
  event.target.src = avatarPlaceholder
}

function onCoverError(event) {
  event.target.src = defaultCover
}

watch(
  () => userStore.userInfo?.id,
  (id) => {
    if (id) {
      loadDashboard()
    }
  }
)

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.creator-dashboard {
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px 0 40px;
}

.profile-card {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 28px;
  border-radius: 20px;
  background: linear-gradient(135deg, #fff8f4, #ffffff);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}

.avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid #fff;
  background: #f3f4f6;
}

.profile-main {
  flex: 1;
}

.profile-main h1 {
  margin: 0;
  font-size: 30px;
  color: #111827;
}

.profile-sign {
  margin: 10px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 14px;
  margin-top: 22px;
}

.stat-item {
  padding: 14px 12px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #f1f5f9;
}

.stat-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.stat-label {
  margin-top: 6px;
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.tabs-section {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 24px 0 18px;
}

.tab-item {
  padding: 10px 18px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  color: #4b5563;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-item.active,
.tab-item:hover {
  border-color: #fb7185;
  color: #e11d48;
  background: #fff1f2;
}

.content-section {
  min-height: 320px;
}

.state-panel {
  padding: 80px 20px;
  text-align: center;
  border-radius: 18px;
  background: #fff;
  color: #6b7280;
}

.state-panel.error {
  color: #dc2626;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 18px;
}

.video-card,
.user-card {
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #f3f4f6;
  cursor: pointer;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.duration {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.78);
  color: #fff;
  font-size: 12px;
}

.card-body {
  padding: 16px;
}

.video-title {
  margin: 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.5;
  cursor: pointer;
}

.video-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
}

.delete-btn {
  margin-top: 14px;
  width: 100%;
  padding: 10px 0;
  border: none;
  border-radius: 12px;
  background: #fff1f2;
  color: #e11d48;
  cursor: pointer;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
}

.user-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  background: #f3f4f6;
}

.user-card-main {
  flex: 1;
  min-width: 0;
}

.user-name {
  color: #111827;
  font-weight: 600;
}

.user-signature {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  padding: 10px 16px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  cursor: pointer;
}

.action-btn.primary {
  border-color: #fb7185;
  background: #fb7185;
  color: #fff;
}

@media (max-width: 768px) {
  .creator-dashboard {
    padding-top: 12px;
  }

  .profile-card {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
  }

  .user-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .user-actions {
    width: 100%;
  }

  .action-btn {
    flex: 1;
  }
}
</style>
