<template>
  <div class="creator-dashboard">
    <section class="profile-card" v-if="userInfo">
      <div class="avatar-wrapper">
        <img :src="resolveAvatar(userInfo.avatar)" class="avatar" alt="avatar" @error="onAvatarError" />
      </div>
      <div class="profile-main">
        <h1 class="username">{{ userInfo.username }}</h1>
        <p class="profile-sign">{{ userInfo.sign || '这个人很神秘，什么都没有留下。' }}</p>
        <div class="profile-stats">
          <div class="stat-item">
            <span class="stat-icon">🎬</span>
            <span class="stat-value">{{ formatCount(stats.videoCount) }}</span>
            <span class="stat-label">作品</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">👥</span>
            <span class="stat-value">{{ formatCount(followingCount) }}</span>
            <span class="stat-label">关注</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">🌟</span>
            <span class="stat-value">{{ formatCount(stats.fanCount) }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">▶️</span>
            <span class="stat-value">{{ formatCount(stats.totalPlayCount) }}</span>
            <span class="stat-label">总播放</span>
          </div>
          <div class="stat-item">
            <span class="stat-icon">❤️</span>
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
      <EmptyState
        v-if="errorMessage"
        icon="⚠️"
        title="加载失败"
        :description="errorMessage"
        :actions="[
          { label: '重试', type: 'primary', handler: loadDashboard },
          { label: '返回首页', type: 'secondary', handler: () => router.push('/') }
        ]"
      />
      <EmptyState
        v-else-if="currentList.length === 0 && !loading"
        icon="📭"
        title="暂无内容"
        description="当前还没有内容，快来创作吧！"
        :actions="[
          { label: '发布视频', type: 'primary', handler: () => router.push('/upload') }
        ]"
      />
      <div v-else-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

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
import EmptyState from '../components/EmptyState.vue'

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
  padding: 32px;
  border-radius: 2.5rem;
  background: #fff;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  position: relative;
  overflow: hidden;
  margin-bottom: 24px;
}

.avatar-wrapper {
  position: relative;
}

.avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid #fff;
  background: #f3f4f6;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}

.avatar:hover {
  transform: scale(1.05);
}

.profile-main {
  flex: 1;
}

.username {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.profile-sign {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.4;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(112px, 1fr));
  gap: 16px;
  margin-top: 24px;
}

.stat-item {
  padding: 16px 12px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  text-align: center;
  transition: all 0.3s;
}

.stat-item:hover {
  background: #f1f5f9;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.stat-icon {
  display: block;
  font-size: 18px;
  margin-bottom: 8px;
}

.stat-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-label {
  margin-top: 6px;
  display: block;
  color: #6b7280;
  font-size: 13px;
  font-weight: 500;
}

.tabs-section {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 24px 0 20px;
  padding: 0 4px;
}

.tab-item {
  padding: 10px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.tab-item.active,
.tab-item:hover {
  border-color: #4338ca;
  color: white;
  background: #4338ca;
  box-shadow: 0 4px 8px rgba(67, 56, 202, 0.3);
  transform: translateY(-1px);
}

.content-section {
  min-height: 400px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 60px 20px;
  text-align: center;
  color: #6b7280;
  font-size: 14px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top: 3px solid #fb7185;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.video-card,
.user-card {
  background: #fff;
  border-radius: 2.5rem;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.video-card:hover,
.user-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-4px);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #f3f4f6;
  cursor: pointer;
  overflow: hidden;
  border-top-left-radius: 2.5rem;
  border-top-right-radius: 2.5rem;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.cover-wrap:hover .cover {
  transform: scale(1.05);
}

.duration {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.85);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
}

.card-body {
  padding: 18px;
}

.video-title {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  color: #6b7280;
  font-size: 13px;
}

.delete-btn {
  margin-top: 16px;
  width: 100%;
  padding: 10px 0;
  border: none;
  border-radius: 12px;
  background: #fff1f2;
  color: #e11d48;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.delete-btn:hover {
  background: #fee2e2;
  box-shadow: 0 2px 8px rgba(225, 29, 72, 0.15);
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  border-radius: 2.5rem;
}

.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  background: #f3f4f6;
  border: 2px solid #e2e8f0;
  transition: transform 0.3s;
}

.user-avatar:hover {
  transform: scale(1.05);
}

.user-card-main {
  flex: 1;
  min-width: 0;
}

.user-name {
  color: #111827;
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
}

.user-signature {
  color: #6b7280;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.user-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  padding: 10px 18px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #374151;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.action-btn:hover {
  background: #f9fafb;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.action-btn.primary {
  border-color: #fb7185;
  background: #fb7185;
  color: #fff;
}

.action-btn.primary:hover {
  background: #f43f5e;
  box-shadow: 0 4px 12px rgba(251, 113, 133, 0.3);
}

@media (max-width: 768px) {
  .creator-dashboard {
    padding: 16px 16px 40px;
  }

  .profile-card {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px;
    border-radius: 1.5rem;
  }

  .avatar-wrapper {
    align-self: center;
  }

  .profile-main {
    width: 100%;
    text-align: center;
  }

  .username {
    font-size: 24px;
  }

  .profile-stats {
    grid-template-columns: repeat(5, 1fr);
    gap: 12px;
  }

  .stat-item {
    padding: 12px 8px;
  }

  .stat-value {
    font-size: 18px;
  }

  .video-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 16px;
  }

  .user-card {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px;
    border-radius: 1.5rem;
  }

  .user-actions {
    width: 100%;
    margin-top: 12px;
  }

  .action-btn {
    flex: 1;
  }
}
</style>
